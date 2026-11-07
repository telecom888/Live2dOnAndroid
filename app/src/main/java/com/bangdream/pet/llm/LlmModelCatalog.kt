package com.bangdream.pet.llm

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

internal fun llmBaseUrl(value: String): HttpUrl? = value.trim().toHttpUrlOrNull()?.takeIf {
    it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null
}

internal fun LlmProvider.catalogUrl(): HttpUrl? {
    val base = llmBaseUrl(connection.baseUrl) ?: return null
    if (modelsUrl.isNotBlank()) {
        val custom = modelsUrl.trim().toHttpUrlOrNull() ?: return null
        return custom.takeIf {
            it.scheme == base.scheme && it.host == base.host && it.port == base.port &&
                it.username.isEmpty() && it.password.isEmpty() && it.fragment == null
        }
    }
    val endpoint = connection.copy(model = "catalog").endpoint(stream = false).toHttpUrlOrNull() ?: return null
    val path = endpoint.encodedPath
    val modelsPath = when (connection.protocol) {
        LlmProtocol.OPENAI -> path.dropLast("/chat/completions".length) + "/models"
        LlmProtocol.ANTHROPIC -> path.removeSuffix("/messages") + "/models"
        LlmProtocol.GOOGLE -> path.substringBeforeLast('/')
    }
    return endpoint.newBuilder().encodedPath(modelsPath).build()
}

internal data class CatalogModel(val model: LlmModel, val compatible: Boolean?)

internal class CatalogFailure(val status: Int) : IOException("HTTP $status")
internal class InvalidCatalog : IOException()

internal class LlmModelCatalog(private val client: OkHttpClient = HTTP) {
    suspend fun fetch(provider: LlmProvider): List<CatalogModel> {
        val url = provider.catalogUrl() ?: throw InvalidCatalog()
        val settings = provider.connection.normalized()
        val result = linkedMapOf<String, CatalogModel>()
        val cursors = mutableSetOf<String>()
        var next: String? = null
        do {
            val pageUrl = url.newBuilder().apply {
                when (settings.protocol) {
                    LlmProtocol.ANTHROPIC -> next?.let { setQueryParameter("after_id", it) }
                    LlmProtocol.GOOGLE -> next?.let { setQueryParameter("pageToken", it) }
                    LlmProtocol.OPENAI -> Unit
                }
            }.build()
            val request = Request.Builder().url(pageUrl).get().header("Accept", "application/json")
            val auth = when {
                url.host == "api.xiaomimimo.com" && settings.protocol == LlmProtocol.OPENAI -> mapOf("api-key" to settings.apiKey)
                settings.protocol == LlmProtocol.ANTHROPIC -> mapOf("x-api-key" to settings.apiKey, "anthropic-version" to "2023-06-01")
                settings.protocol == LlmProtocol.GOOGLE -> mapOf("x-goog-api-key" to settings.apiKey)
                else -> mapOf("Authorization" to "Bearer ${settings.apiKey}")
            }
            settings.customHeaders().filterNot { (name, _) ->
                name.equals("Host", ignoreCase = true) || auth.keys.any { it.equals(name, ignoreCase = true) }
            }.forEach { (name, value) -> request.header(name, value) }
            auth.forEach { (name, value) -> request.header(name, value) }

            val page = client.newCall(request.build()).readCatalog()
            val protocol = settings.protocol
            val entries = page.optJSONArray(if (protocol == LlmProtocol.GOOGLE) "models" else "data") ?: throw InvalidCatalog()
            entries.objects().forEach { entry ->
                val id = modelId(protocol, entry.optString(if (protocol == LlmProtocol.GOOGLE) "name" else "id"))
                if (id.isNotBlank()) {
                    val name = when (protocol) {
                        LlmProtocol.GOOGLE -> entry.optString("displayName")
                        LlmProtocol.ANTHROPIC -> entry.optString("display_name")
                        LlmProtocol.OPENAI -> entry.optString("name")
                    }
                    result.putIfAbsent(id, CatalogModel(LlmModel(id, name), catalogCompatibility(protocol, entry)))
                }
            }
            next = when (protocol) {
                LlmProtocol.ANTHROPIC -> if (page.optBoolean("has_more")) {
                    page.optString("last_id").ifBlank { throw InvalidCatalog() }
                } else null
                LlmProtocol.GOOGLE -> page.optString("nextPageToken").takeIf(String::isNotBlank)
                LlmProtocol.OPENAI -> null
            }
            if (next != null && !cursors.add(next)) throw InvalidCatalog()
        } while (next != null)
        return result.values.filter { settings.protocol != LlmProtocol.GOOGLE || it.compatible != false }
    }

    private fun catalogCompatibility(protocol: LlmProtocol, entry: JSONObject): Boolean? {
        fun hasValue(key: String, predicate: (String) -> Boolean): Boolean? = entry.optJSONArray(key)?.let { values ->
            (0 until values.length()).any { predicate(values.optString(it)) }
        }
        return when (protocol) {
            LlmProtocol.GOOGLE -> hasValue("supportedGenerationMethods") { it == "generateContent" }
            LlmProtocol.ANTHROPIC -> true
            LlmProtocol.OPENAI -> hasValue("endpoints") { it.endsWith("/chat/completions") }
        }
    }

    private suspend fun Call.readCatalog(): JSONObject = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw CatalogFailure(it.code)
                        val source = it.body?.source() ?: throw InvalidCatalog()
                        if (source.request(MAX_RESPONSE_BYTES + 1)) throw InvalidCatalog()
                        runCatching { JSONObject(source.readUtf8()) }.getOrElse { throw InvalidCatalog() }
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
        val HTTP = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
