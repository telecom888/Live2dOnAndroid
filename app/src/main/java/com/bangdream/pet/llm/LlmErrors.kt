package com.bangdream.pet.llm

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.json.JSONObject

/** Bound before parsing/displaying provider details, and redact credentials even in transport errors. */
internal fun redactLlmError(text: String, settings: LlmSettings): String {
    var safe = text
    (listOf(settings.apiKey.trim()) + settings.headers.map { it.value.trim() })
        .filter(String::isNotEmpty).sortedByDescending(String::length).forEach {
            safe = safe.replace(it, "***")
        }
    return safe.take(2048)
}

internal fun llmFailureMessage(error: Throwable, settings: LlmSettings): String = when (error) {
    is SocketTimeoutException -> "LLM_TIMEOUT"
    is UnknownHostException -> "LLM_HOST_UNREACHABLE"
    else -> redactLlmError(error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName, settings)
}

internal fun providerFailure(raw: String, settings: LlmSettings, status: Int? = null): IOException {
    val json = runCatching { JSONObject(raw) }.getOrNull()
    val value = json?.opt("error")
    val details = when (value) {
        is JSONObject -> value.optString("message").ifBlank { value.optString("code") }
        is String -> value
        else -> json?.optString("message").orEmpty()
    }.ifBlank { raw }
    val prefix = status?.let { "HTTP $it" }.orEmpty()
    return IOException(redactLlmError(listOf(prefix, details).filter(String::isNotBlank).joinToString(": "), settings)
        .ifBlank { "LLM_INVALID_RESPONSE" })
}
