package com.bangdream.pet.llm

import android.content.Context
import androidx.compose.runtime.Immutable
import com.bangdream.pet.SETTINGS_PREFS
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class LlmModel(val id: String, val name: String = "") {
    val label: String get() = name.ifBlank { id }
}

@Immutable
data class LlmModelRef(val providerId: String, val modelId: String)

@Immutable
data class LlmReplyRoute(val model: LlmModelRef, val endpointHash: String) {
    fun toJson(): JSONObject = model.toJson().put("endpointHash", endpointHash)

    companion object {
        fun fromJson(json: JSONObject?): LlmReplyRoute? = modelRefFromJson(json)?.let { model ->
            json?.optString("endpointHash")?.takeIf(String::isNotBlank)?.let { LlmReplyRoute(model, it) }
        }
    }
}

@Immutable
data class LlmProvider(
    val id: String,
    val name: String = "",
    val connection: LlmSettings = LlmSettings(baseUrl = "", model = ""),
    val modelsUrl: String = "",
    val models: List<LlmModel> = emptyList(),
) {
    fun normalized(): LlmProvider {
        val settings = connection.normalized().copy(model = "", showReasoning = true)
        return copy(
            name = name.trim(),
            connection = settings,
            modelsUrl = modelsUrl.trim(),
            models = models.map { it.copy(id = modelId(settings.protocol, it.id), name = it.name.trim()) }
                .filter { it.id.isNotEmpty() }.distinctBy(LlmModel::id),
        )
    }
}

internal fun modelId(protocol: LlmProtocol, id: String): String =
    id.trim().let { if (protocol == LlmProtocol.GOOGLE) it.removePrefix("models/") else it }

@Immutable
data class LlmProviderSettings(
    val providers: List<LlmProvider> = emptyList(),
    val defaultModel: LlmModelRef? = null,
    val showReasoning: Boolean = true,
) {
    fun provider(id: String): LlmProvider? = providers.firstOrNull { it.id == id }

    fun model(ref: LlmModelRef?): LlmModel? =
        ref?.let { provider(it.providerId)?.models?.firstOrNull { model -> model.id == it.modelId } }

    fun settings(ref: LlmModelRef?): LlmSettings? {
        val model = model(ref) ?: return null
        return provider(requireNotNull(ref).providerId)?.connection?.copy(model = model.id, showReasoning = showReasoning)
    }

    fun activeSettings(): LlmSettings = settings(defaultModel)
        ?: LlmSettings(baseUrl = "", model = "", showReasoning = showReasoning)

    fun route(ref: LlmModelRef?): LlmReplyRoute? {
        val settings = settings(ref)?.takeIf(LlmSettings::isConfigured) ?: return null
        val fingerprint = "${settings.protocol.value}\n${settings.endpoint()}"
        val hash = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray()).joinToString("") { "%02x".format(it) }
        return LlmReplyRoute(requireNotNull(ref), hash)
    }

    internal fun replace(provider: LlmProvider, replacementDefault: LlmModelRef?): LlmProviderSettings {
        val next = if (provider(provider.id) == null) providers + provider
        else providers.map { if (it.id == provider.id) provider else it }
        return copy(providers = next, defaultModel = replacementDefault).validDefault()
    }

    internal fun validDefault(): LlmProviderSettings =
        if (settings(defaultModel)?.isConfigured == true) this else copy(defaultModel = null)

    internal fun toJson(): JSONObject = JSONObject()
        .put("showReasoning", showReasoning)
        .put("defaultModel", defaultModel?.toJson())
        .put("providers", JSONArray(providers.map(LlmProvider::toJson)))

    companion object {
        internal fun fromLegacy(settings: LlmSettings): LlmProviderSettings {
            if (settings.baseUrl.isBlank() && settings.apiKey.isBlank() && settings.model.isBlank()) {
                return LlmProviderSettings(showReasoning = settings.showReasoning)
            }
            val name = runCatching { java.net.URL(settings.baseUrl).host }.getOrNull().orEmpty().ifBlank { "LLM" }
            val provider = LlmProvider(
                id = "legacy",
                name = name,
                connection = settings.copy(headers = settings.headers.filterNot { it.value.isBlank() }),
                models = listOf(LlmModel(settings.model)),
            ).normalized()
            return LlmProviderSettings(
                providers = listOf(provider),
                defaultModel = provider.models.firstOrNull()?.let { LlmModelRef(provider.id, it.id) },
                showReasoning = settings.showReasoning,
            ).validDefault()
        }

        internal fun fromJson(json: JSONObject): LlmProviderSettings = LlmProviderSettings(
            providers = json.optJSONArray("providers").objects().mapNotNull(::providerFromJson).distinctBy(LlmProvider::id),
            defaultModel = modelRefFromJson(json.optJSONObject("defaultModel")),
            showReasoning = json.optBoolean("showReasoning", true),
        ).validDefault()
    }
}

class LlmProviderStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(load())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun update(change: (LlmProviderSettings) -> LlmProviderSettings) {
        val next = change(mutableState.value).validDefault()
        if (next == mutableState.value) return
        prefs.edit().putString(KEY, next.toJson().toString()).apply()
        mutableState.value = next
    }

    private fun load(): LlmProviderSettings {
        val stored = prefs.getString(KEY, null)
        if (stored != null) return runCatching { LlmProviderSettings.fromJson(JSONObject(stored)) }
            .getOrDefault(LlmProviderSettings())
        val migrated = LlmProviderSettings.fromLegacy(LlmSettings.loadLegacy(prefs))
        prefs.edit().putString(KEY, migrated.toJson().toString()).also { LlmSettings.clearLegacy(it) }.apply()
        return migrated
    }

    companion object {
        private const val KEY = "llm_providers_v1"
        private var instance: LlmProviderStore? = null

        @Synchronized
        fun get(context: Context): LlmProviderStore = instance ?: LlmProviderStore(context.applicationContext).also { instance = it }
    }
}

private fun LlmModelRef.toJson(): JSONObject = JSONObject().put("providerId", providerId).put("modelId", modelId)

private fun modelRefFromJson(json: JSONObject?): LlmModelRef? {
    if (json == null) return null
    val providerId = json.optString("providerId")
    val modelId = json.optString("modelId")
    return if (providerId.isBlank() || modelId.isBlank()) null else LlmModelRef(providerId, modelId)
}

private fun LlmProvider.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("modelsUrl", modelsUrl)
    .put("baseUrl", connection.baseUrl).put("apiKey", connection.apiKey)
    .put("protocol", connection.protocol.value).put("headers", encodeHeaders(connection.headers))
    .put("thinkingMode", connection.thinkingMode.value).put("temperature", connection.temperature.toDouble())
    .put("maxTokens", connection.maxTokens).put("contextTokens", connection.contextTokens)
    .put("imageInputEnabled", connection.imageInputEnabled).put("sendMessageTime", connection.sendMessageTime)
    .put("anthropicAdaptiveThinking", connection.anthropicAdaptiveThinking)
    .put("models", JSONArray(models.map { JSONObject().put("id", it.id).put("name", it.name) }))

private fun providerFromJson(json: JSONObject): LlmProvider? = json.optString("id").takeIf(String::isNotBlank)?.let { id ->
    LlmProvider(
        id = id,
        name = json.optString("name"),
        modelsUrl = json.optString("modelsUrl"),
        connection = LlmSettings(
            baseUrl = json.optString("baseUrl"), apiKey = json.optString("apiKey"), model = "",
            protocol = LlmProtocol.fromValue(json.optString("protocol")), headers = decodeHeaders(json.optJSONArray("headers")),
            thinkingMode = ThinkingMode.fromValue(json.optString("thinkingMode")),
            temperature = json.optDouble("temperature", 0.8).toFloat(),
            maxTokens = json.optLong("maxTokens", 1024), contextTokens = json.optInt("contextTokens", LlmSettings.DEFAULT_CONTEXT_TOKENS),
            imageInputEnabled = json.optBoolean("imageInputEnabled"), sendMessageTime = json.optBoolean("sendMessageTime"),
            anthropicAdaptiveThinking = json.optBoolean("anthropicAdaptiveThinking", true),
        ),
        models = json.optJSONArray("models").objects().map { LlmModel(it.optString("id"), it.optString("name")) },
    ).normalized()
}

internal fun JSONArray?.objects(): List<JSONObject> = buildList {
    val array = this@objects ?: return@buildList
    for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
}
