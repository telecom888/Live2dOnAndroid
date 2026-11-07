package com.bangdream.pet.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

enum class ThinkingMode(val value: String) {
    Auto("auto"),
    Enabled("enabled"),
    Disabled("disabled"),
    ;

    companion object {
        fun fromValue(value: String?): ThinkingMode = entries.firstOrNull { it.value == value } ?: Auto
    }
}

enum class TimeContextOverride { INHERIT, ENABLED, DISABLED }

enum class LlmProtocol(val value: String, val label: String) {
    OPENAI("openai", "OpenAI"),
    ANTHROPIC("anthropic", "Anthropic"),
    GOOGLE("google", "Google"),
    ;

    companion object {
        fun fromValue(value: String?): LlmProtocol = entries.firstOrNull { it.value == value } ?: OPENAI
    }
}

@Immutable
data class LlmHeader(
    val name: String = "",
    val value: String = "",
)

@Immutable
data class LlmSettings(
    val baseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-v4-flash",
    val customPrompt: String = "",
    val thinkingMode: ThinkingMode = ThinkingMode.Auto,
    val temperature: Float = 0.8f,
    val maxTokens: Long = 1024,
    val contextTokens: Int = DEFAULT_CONTEXT_TOKENS,
    val imageInputEnabled: Boolean = false,
    val sendMessageTime: Boolean = false,
    val headers: List<LlmHeader> = emptyList(),
    val protocol: LlmProtocol = LlmProtocol.OPENAI,
    val anthropicAdaptiveThinking: Boolean = true,
    val showReasoning: Boolean = true,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun normalized(): LlmSettings = copy(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        customPrompt = customPrompt.trim(),
        temperature = temperature.coerceIn(0f, 2f),
        maxTokens = maxTokens.coerceAtLeast(1),
        contextTokens = contextTokens.coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS),
        headers = headers
            .map { LlmHeader(name = it.name.trim(), value = it.value.trim()) }
            .filter { it.name.isNotEmpty() },
    )

    fun endpoint(stream: Boolean = true): String {
        val base = baseUrl.trim().trimEnd('/')
        return when (protocol) {
            LlmProtocol.OPENAI -> if (base.endsWith("/chat/completions", ignoreCase = true)) base else "$base/chat/completions"
            LlmProtocol.ANTHROPIC -> when {
                base.endsWith("/messages") -> base
                base.endsWith("/v1") -> "$base/messages"
                else -> "$base/v1/messages"
            }
            LlmProtocol.GOOGLE -> {
                val root = base.removeSuffix("/chat/completions").removeSuffix("/v1")
                val versioned = if (root.endsWith("/v1beta")) root else "$root/v1beta"
                val id = java.net.URLEncoder.encode(model.trim().removePrefix("models/"), "UTF-8").replace("+", "%20")
                "$versioned/models/$id:" + if (stream) "streamGenerateContent?alt=sse" else "generateContent"
            }
        }
    }

    companion object {
        const val DEFAULT_CONTEXT_TOKENS = 1_048_576 // 1M
        const val MIN_CONTEXT_TOKENS = 8_192
        const val MAX_CONTEXT_TOKENS = 4_000_000

        fun load(context: Context): LlmSettings = LlmProviderStore.get(context).state.value.activeSettings()

        internal fun loadLegacy(prefs: SharedPreferences): LlmSettings = LlmSettings(
            baseUrl = prefs.getString(KEY_LLM_BASE_URL, null).orEmpty(),
            apiKey = prefs.getString(KEY_LLM_API_KEY, null).orEmpty(),
            model = prefs.getString(KEY_LLM_MODEL, null).orEmpty(),
            thinkingMode = ThinkingMode.fromValue(prefs.getString(KEY_LLM_THINKING_MODE, null)),
            showReasoning = prefs.getBoolean(KEY_LLM_SHOW_REASONING, true),
            protocol = LlmProtocol.fromValue(prefs.getString("llm_protocol", null)),
            anthropicAdaptiveThinking = prefs.getBoolean("llm_anthropic_adaptive", true),
            temperature = prefs.getFloat(KEY_LLM_TEMPERATURE, 0.8f),
            maxTokens = prefs.getInt(KEY_LLM_MAX_TOKENS, 1024).toLong(),
            contextTokens = prefs.getInt(KEY_LLM_CONTEXT_TOKENS, DEFAULT_CONTEXT_TOKENS),
            imageInputEnabled = prefs.getBoolean(KEY_LLM_IMAGE_INPUT_ENABLED, false),
            sendMessageTime = prefs.getBoolean(KEY_LLM_SEND_MESSAGE_TIME, false),
            headers = runCatching { decodeHeaders(JSONArray(prefs.getString(KEY_LLM_HEADERS, null))) }
                .getOrDefault(emptyList()),
        ).normalized()

        internal fun clearLegacy(editor: SharedPreferences.Editor) {
            listOf(
                KEY_LLM_BASE_URL, KEY_LLM_API_KEY, KEY_LLM_MODEL, KEY_LLM_THINKING_MODE,
                KEY_LLM_SHOW_REASONING, "llm_protocol", "llm_anthropic_adaptive", KEY_LLM_TEMPERATURE,
                KEY_LLM_MAX_TOKENS, KEY_LLM_CONTEXT_TOKENS, KEY_LLM_IMAGE_INPUT_ENABLED,
                KEY_LLM_SEND_MESSAGE_TIME, KEY_LLM_HEADERS,
            ).forEach(editor::remove)
        }
    }
}

