package com.bangdream.pet.llm

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bangdream.pet.VoiceSettings
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.loadCharacterCustomPrompt
import com.bangdream.pet.loadCharacterMemory
import com.bangdream.pet.appendCharacterMemory
import com.bangdream.pet.loadReplyVoiceEnabled
import com.bangdream.pet.voice.VoiceCloneClient
import com.bangdream.pet.voice.VoicePlayer
import com.bangdream.pet.voice.VoiceSamples
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ChatUiState(
    val characterId: String? = null,
    val conversationId: String? = null,
    val conversationTitle: String = "",
    val conversations: List<ChatConversationSummary> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val streamingText: String = "",
    val streamingReasoning: String = "",
    val isGenerating: Boolean = false,
    val isThinking: Boolean = false,
    val isHistoryLoading: Boolean = false,
    val error: String? = null,
)

class Live2DChatViewModel(application: Application) : AndroidViewModel(application) {
    private val localBackend = LocalChatBackend(application)
    private val history = localBackend.history
    private val prompts = localBackend.prompts
    private val client = localBackend.client
    private val mutableState = MutableStateFlow(ChatUiState())
    private val mutableActions = Channel<String>(Channel.BUFFERED)
    private var requestJob: Job? = null
    private var transitionJob: Job? = null
    private var lastFailedRequest: FailedRequest? = null
    private var selectedLocalModel: ModelChoice? = null

    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()
    val actions: Flow<String> = mutableActions.receiveAsFlow()

    fun selectCharacter(model: ModelChoice, force: Boolean = false) {
        selectedLocalModel = model
        val current = mutableState.value
        if (
            current.characterId == model.characterId &&
            (!force || current.isGenerating || current.isHistoryLoading)
        ) return
        mutableState.value = if (current.characterId == model.characterId) {
            current.copy(isHistoryLoading = true, error = null)
        } else {
            ChatUiState(characterId = model.characterId, isHistoryLoading = true)
        }
        launchTransition {
            stopRequestAndJoin()
            val snapshot = runIoCatching { history.loadSnapshot(model.characterId) }.getOrElse {
                mutableState.value = ChatUiState(characterId = model.characterId, error = ERROR_HISTORY_LOAD)
                return@launchTransition
            }
            applySnapshot(model.characterId, snapshot)
        }
    }

    fun startNewConversation(characterId: String) {
        if (mutableState.value.characterId != characterId) return
        mutableState.value = mutableState.value.copy(isHistoryLoading = true, error = null)
        launchTransition {
            stopRequestAndJoin()
            val conversations = runIoCatching { history.listConversations(characterId) }.getOrElse {
                mutableState.value = mutableState.value.copy(isHistoryLoading = false, error = ERROR_HISTORY_LOAD)
                return@launchTransition
            }
            lastFailedRequest = null
            mutableState.value = ChatStateTransitions.newDraft(characterId, conversations)
        }
    }

    fun selectConversation(characterId: String, conversationId: String) {
        if (mutableState.value.characterId != characterId || mutableState.value.conversationId == conversationId) return
        mutableState.value = mutableState.value.copy(isHistoryLoading = true, error = null)
        launchTransition {
            stopRequestAndJoin()
            val result = runIoCatching {
                val conversation = history.loadConversation(characterId, conversationId)
                    ?: error("Conversation not found")
                history.setActiveConversation(characterId, conversationId)
                conversation to history.listConversations(characterId)
            }.getOrElse {
                mutableState.value = mutableState.value.copy(isHistoryLoading = false, error = ERROR_HISTORY_LOAD)
                return@launchTransition
            }
            lastFailedRequest = null
            val (conversation, conversations) = result
            mutableState.value = ChatStateTransitions.fromConversation(conversation, conversations)
        }
    }

    fun deleteConversation(characterId: String, conversationId: String) {
        if (mutableState.value.characterId != characterId) return
        mutableState.value = mutableState.value.copy(isHistoryLoading = true, error = null)
        launchTransition {
            stopRequestAndJoin()
            val wasActive = mutableState.value.conversationId == conversationId
            val result = runIoCatching {
                check(history.deleteConversation(characterId, conversationId))
                val conversations = history.listConversations(characterId)
                val replacement = if (wasActive) {
                    conversations.firstOrNull()?.let { history.loadConversation(characterId, it.id) }
                } else {
                    null
                }
                if (wasActive) history.setActiveConversation(characterId, replacement?.id)
                conversations to replacement
            }.getOrElse {
                mutableState.value = mutableState.value.copy(isHistoryLoading = false, error = ERROR_HISTORY_DELETE)
                return@launchTransition
            }
            val (conversations, replacement) = result
            lastFailedRequest = null
            mutableState.value = if (wasActive) {
                ChatStateTransitions.afterActiveDelete(characterId, conversations, replacement)
            } else {
                mutableState.value.copy(conversations = conversations, isHistoryLoading = false)
            }
        }
    }

