package com.bangdream.pet.llm

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class LlmChatClient {

    /**
     * 流式对话（OpenAI 兼容）。自动适配工具调用：
     * - 模型返回 tool_calls 时不当作文本输出，工具调用字段不会明文显示；
     * - 把 assistant(tool_calls) + tool 结果回传并继续请求，直到模型输出最终文本或达到最大轮数，
     *   避免「工具调用结束后早停、要用户再发一句才继续」。
     */
    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    fun streamCompletion(
        settings: LlmSettings,
        systemPrompt: String,
        messages: List<ChatMessage>,
    ): Flow<LlmStreamEvent> = flow {
        val normalized = settings.normalized()
        var requestMessages = messagesToJsonArray(systemPrompt, messages)
        var remainingRounds = MAX_TOOL_ROUNDS
        while (true) {
            val result = runSingleRequest(normalized, requestMessages) { event -> emit(event) }
            if (result.content.isNotBlank() || result.toolCalls.isEmpty() || remainingRounds <= 0) break
            requestMessages = appendToolMessages(requestMessages, result.toolCalls)
            remainingRounds--
        }
    }.flowOn(Dispatchers.IO)

    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    private suspend fun runSingleRequest(
        normalized: LlmSettings,
        requestMessages: JSONArray,
        onEvent: suspend (LlmStreamEvent) -> Unit,
    ): SingleRequestResult {
        val connection = (URL(normalized.endpoint()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Authorization", "Bearer ${normalized.apiKey}")
        }
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion(
            onCancelling = true,
            invokeImmediately = true,
        ) { cause ->
            if (cause != null) connection.disconnect()
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(buildRequestBody(normalized, requestMessages).toString())
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val rawDetail = connection.errorStream?.bufferedReader()?.use { it.readText().take(2048) }.orEmpty()
                val detail = if (normalized.apiKey.isEmpty()) rawDetail else rawDetail.replace(normalized.apiKey, "***")
                throw IOException("HTTP $code${if (detail.isBlank()) "" else ": $detail"}")
            }
            var reasoningEmitted = false
            val dataLines = mutableListOf<String>()
            val contentBuilder = StringBuilder()
            val toolCalls = mutableMapOf<Int, AccumulatedToolCall>()
            val textToolCalls = TextToolCallScanner()

            fun takeEvent(): ParsedSseEvent? {
                if (dataLines.isEmpty()) return null
                val data = dataLines.joinToString("\n").trim()
                dataLines.clear()
                if (data == "[DONE]") return ParsedSseEvent(done = true)
                if (data.isBlank()) return null
                val choice = runCatching {
                    JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                }.getOrNull() ?: return null
                val delta = choice.optJSONObject("delta")
                val reasoning = delta?.opt("reasoning_content")
                val content = delta?.opt("content")
                return ParsedSseEvent(
                    done = false,
                    reasoning = reasoning is String && reasoning.isNotEmpty(),
                    reasoningContent = (reasoning as? String)?.takeIf(String::isNotEmpty),
                    content = content as? String,
                    toolCalls = delta?.let { parseToolCallDeltas(it) },
                    finishReason = choice.optString("finish_reason").takeIf(String::isNotEmpty),
                )
            }

            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    when {
                        line.isBlank() -> {
                            val event = takeEvent()
                            if (event?.done == true) break
                            if (event?.reasoning == true && !reasoningEmitted) {
                                reasoningEmitted = true
                                onEvent(LlmStreamEvent.ReasoningStarted)
                            }
                            event?.reasoningContent?.let { onEvent(LlmStreamEvent.Reasoning(it)) }
                            event?.content?.takeIf(String::isNotEmpty)?.let { raw ->
                                val visible = textToolCalls.append(raw)
                                if (visible.isNotEmpty()) {
                                    contentBuilder.append(visible)
                                    onEvent(LlmStreamEvent.Content(visible))
                                }
                            }
                            event?.toolCalls?.forEach { mergeToolCall(toolCalls, it) }
                        }
                        line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
                    }
                }
                val event = takeEvent()
                if (event?.reasoning == true && !reasoningEmitted) onEvent(LlmStreamEvent.ReasoningStarted)
                event?.reasoningContent?.let { onEvent(LlmStreamEvent.Reasoning(it)) }
                event?.content?.takeIf(String::isNotEmpty)?.let { raw ->
                    val visible = textToolCalls.append(raw)
                    if (visible.isNotEmpty()) {
                        contentBuilder.append(visible)
                        onEvent(LlmStreamEvent.Content(visible))
                    }
                }
                event?.toolCalls?.forEach { mergeToolCall(toolCalls, it) }
            }
            textToolCalls.finish().forEach { toolCalls[it.index] = it }
            return SingleRequestResult(
                content = contentBuilder.toString(),
                toolCalls = toolCalls.values.sortedBy { it.index },
            )
        } finally {
            cancellationHandle.dispose()
            connection.disconnect()
        }
    }

    /** 把系统提示词 + 历史消息转成请求消息 JSON（带图片消息转内容块数组）。 */
    internal fun messagesToJsonArray(systemPrompt: String, messages: List<ChatMessage>): JSONArray =
        JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            messages.forEach { message ->
                val contentJson: Any = if (message.images.isEmpty()) {
                    message.content
                } else {
                    JSONArray().apply {
                        message.images.forEach { imageUrl ->
                            put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put("image_url", JSONObject().put("url", imageUrl)),
                            )
                        }
                        put(JSONObject().put("type", "text").put("text", message.content))
                    }
                }
                put(JSONObject().put("role", message.role).put("content", contentJson))
            }
        }

    /** 追加 assistant(tool_calls) + 各 tool 的执行结果消息。 */
    private fun appendToolMessages(
        requestMessages: JSONArray,
        toolCalls: List<AccumulatedToolCall>,
    ): JSONArray = JSONArray().apply {
        for (index in 0 until requestMessages.length()) put(requestMessages.get(index))
        put(
            JSONObject()
                .put("role", "assistant")
                .put("content", "")
                .put(
                    "tool_calls",
                    JSONArray().apply {
                        toolCalls.forEach { call ->
                            put(
                                JSONObject()
                                    .put("id", call.id)
                                    .put("type", "function")
                                    .put(
                                        "function",
                                        JSONObject()
                                            .put("name", call.name)
                                            .put("arguments", call.arguments),
                                    ),
                            )
                        }
                    },
                ),
        )
        toolCalls.forEach { call ->
            put(
                JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", call.id)
                    .put("content", toolResultContent(call)),
            )
        }
    }

    /** 工具执行结果（项目内置工具为空，返回成功占位，让模型基于参数继续回复）。 */
    private fun toolResultContent(call: AccumulatedToolCall): String = JSONObject()
        .put("status", "ok")
        .put("tool", call.name)
        .put(
            "arguments",
            runCatching { JSONObject(call.arguments.ifBlank { "{}" }) }
                .getOrElse { JSONObject().put("raw", call.arguments) },
        )
        .toString()

    /** 非流式单次请求：返回最终 content（content 为空时回退 reasoning_content）。 */
    suspend fun complete(
        settings: LlmSettings,
        messages: List<Pair<String, String>>,
    ): String = withContext(Dispatchers.IO) {
        val normalized = settings.normalized()
        val body = JSONObject()
            .put("model", normalized.model)
            .put(
                "messages",
                JSONArray().apply {
                    messages.forEach { (role, content) ->
                        put(JSONObject().put("role", role).put("content", content))
                    }
                },
            )
            .put("stream", false)
            .put("temperature", normalized.temperature.toDouble())
            .put("max_tokens", normalized.maxTokens.coerceAtLeast(64))
            .apply {
                when (normalized.thinkingMode) {
                    ThinkingMode.Auto -> Unit
                    ThinkingMode.Enabled -> put("thinking", JSONObject().put("type", "enabled"))
                    ThinkingMode.Disabled -> put("thinking", JSONObject().put("type", "disabled"))
                }
            }
        runCatching {
            val connection = (URL(normalized.endpoint()).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer ${normalized.apiKey}")
            }
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText().take(1024) }.orEmpty()
                    throw IOException("HTTP $code: $detail")
                }
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val message = JSONObject(text)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                val rawContent = message?.optString("content").orEmpty().trim()
                val content = stripTextToolCalls(rawContent)
                if (content.isNotEmpty()) {
                    content
                } else {
                    stripTextToolCalls(message?.optString("reasoning_content").orEmpty().trim())
                }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault("")
    }

    internal fun buildRequestBody(
        settings: LlmSettings,
        requestMessages: JSONArray,
    ): JSONObject = JSONObject()
        .put("model", settings.model)
        .put("messages", requestMessages)
        .put("stream", true)
        .put("temperature", settings.temperature.toDouble())
        .put("max_tokens", settings.maxTokens)
        .apply {
            when (settings.thinkingMode) {
                ThinkingMode.Auto -> Unit
                ThinkingMode.Enabled -> put("thinking", JSONObject().put("type", "enabled"))
                ThinkingMode.Disabled -> put("thinking", JSONObject().put("type", "disabled"))
            }
        }

    private fun parseToolCallDeltas(delta: JSONObject): List<ParsedToolCallDelta> {
        val array = delta.optJSONArray("tool_calls") ?: return emptyList()
        val result = mutableListOf<ParsedToolCallDelta>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val function = item.optJSONObject("function")
            result.add(
                ParsedToolCallDelta(
                    index = item.optInt("index", 0),
                    id = item.optString("id"),
                    name = function?.optString("name").orEmpty(),
                    arguments = function?.optString("arguments").orEmpty(),
                ),
            )
        }
        return result
    }

    private fun mergeToolCall(map: MutableMap<Int, AccumulatedToolCall>, delta: ParsedToolCallDelta) {
        val current = map.getOrPut(delta.index) { AccumulatedToolCall(delta.index, "", "", "") }
        if (delta.id.isNotBlank()) current.id = delta.id
        if (delta.name.isNotBlank()) current.name = delta.name
        current.arguments += delta.arguments
    }

    private data class ParsedSseEvent(
        val done: Boolean = false,
        val reasoning: Boolean = false,
        val reasoningContent: String? = null,
        val content: String? = null,
        val toolCalls: List<ParsedToolCallDelta>? = null,
        val finishReason: String? = null,
    )

    private data class ParsedToolCallDelta(
        val index: Int,
        val id: String,
        val name: String,
        val arguments: String,
    )

    internal data class AccumulatedToolCall(
        val index: Int,
        var id: String,
        var name: String,
        var arguments: String,
    )

    private data class SingleRequestResult(
        val content: String,
        val toolCalls: List<AccumulatedToolCall>,
    )

    companion object {
        private const val MAX_TOOL_ROUNDS = 6
    }
}

