package com.bangdream.pet.llm

import android.content.Context
import androidx.compose.runtime.Immutable
import com.bangdream.pet.SETTINGS_PREFS

enum class ThinkingMode(val value: String) {
    Auto("auto"),
    Enabled("enabled"),
    Disabled("disabled"),
    ;

    companion object {
        fun fromValue(value: String?): ThinkingMode = entries.firstOrNull { it.value == value } ?: Auto
    }
}

@Immutable
data class LlmSettings(
    val baseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-v4-flash",
    val customPrompt: String = "",
    val thinkingMode: ThinkingMode = ThinkingMode.Auto,
    val temperature: Float = 0.8f,
    val maxTokens: Int = 1024,
    val contextTokens: Int = DEFAULT_CONTEXT_TOKENS,
    val imageInputEnabled: Boolean = false,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun normalized(): LlmSettings = copy(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        customPrompt = customPrompt.trim(),
        temperature = temperature.coerceIn(0f, 2f),
        maxTokens = maxTokens.coerceIn(1, 32_768),
        contextTokens = contextTokens.coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS),
    )

    fun endpoint(): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/chat/completions", ignoreCase = true)) base else "$base/chat/completions"
    }

    fun save(context: Context) {
        val value = normalized()
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LLM_BASE_URL, value.baseUrl)
            .putString(KEY_LLM_API_KEY, value.apiKey)
            .putString(KEY_LLM_MODEL, value.model)
            .putString(KEY_LLM_THINKING_MODE, value.thinkingMode.value)
            .putFloat(KEY_LLM_TEMPERATURE, value.temperature)
            .putInt(KEY_LLM_MAX_TOKENS, value.maxTokens)
            .putInt(KEY_LLM_CONTEXT_TOKENS, value.contextTokens)
            .putBoolean(KEY_LLM_IMAGE_INPUT_ENABLED, value.imageInputEnabled)
            .apply()
    }

    companion object {
        const val DEFAULT_CONTEXT_TOKENS = 1_048_576 // 1M
        const val MIN_CONTEXT_TOKENS = 8_192
        const val MAX_CONTEXT_TOKENS = 4_000_000

        fun load(context: Context): LlmSettings {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            return LlmSettings(
                baseUrl = prefs.getString(KEY_LLM_BASE_URL, null).orEmpty(),
                apiKey = prefs.getString(KEY_LLM_API_KEY, null).orEmpty(),
                model = prefs.getString(KEY_LLM_MODEL, null).orEmpty(),
                thinkingMode = ThinkingMode.fromValue(prefs.getString(KEY_LLM_THINKING_MODE, null)),
                temperature = prefs.getFloat(KEY_LLM_TEMPERATURE, 0.8f).coerceIn(0f, 2f),
                maxTokens = prefs.getInt(KEY_LLM_MAX_TOKENS, 1024).coerceIn(1, 32_768),
                contextTokens = prefs.getInt(KEY_LLM_CONTEXT_TOKENS, DEFAULT_CONTEXT_TOKENS)
                    .coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS),
                imageInputEnabled = prefs.getBoolean(KEY_LLM_IMAGE_INPUT_ENABLED, false),
            )
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
)

@Immutable
data class ChatConversation(
    val id: String,
    val characterId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
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
private const val KEY_LLM_TEMPERATURE = "llm_temperature"
private const val KEY_LLM_MAX_TOKENS = "llm_max_tokens"
private const val KEY_LLM_CONTEXT_TOKENS = "llm_context_tokens"
private const val KEY_LLM_IMAGE_INPUT_ENABLED = "llm_image_input_enabled"
