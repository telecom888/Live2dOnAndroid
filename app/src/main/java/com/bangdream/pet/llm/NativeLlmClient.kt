package com.bangdream.pet.llm

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Native Messages / GenerateContent transport. Model IDs are opaque user configuration. */
internal class NativeLlmClient {
    suspend fun stream(
        settings: LlmSettings,
        messages: JSONArray,
        sessionKey: String,
        onEvent: suspend (LlmStreamEvent) -> Unit,
    ) {
        request(settings, messages, sessionKey, stream = true) { connection ->
            val text = StringBuilder()
            var reasoningStarted = false
            val lines = mutableListOf<String>()
            suspend fun consume(): Boolean {
                if (lines.isEmpty()) return false
                val raw = lines.joinToString("\n")
                lines.clear()
                if (raw.trim() == "[DONE]") return true
                val payload = parseJson(raw)
                val result = parseNativeResponse(settings, payload, stream = true)
                for (event in result) {
                    if (event is LlmStreamEvent.Reasoning && !reasoningStarted) {
                        reasoningStarted = true
                        onEvent(LlmStreamEvent.ReasoningStarted)
                    }
                    if (event is LlmStreamEvent.Content) text.append(event.text)
                    onEvent(event)
                }
                return payload.optString("type") == "message_stop"
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    when {
                        line.isBlank() -> if (consume()) break
                        line.startsWith("data:") -> lines += line.substringAfter(':').trimStart()
                        line.trimStart().startsWith("{") -> {
                            // A JSON gateway error may arrive even on an SSE request.
                            throw providerFailure(line, settings)
                        }
                    }
                }
                consume()
            }
            if (text.isBlank()) throw IOException("LLM_EMPTY_RESPONSE")
        }
    }

    suspend fun complete(settings: LlmSettings, messages: JSONArray, sessionKey: String): String =
        request(settings, messages, sessionKey, stream = false) { connection ->
            val payload = parseJson(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val text = parseNativeResponse(settings, payload, stream = false)
                .filterIsInstance<LlmStreamEvent.Content>().joinToString("") { it.text }
            stripTextToolCalls(text).trim().ifBlank { throw IOException("LLM_EMPTY_RESPONSE") }
        }

    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    private suspend fun <T> request(
        settings: LlmSettings,
        messages: JSONArray,
        sessionKey: String,
        stream: Boolean,
        read: suspend (HttpURLConnection) -> T,
    ): T {
        val body = buildNativeRequest(settings, messages, stream)
        val connection = (URL(settings.endpoint(stream)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", if (stream) "text/event-stream" else "application/json")
            if (settings.protocol == LlmProtocol.ANTHROPIC) {
                setRequestProperty("x-api-key", settings.apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            } else {
                setRequestProperty("x-goog-api-key", settings.apiKey)
            }
            applyExtraHeaders(settings, sessionKey)
        }
        val cancellation = currentCoroutineContext().job.invokeOnCompletion(
            onCancelling = true, invokeImmediately = true,
        ) { if (it != null) connection.disconnect() }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                // Preserve the known HTTP status even if reading an error body fails.
                val detail = runCatching {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readTextBounded() }
                }.getOrNull().orEmpty()
                throw providerFailure(detail, settings, code)
            }
            return read(connection)
        } finally {
            cancellation.dispose()
            connection.disconnect()
        }
    }
}

internal fun buildNativeRequest(settings: LlmSettings, messages: JSONArray, stream: Boolean): JSONObject {
    val google = settings.protocol == LlmProtocol.GOOGLE
    val system = mutableListOf<String>()
    val turns = JSONArray()
    for (index in 0 until messages.length()) {
        val message = messages.getJSONObject(index)
        val role = message.getString("role")
        if (role == "system") {
            system += message.optString("content")
            continue
        }
        if (role != "user" && role != "assistant") throw IOException("LLM_TOOL_UNAVAILABLE")
        val parts = nativeContent(message.opt("content"), google)
        val nativeRole = if (google && role == "assistant") "model" else role
        // Consecutive assistant/user messages occur in edited histories. Both APIs accept
        // a single turn with their ordered content blocks, so merge adjacent equal roles.
        val field = if (google) "parts" else "content"
        val previous = turns.optJSONObject(turns.length() - 1)
        if (previous?.optString("role") == nativeRole) {
            val target = previous.getJSONArray(field)
            for (part in 0 until parts.length()) target.put(parts.get(part))
        } else {
            turns.put(JSONObject().put("role", nativeRole).put(field, parts))
        }
    }
    if (google) {
        val config = JSONObject().put("temperature", settings.temperature.toDouble()).put("maxOutputTokens", settings.maxTokens)
        if (settings.thinkingMode != ThinkingMode.Auto) {
            config.put("thinkingConfig", JSONObject().put("includeThoughts", settings.thinkingMode == ThinkingMode.Enabled))
        }
        return JSONObject().put("contents", turns).put("generationConfig", config).apply {
            if (system.isNotEmpty()) put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system.joinToString("\n\n")))))
        }
    }
    return JSONObject().put("model", settings.model).put("messages", turns)
        .put("max_tokens", settings.maxTokens).put("stream", stream).apply {
            if (system.isNotEmpty()) put("system", system.joinToString("\n\n"))
            when (settings.thinkingMode) {
                ThinkingMode.Auto -> put("temperature", settings.temperature.toDouble())
                ThinkingMode.Enabled -> {
                    val thinking = JSONObject().put("type", if (settings.anthropicAdaptiveThinking) "adaptive" else "enabled")
                    if (settings.anthropicAdaptiveThinking) thinking.put("display", "summarized")
                    else {
                        thinking.put("budget_tokens", 1024)
                        put("max_tokens", settings.maxTokens.coerceAtLeast(2048))
                    }
                    put("thinking", thinking)
                }
                ThinkingMode.Disabled -> {
                    // Always-on models can hide summaries without sending an invalid disabled mode.
                    put("thinking", if (settings.anthropicAdaptiveThinking) JSONObject().put("type", "adaptive").put("display", "omitted")
                        else JSONObject().put("type", "disabled"))
                }
            }
        }
}