/**
 * 文本格式工具调用剥离器：部分模型（如 mimo）会在 content 里直接输出
 * `<tool_call><function=...><parameter=...>...</function></tool_call>` 文本标记，
 * 而不是标准 delta.tool_calls JSON。这里把它从可见文本中剥离并解析为工具调用，
 * 走统一的 appendToolMessages 回传链路，避免协议文本暴露在聊天界面。
 */
private class TextToolCallScanner {
    private val buffer = StringBuilder()
    private val calls = mutableListOf<LlmChatClient.AccumulatedToolCall>()
    private var counter = 0

    /** 追加一段流式文本，返回应显示给用户的文本（完整工具调用块已剥离）。 */
    fun append(chunk: String): String {
        buffer.append(chunk)
        val visible = StringBuilder()
        while (buffer.isNotEmpty()) {
            val start = buffer.indexOf(OPEN_TAG)
            if (start < 0) {
                // 没有完整开始标记：检查尾部是否为开始标记前缀（流式截断场景）
                val prefixLen = partialOpenTagPrefixLength()
                if (prefixLen == 0) {
                    visible.append(buffer)
                    buffer.setLength(0)
                } else if (prefixLen < buffer.length) {
                    visible.append(buffer, 0, buffer.length - prefixLen)
                    buffer.delete(0, buffer.length - prefixLen)
                }
                break
            }
            if (start > 0) visible.append(buffer, 0, start)
            buffer.delete(0, start)
            val end = buffer.indexOf(CLOSE_TAG)
            if (end < 0) break // 未闭合，保留等待后续分片
            val block = buffer.substring(0, end + CLOSE_TAG.length)
            buffer.delete(0, end + CLOSE_TAG.length)
            parseBlock(block)?.let { calls += it }
        }
        return visible.toString()
    }