@Immutable
data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val reasoning: String? = null,
    /** 随本条消息发送给模型的图片（base64 data URL）。仅用于当次请求，不写入历史。 */
    val images: List<String> = emptyList(),
    /** LINE 已读状态：只对用户发送的消息显示，对方（角色）看过后为 true。 */
    val read: Boolean = false,
    /** Whether this user message carried its local send time to the model. */
    val timeContextEnabled: Boolean = false,
    val timeZoneId: String? = null,
    val parentId: String? = null,
    /** One reply/branch node, rendered as ordered bubbles. Empty for legacy messages. */
    val segments: List<String> = emptyList(),
    /** Internal recovery state; no status label is shown in chat. */
    val incomplete: Boolean = false,
    /** Wire turns behind one visible reply, preserving continuation boundaries. */
    val requestHistory: List<ChatRequestTurn> = emptyList(),
    val continuationRoute: LlmReplyRoute? = null,
)

@Immutable
data class ChatRequestTurn(val role: String, val content: String)

@Immutable
data class ChatConversation(
    val id: String,
    val characterId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    val timeContextOverride: TimeContextOverride = TimeContextOverride.INHERIT,
    /** Complete branch tree. [messages] is only the currently selected path. */
    val nodes: List<ChatMessage> = emptyList(),
    val activeLeafId: String? = null,
    val preferredChildIds: Map<String, String> = emptyMap(),
    val titleManual: Boolean = true,
)

@Immutable
data class ChatConversationSummary(
    val id: String,
    val characterId: String,
    val title: String,
    val preview: String,
    val searchableContent: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
)

data class ChatHistorySnapshot(
    val conversations: List<ChatConversationSummary>,
    val activeConversation: ChatConversation?,
)

sealed interface LlmStreamEvent {
    data class Content(val text: String) : LlmStreamEvent
    data class Reasoning(val text: String) : LlmStreamEvent
    data object ReasoningStarted : LlmStreamEvent
}

private const val KEY_LLM_BASE_URL = "llm_base_url"
private const val KEY_LLM_API_KEY = "llm_api_key"
private const val KEY_LLM_MODEL = "llm_model"
private const val KEY_LLM_THINKING_MODE = "llm_thinking_mode"
private const val KEY_LLM_SHOW_REASONING = "llm_show_reasoning"
private const val KEY_LLM_TEMPERATURE = "llm_temperature"
private const val KEY_LLM_MAX_TOKENS = "llm_max_tokens"
private const val KEY_LLM_CONTEXT_TOKENS = "llm_context_tokens"
private const val KEY_LLM_IMAGE_INPUT_ENABLED = "llm_image_input_enabled"
private const val KEY_LLM_SEND_MESSAGE_TIME = "llm_send_message_time"
private const val KEY_LLM_HEADERS = "llm_headers"

internal fun encodeHeaders(headers: List<LlmHeader>): JSONArray {
    val array = JSONArray()
    headers.forEach { header ->
        array.put(JSONObject().put("name", header.name).put("value", header.value))
    }
    return array
}

internal fun decodeHeaders(array: JSONArray?): List<LlmHeader> = buildList {
    if (array == null) return@buildList
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        add(LlmHeader(name = obj.optString("name"), value = obj.optString("value")))
    }
}