private fun nativeContent(content: Any?, google: Boolean): JSONArray {
    fun text(value: String): JSONObject = if (google) JSONObject().put("text", value)
        else JSONObject().put("type", "text").put("text", value)
    if (content is String) return JSONArray().put(text(content))
    val blocks = content as? JSONArray ?: throw IOException("LLM_INVALID_RESPONSE")
    return JSONArray().apply {
        for (index in 0 until blocks.length()) {
            val block = blocks.getJSONObject(index)
            when (block.optString("type")) {
                "text" -> put(text(block.getString("text")))
                "image_url" -> {
                    val url = block.getJSONObject("image_url").getString("url")
                    val match = Regex("^data:([^;,]+);base64,([\\s\\S]+)$").matchEntire(url)
                    if (google) {
                        if (match == null) throw IOException("LLM_IMAGE_INLINE_REQUIRED")
                        put(JSONObject().put("inlineData", JSONObject().put("mimeType", match.groupValues[1]).put("data", match.groupValues[2])))
                    } else {
                        val source = if (match != null) JSONObject().put("type", "base64")
                            .put("media_type", match.groupValues[1]).put("data", match.groupValues[2])
                        else JSONObject().put("type", "url").put("url", url)
                        put(JSONObject().put("type", "image").put("source", source))
                    }
                }
                else -> throw IOException("LLM_INVALID_RESPONSE")
            }
        }
    }
}

internal fun parseNativeResponse(settings: LlmSettings, payload: JSONObject, stream: Boolean): List<LlmStreamEvent> {
    if (payload.has("error") && !payload.isNull("error")) throw providerFailure(payload.toString(), settings)
    val events = mutableListOf<LlmStreamEvent>()
    fun append(block: JSONObject, thinking: Boolean, field: String) {
        val value = block.opt(field) as? String ?: return
        if (value.isNotEmpty()) events += if (thinking) LlmStreamEvent.Reasoning(value) else LlmStreamEvent.Content(value)
    }
    if (settings.protocol == LlmProtocol.GOOGLE) {
        if (payload.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty().isNotEmpty()) throw IOException("LLM_CONTENT_FILTERED")
        val candidate = payload.optJSONArray("candidates")?.optJSONObject(0) ?: return events
        val finish = candidate.optString("finishReason")
        if (finish in setOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY")) throw IOException("LLM_CONTENT_FILTERED")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return events
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            if (part.has("functionCall")) throw IOException("LLM_TOOL_UNAVAILABLE")
            append(part, part.optBoolean("thought"), "text")
        }
    } else {
        fun block(value: JSONObject) {
            when (value.optString("type")) {
                "thinking", "thinking_delta" -> append(value, true, "thinking")
                "text", "text_delta" -> append(value, false, "text")
                "tool_use", "input_json_delta" -> throw IOException("LLM_TOOL_UNAVAILABLE")
                // signature_delta/redacted_thinking are opaque, never visible text.
            }
        }
        if (stream) {
            when (payload.optString("type")) {
                "content_block_start" -> payload.optJSONObject("content_block")?.let(::block)
                "content_block_delta" -> payload.optJSONObject("delta")?.let(::block)
                "message_delta" -> if (payload.optJSONObject("delta")?.optString("stop_reason") == "refusal") throw IOException("LLM_CONTENT_FILTERED")
            }
        } else {
            if (payload.optString("stop_reason") == "refusal") throw IOException("LLM_CONTENT_FILTERED")
            val blocks = payload.optJSONArray("content") ?: return events
            for (index in 0 until blocks.length()) blocks.optJSONObject(index)?.let(::block)
        }
    }
    return events
}

private fun parseJson(raw: String): JSONObject = try { JSONObject(raw) }
catch (error: Exception) { throw IOException("LLM_INVALID_RESPONSE", error) }