    fun renameConversation(characterId: String, conversationId: String, title: String) {
        if (mutableState.value.characterId != characterId) return
        launchTransition {
            stopRequestAndJoin()
            val conversations = runIoCatching {
                check(history.renameConversation(characterId, conversationId, title))
                history.listConversations(characterId)
            }.getOrElse {
                mutableState.value = mutableState.value.copy(error = ERROR_HISTORY_SAVE)
                return@launchTransition
            }
            mutableState.value = mutableState.value.copy(
                conversations = conversations,
                conversationTitle = if (mutableState.value.conversationId == conversationId) {
                    title.trim().takeIf(String::isNotBlank) ?: mutableState.value.conversationTitle
                } else {
                    mutableState.value.conversationTitle
                },
            )
        }
    }

    data class ConversationStats(
        val messageCount: Int = 0,
        val totalChars: Int = 0,
        val systemPromptChars: Int = 0,
    )

    /** 某对话的统计信息：消息条数、消息总字数、角色设定字数。 */
    suspend fun conversationStats(model: ModelChoice, conversationId: String): ConversationStats =
        withContext(Dispatchers.IO) {
            val system = prompts.buildSystemPrompt(model).text
            val conversation = history.loadConversation(model.characterId, conversationId)
            val messages = conversation?.messages.orEmpty()
            ConversationStats(
                messageCount = messages.size,
                totalChars = messages.sumOf { it.content.length },
                systemPromptChars = system.length,
            )
        }

    fun send(model: ModelChoice, input: String, images: List<String> = emptyList()): Boolean {
        val text = input.trim()
        val current = mutableState.value
        if (
            (text.isEmpty() && images.isEmpty()) ||
            requestJob?.isActive == true ||
            current.isHistoryLoading ||
            current.characterId != model.characterId
        ) return false
        startRequest(model, text, appendUser = true, images = images)
        return true
    }

    fun retry(model: ModelChoice) {
        val failed = lastFailedRequest ?: return
        val current = mutableState.value
        if (
            requestJob?.isActive == true ||
            current.isHistoryLoading ||
            failed.characterId != model.characterId ||
            current.characterId != failed.characterId ||
            current.conversationId != failed.conversationId
        ) return
        startRequest(model, failed.input, appendUser = false)
    }

    fun stop() {
        requestJob?.cancel()
    }

    fun clearAll() {
        mutableState.value = mutableState.value.copy(isHistoryLoading = true, error = null)
        launchTransition {
            stopRequestAndJoin()
            val cleared = runIoCatching { history.clearAll() }.isSuccess
            val characterId = mutableState.value.characterId
            lastFailedRequest = null
            mutableState.value = ChatUiState(
                characterId = characterId,
                error = if (cleared) null else ERROR_HISTORY_DELETE,
            )
        }
    }

