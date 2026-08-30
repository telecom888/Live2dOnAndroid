package com.bangdream.pet.chat

import android.content.Context
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.llm.ActionTagParser
import com.bangdream.pet.llm.ChatConversation
import com.bangdream.pet.llm.ChatHistoryRepository
import com.bangdream.pet.llm.ChatMessage
import com.bangdream.pet.llm.CharacterPromptRepository
import com.bangdream.pet.llm.LlmChatClient
import com.bangdream.pet.llm.LlmSettings
import com.bangdream.pet.llm.LlmStreamEvent
import com.bangdream.pet.VoiceSettings
import com.bangdream.pet.voice.VoicePlayer
import com.bangdream.pet.voice.VoiceCloneClient
import com.bangdream.pet.voice.VoiceSamples
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class WallpaperChatResult(
    val text: String,
    val actionTag: String?,
    val error: String? = null,
    val ttsSpoken: Boolean = false,
)

/**
 * 壁纸聊天引擎：OpenAI 兼容（默认 DeepSeek / opencode go mimo），
 * 支持 thinking 开关、每角色多会话历史、动作标签 [动作]。
 */
class WallpaperChatEngine(private val context: Context) {
    private val history = ChatHistoryRepository(context)
    private val prompts = CharacterPromptRepository(context)
    private val client = LlmChatClient()

    suspend fun send(
        model: ModelChoice,
        userText: String,
        onStreaming: (String) -> Unit,
    ): WallpaperChatResult {
        val settings = LlmSettings.load(context)
        if (!settings.isConfigured) {
            return WallpaperChatResult("", null, "请先在设置中配置模型提供商（默认 DeepSeek）。")
        }
        val characterId = model.characterId
        val prompt = prompts.buildSystemPrompt(model)
        val snapshot = history.loadSnapshot(characterId)
        val active = snapshot.activeConversation
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(UUID.randomUUID().toString(), "user", userText, now)
        val baseMessages = active?.messages.orEmpty()

        val parser = ActionTagParser(prompt.allowedActionTags)
        val textBuilder = StringBuilder()
        var error: String? = null
        try {
            client.streamCompletion(settings, prompt.text, baseMessages + userMessage).collect { event ->
                coroutineContext.ensureActive()
                when (event) {
                    is LlmStreamEvent.Content -> {
                        textBuilder.append(event.text)
                        onStreaming(parser.consume(textBuilder.toString()))
                    }
                    LlmStreamEvent.ReasoningStarted -> Unit
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            error = throwable.message ?: "请求失败"
        }

        val parsed = parser.finish()
        if (parsed.text.isBlank() && error == null) error = "模型返回为空"

        if (error == null) {
            val assistantMessage = ChatMessage(
                UUID.randomUUID().toString(),
                "assistant",
                parsed.text,
                System.currentTimeMillis(),
            )
            val conversation = active?.copy(
                updatedAt = System.currentTimeMillis(),
                messages = baseMessages + userMessage + assistantMessage,
            ) ?: ChatConversation(
                id = UUID.randomUUID().toString(),
                characterId = characterId,
                title = userText.take(24),
                createdAt = now,
                updatedAt = System.currentTimeMillis(),
                messages = listOf(userMessage, assistantMessage),
            )
            history.saveConversation(conversation)
            history.setActiveConversation(characterId, conversation.id)
        }

        var ttsSpoken = false
        if (error == null && parsed.text.isNotBlank()) {
            ttsSpoken = speak(characterId, parsed.text)
        }
        return WallpaperChatResult(parsed.text, parsed.action, error, ttsSpoken)
    }

    private suspend fun speak(characterId: String, text: String): Boolean {
        val settings = VoiceSettings.load(context)
        if (!settings.isConfigured) return false
        val sample = VoiceSamples.activeSampleFile(context, characterId) ?: return false
        val wav = withContext(Dispatchers.IO) {
            VoiceCloneClient(settings.baseUrl, settings.model, settings.apiKey).synthesize(text, sample)
        } ?: return false
        return VoicePlayer.play(context.applicationContext, wav)
    }
}