    /** 流结束：丢弃未闭合的残余标记（不显示协议文本），返回已解析的工具调用。 */
    fun finish(): List<LlmChatClient.AccumulatedToolCall> {
        buffer.setLength(0)
        return calls
    }

    private fun partialOpenTagPrefixLength(): Int {
        var longest = 0
        for (len in 1 until OPEN_TAG.length) {
            if (buffer.endsWith(OPEN_TAG.substring(0, len))) longest = len
        }
        return longest
    }

    private fun parseBlock(block: String): LlmChatClient.AccumulatedToolCall? {
        val functionMatch = FUNCTION_REGEX.find(block) ?: return null
        val name = functionMatch.groupValues[1]
        val arguments = JSONObject()
        PARAMETER_REGEX.findAll(block).forEach { match ->
            arguments.put(match.groupValues[1], match.groupValues[2])
        }
        counter += 1
        return LlmChatClient.AccumulatedToolCall(
            index = TEXT_TOOL_INDEX_BASE + counter,
            id = "text_call_$counter",
            name = name,
            arguments = arguments.toString(),
        )
    }

    private companion object {
        const val OPEN_TAG = "<tool_call>"
        const val CLOSE_TAG = "</tool_call>"
        const val TEXT_TOOL_INDEX_BASE = 10_000
        val FUNCTION_REGEX = Regex("<function=([^>]+)>")
        val PARAMETER_REGEX = Regex("<parameter=([^>]+)>([\\s\\S]*?)</parameter>")
    }
}

/** 非流式响应也可能包含文本格式工具调用标记，剥离开避免显示协议文本。 */
private fun stripTextToolCalls(text: String): String =
    TEXT_TOOL_CALL_BLOCK_REGEX.replace(text, "")

private val TEXT_TOOL_CALL_BLOCK_REGEX = Regex("<tool_call>[\\s\\S]*?</tool_call>")