    private fun startRequest(model: ModelChoice, input: String, appendUser: Boolean, images: List<String> = emptyList()) {
        requestJob = viewModelScope.launch {
            val current = mutableState.value
            if (current.characterId != model.characterId) return@launch

            val now = System.currentTimeMillis()
            val conversationId = current.conversationId ?: UUID.randomUUID().toString()
            val existingSummary = current.conversations.firstOrNull { it.id == conversationId }
            val createdAt = existingSummary?.createdAt ?: now
            val title = current.conversationTitle.ifBlank {
                if (appendUser) ChatHistoryRepository.titleFromFirstMessage(input) else existingSummary?.title.orEmpty()
            }
            val messages = if (appendUser) {
                (current.messages + newMessage("user", input).copy(images = images))
            } else {
                current.messages
            }
            val requestContext = RequestContext(
                characterId = model.characterId,
                conversationId = conversationId,
                title = title,
                createdAt = createdAt,
                updatedAt = now,
                messages = messages,
            )

            // LINE 已读：模拟「对方（角色）看过了」，在模型开始回复前随机时间把这条用户消息标记为已读
            if (appendUser) {
                val userMessageId = messages.lastOrNull { it.role == "user" }?.id
                viewModelScope.launch {
                    delay(Random.nextLong(800L, 4_000L))
                    markUserMessageRead(model.characterId, conversationId, userMessageId)
                }
            }

            val conversations = runIoCatching {
                history.saveConversation(requestContext.toConversation(messages))
                history.setActiveConversation(model.characterId, conversationId)
                history.listConversations(model.characterId)
            }.getOrElse {
                mutableState.value = current.copy(
                    conversationId = conversationId,
                    conversationTitle = title,
                    messages = messages,
                    error = ERROR_HISTORY_SAVE,
                )
                return@launch
            }

            mutableState.value = current.copy(
                conversationId = conversationId,
                conversationTitle = title,
                conversations = conversations,
                messages = messages,
                streamingText = "",
                isThinking = false,
                error = null,
            )

            val settings = LlmSettings.load(getApplication())
            if (!settings.isConfigured) {
                mutableState.value = mutableState.value.copy(error = ERROR_LLM_NOT_CONFIGURED)
                return@launch
            }
            val characterPrompt = withContext(Dispatchers.IO) { prompts.buildSystemPrompt(model) }
            val customPrompt = withContext(Dispatchers.IO) {
                loadCharacterCustomPrompt(getApplication(), model.characterId)
            }
            val memory = withContext(Dispatchers.IO) {
                loadCharacterMemory(getApplication(), model.characterId)
            }
            val basePrompt = customPrompt?.takeIf(String::isNotBlank) ?: characterPrompt.text
            val systemPrompt = if (memory.isBlank()) {
                basePrompt
            } else {
                "$basePrompt\n\n【长期记忆】以下是你已经记住的内容，请在交流中自然地运用，不要向用户解释或提及记忆本身：\n$memory"
            }
            val parser = ActionTagParser(characterPrompt.allowedActionTags)
            mutableState.value = mutableState.value.copy(isGenerating = true)
            lastFailedRequest = null
            var latestStreamingText = ""
            var latestStreamingReasoning = ""
            val pendingParserText = StringBuilder()
            var lastStreamingPublishAt = 0L
            var pendingStreamingUpdate: Job? = null

            fun flushParserText() {
                if (pendingParserText.isEmpty()) return
                latestStreamingText = parser.consume(pendingParserText.toString())
                pendingParserText.setLength(0)
            }

            fun publishStreamingText() {
                val now = System.nanoTime() / 1_000_000L
                val elapsed = now - lastStreamingPublishAt
                if (lastStreamingPublishAt == 0L || elapsed >= STREAMING_UI_UPDATE_INTERVAL_MS) {
                    flushParserText()
                    pendingStreamingUpdate?.cancel()
                    pendingStreamingUpdate = null
                    lastStreamingPublishAt = now
                    if (isActive(requestContext)) {
                        mutableState.value = mutableState.value.copy(
                            streamingText = latestStreamingText,
                            streamingReasoning = latestStreamingReasoning,
                            isThinking = false,
                        )
                    }
                } else if (pendingStreamingUpdate?.isActive != true) {
                    pendingStreamingUpdate = launch {
                        delay((STREAMING_UI_UPDATE_INTERVAL_MS - elapsed).coerceAtLeast(1L))
                        if (isActive(requestContext)) {
                            flushParserText()
                            lastStreamingPublishAt = System.nanoTime() / 1_000_000L
                            mutableState.value = mutableState.value.copy(
                                streamingText = latestStreamingText,
                                streamingReasoning = latestStreamingReasoning,
                                isThinking = false,
                            )
                        }
                    }
                }
            }

            try {
                client.streamCompletion(settings, systemPrompt, messages, requestContext.conversationId).collect { event ->
                    if (!isActive(requestContext)) return@collect
                    when (event) {
                        is LlmStreamEvent.Content -> {
                            pendingParserText.append(event.text)
                            publishStreamingText()
                        }
                        is LlmStreamEvent.Reasoning -> {
                            latestStreamingReasoning += event.text
                            mutableState.value = mutableState.value.copy(
                                streamingReasoning = latestStreamingReasoning,
                                isThinking = true,
                            )
                        }
                        LlmStreamEvent.ReasoningStarted -> mutableState.value = mutableState.value.copy(isThinking = true)
                    }
                }
                pendingStreamingUpdate?.cancel()
                flushParserText()
                finalizeAssistant(requestContext, parser, latestStreamingReasoning)
            } catch (cancelled: CancellationException) {
                pendingStreamingUpdate?.cancel()
                withContext(NonCancellable) {
                    flushParserText()
                    finalizeAssistant(requestContext, parser, latestStreamingReasoning)
                }
                throw cancelled
            } catch (error: Throwable) {
                pendingStreamingUpdate?.cancel()
                if (!currentCoroutineContext().isActive) {
                    withContext(NonCancellable) {
                        flushParserText()
                        finalizeAssistant(requestContext, parser, latestStreamingReasoning)
                    }
                    return@launch
                }
                lastFailedRequest = FailedRequest(
                    characterId = requestContext.characterId,
                    conversationId = requestContext.conversationId,
                    input = input,
                )
                if (isActive(requestContext)) {
                    mutableState.value = mutableState.value.copy(
                        isGenerating = false,
                        isThinking = false,
                        streamingText = "",
                        streamingReasoning = "",
                        error = error.message ?: error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    private suspend fun finalizeAssistant(request: RequestContext, parser: ActionTagParser, reasoning: String) {
        val result = parser.finish()
        val finalMessages = if (result.text.isNotBlank()) {
            (request.messages + newMessage("assistant", result.text).copy(
                reasoning = reasoning.takeIf(String::isNotBlank),
                read = true,
            ))
        } else {
            request.messages
        }
        val updatedRequest = request.copy(
            updatedAt = if (result.text.isNotBlank()) System.currentTimeMillis() else request.updatedAt,
        )
        val conversations = runIoCatching {
            history.saveConversation(updatedRequest.toConversation(finalMessages))
            history.listConversations(request.characterId)
        }.getOrElse {
            if (isActive(request)) {
                mutableState.value = mutableState.value.copy(
                    messages = finalMessages,
                    streamingText = "",
                    streamingReasoning = "",
                    isGenerating = false,
                    isThinking = false,
                    error = ERROR_HISTORY_SAVE,
                )
            }
            return
        }
        if (!isActive(request)) return
        result.action?.let { mutableActions.send(it) }
        if (lastFailedRequest?.conversationId == request.conversationId) lastFailedRequest = null
        mutableState.value = mutableState.value.copy(
            conversationId = request.conversationId,
            conversationTitle = request.title,
            conversations = conversations,
            messages = finalMessages,
            streamingText = "",
            streamingReasoning = "",
            isGenerating = false,
            isThinking = false,
            error = null,
        )
        if (result.text.isNotBlank()) {
            playReplyVoiceIfEnabled(request.characterId, result.text)
        }
    }

    /** 对话列表「载入记忆」：把整条对话按「用户/角色」格式追加为该角色的长期记忆。 */
    fun loadConversationAsMemory(characterId: String, characterName: String, conversationId: String) {
        viewModelScope.launch {
            val entry = withContext(Dispatchers.IO) {
                val conversation = history.loadConversation(characterId, conversationId) ?: return@withContext ""
                conversation.messages.joinToString("\n") { message ->
                    val who = if (message.role == "user") "用户" else characterName
                    "$who：${message.content}"
                }.trim()
            }
            if (entry.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    appendCharacterMemory(getApplication(), characterId, entry)
                }
            }
        }
    }

    /** 对话页「朗读」：用该角色当前音色合成并播放指定文本。 */
    fun replayMessage(characterId: String, text: String) {
        val textValue = text.trim()
        if (textValue.isEmpty()) return
        val app = getApplication<Application>()
        val voice = VoiceSettings.load(app)
        if (!voice.isConfigured) return
        val sample = VoiceSamples.activeSampleFile(app, characterId) ?: return
        viewModelScope.launch {
            val wav = withContext(Dispatchers.IO) {
                runCatching {
                    VoiceCloneClient(voice.baseUrl, voice.model, voice.apiKey).synthesize(textValue, sample)
                }.getOrNull()
            }
            val audio = wav?.takeIf { it.isNotEmpty() }
            if (audio != null) VoicePlayer.play(app, audio)
        }
    }

    /** 回复完成后若开启「回复后播放语音」且 TTS/音色已配置，合成并播放（等待合成完成后播放）。 */
    private fun playReplyVoiceIfEnabled(characterId: String, text: String) {
        val app = getApplication<Application>()
        if (!loadReplyVoiceEnabled(app)) return
        val voice = VoiceSettings.load(app)
        if (!voice.isConfigured) return
        val sample = VoiceSamples.activeSampleFile(app, characterId) ?: return
        viewModelScope.launch {
            val wav = withContext(Dispatchers.IO) {
                runCatching {
                    VoiceCloneClient(voice.baseUrl, voice.model, voice.apiKey).synthesize(text, sample)
                }.getOrNull()
            }
            val audio = wav?.takeIf { it.isNotEmpty() }
            if (audio != null) VoicePlayer.play(app, audio)
        }
    }

    private fun applySnapshot(characterId: String, snapshot: ChatHistorySnapshot) {
        lastFailedRequest = null
        mutableState.value = ChatStateTransitions.fromSnapshot(characterId, snapshot)
    }

    private fun launchTransition(block: suspend () -> Unit) {
        transitionJob?.cancel()
        transitionJob = viewModelScope.launch { block() }
    }

    private suspend fun <T> runIoCatching(block: () -> T): Result<T> = try {
        Result.success(withContext(Dispatchers.IO) { block() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun stopRequestAndJoin() {
        requestJob?.takeIf { it.isActive }?.cancelAndJoin()
    }

    private fun isActive(request: RequestContext): Boolean = ChatStateTransitions.matchesConversation(
        mutableState.value,
        request.characterId,
        request.conversationId,
    )

    private suspend fun markUserMessageRead(characterId: String, conversationId: String, messageId: String?) {
        if (messageId == null) return
        val current = mutableState.value
        if (current.conversationId != conversationId) return
        val updated = current.messages.map { if (it.id == messageId) it.copy(read = true) else it }
        if (updated == current.messages) return
        mutableState.value = current.copy(messages = updated)
        withContext(Dispatchers.IO) {
            val conversation = history.loadConversation(characterId, conversationId) ?: return@withContext
            history.saveConversation(
                conversation.copy(
                    messages = conversation.messages.map { if (it.id == messageId) it.copy(read = true) else it },
                ),
            )
        }
    }

    private fun newMessage(role: String, content: String): ChatMessage = ChatMessage(
        id = UUID.randomUUID().toString(),
        role = role,
        content = content,
        timestamp = System.currentTimeMillis(),
    )

    private data class FailedRequest(
        val characterId: String,
        val conversationId: String,
        val input: String,
    )

    private data class RequestContext(
        val characterId: String,
        val conversationId: String,
        val title: String,
        val createdAt: Long,
        val updatedAt: Long,
        val messages: List<ChatMessage>,
    ) {
        fun toConversation(messages: List<ChatMessage>): ChatConversation = ChatConversation(
            id = conversationId,
            characterId = characterId,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            messages = messages,
        )
    }

    companion object {
        const val ERROR_LLM_NOT_CONFIGURED = "LLM_NOT_CONFIGURED"
        const val ERROR_HISTORY_LOAD = "CHAT_HISTORY_LOAD_FAILED"
        const val ERROR_HISTORY_SAVE = "CHAT_HISTORY_SAVE_FAILED"
        const val ERROR_HISTORY_DELETE = "CHAT_HISTORY_DELETE_FAILED"
        private const val STREAMING_UI_UPDATE_INTERVAL_MS = 32L
    }
}

internal object ChatStateTransitions {
    fun fromSnapshot(characterId: String, snapshot: ChatHistorySnapshot): ChatUiState =
        snapshot.activeConversation?.let { fromConversation(it, snapshot.conversations) }
            ?: newDraft(characterId, snapshot.conversations)

    fun fromConversation(
        conversation: ChatConversation,
        conversations: List<ChatConversationSummary>,
    ): ChatUiState = ChatUiState(
        characterId = conversation.characterId,
        conversationId = conversation.id,
        conversationTitle = conversation.title,
        conversations = conversations,
        messages = conversation.messages,
    )

    fun newDraft(
        characterId: String,
        conversations: List<ChatConversationSummary>,
    ): ChatUiState = ChatUiState(
        characterId = characterId,
        conversations = conversations,
    )

    fun afterActiveDelete(
        characterId: String,
        conversations: List<ChatConversationSummary>,
        replacement: ChatConversation?,
    ): ChatUiState = replacement?.let { fromConversation(it, conversations) }
        ?: newDraft(characterId, conversations)

    fun matchesConversation(state: ChatUiState, characterId: String, conversationId: String): Boolean =
        state.characterId == characterId && state.conversationId == conversationId
}
