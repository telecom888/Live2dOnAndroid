package com.bangdream.pet.llm

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class LlmStreamingTest {
    @Test
    fun nativeProtocolsPreserveForbiddenStatusAndRedactProviderErrors() = runBlocking {
        for (protocol in listOf(LlmProtocol.GOOGLE, LlmProtocol.ANTHROPIC)) {
            withServer(403, "application/json", """{"error":{"message":"denied test-key"}}""".toByteArray()) { baseUrl ->
                val settings = LlmSettings(baseUrl = baseUrl, apiKey = "test-key", protocol = protocol)
                val streaming = runCatching { LlmChatClient().streamCompletion(settings, "persona", emptyList()).toList() }.exceptionOrNull()
                val complete = runCatching { LlmChatClient().complete(settings, listOf("user" to "hello")) }.exceptionOrNull()
                assertEquals("HTTP 403: denied ***", llmFailureMessage(requireNotNull(streaming), settings))
                assertEquals("HTTP 403: denied ***", llmFailureMessage(requireNotNull(complete), settings))
            }
        }
    }

    @Test
    fun nativeProtocolsReportStreamErrorsAndEmptyReplies() = runBlocking {
        for (protocol in listOf(LlmProtocol.GOOGLE, LlmProtocol.ANTHROPIC)) {
            withServer(200, "text/event-stream", "data: {\"error\":{\"message\":\"quota exhausted\"}}\n\n".toByteArray()) { baseUrl ->
                val error = runCatching { LlmChatClient().streamCompletion(LlmSettings(baseUrl = baseUrl, protocol = protocol), "persona", emptyList()).toList() }.exceptionOrNull()
                assertEquals("quota exhausted", error?.message)
            }
            withServer(200, "text/event-stream", "data: {}\n\n".toByteArray()) { baseUrl ->
                val error = runCatching { LlmChatClient().streamCompletion(LlmSettings(baseUrl = baseUrl, protocol = protocol), "persona", emptyList()).toList() }.exceptionOrNull()
                assertEquals("LLM_EMPTY_RESPONSE", error?.message)
            }
        }
    }

    @Test
    fun nativeProtocolsUseTheirOwnPathsHeadersAndReasoningStreams() = runBlocking {
        for (protocol in listOf(LlmProtocol.GOOGLE, LlmProtocol.ANTHROPIC)) {
            val requests = java.util.concurrent.CopyOnWriteArrayList<Pair<JSONObject, List<String>>>()
            withServer(200, "text/event-stream", listOf(byteArrayOf()), responseForRequest = { raw, headers ->
                val request = JSONObject(raw)
                requests += request to headers
                val streaming = headers.first().contains(":streamGenerateContent") || request.optBoolean("stream")
                if (protocol == LlmProtocol.GOOGLE) {
                    val payload = """{"candidates":[{"content":{"parts":[{"thought":true,"text":"summary"},{"thoughtSignature":"opaque"},{"text":"hello"}]},"finishReason":"STOP"}]}"""
                    (if (streaming) "data: $payload" else payload).toByteArray()
                } else if (streaming) {
                    ("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"summary\"}}\n\n" +
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"signature_delta\",\"signature\":\"opaque\"}}\n\n" +
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}\n\n" +
                        "data: {\"type\":\"message_stop\"}\n\n").toByteArray()
                } else """{"content":[{"type":"thinking","thinking":"summary"},{"type":"text","text":"hello"}]}""".toByteArray()
            }) { baseUrl ->
                val settings = LlmSettings(baseUrl = baseUrl, apiKey = "test-key", model = "public-alias",
                    protocol = protocol, thinkingMode = ThinkingMode.Enabled)
                val client = LlmChatClient()
                val events = client.streamCompletion(settings, "persona", listOf(ChatMessage("u", "user", "question", 0))).toList()
                assertEquals(listOf(LlmStreamEvent.ReasoningStarted, LlmStreamEvent.Reasoning("summary"), LlmStreamEvent.Content("hello")), events)
                assertEquals("hello", client.complete(settings, listOf("user" to "question")))
                assertEquals(2, requests.size)
                val (body, headers) = requests.first()
                if (protocol == LlmProtocol.GOOGLE) {
                    assertTrue(headers.first().contains("/v1beta/models/public-alias:streamGenerateContent?alt=sse"))
                    assertTrue(headers.any { it.equals("x-goog-api-key: test-key", true) })
                    assertTrue(body.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getBoolean("includeThoughts"))
                    assertTrue(requests.last().second.first().contains(":generateContent"))
                } else {
                    assertTrue(headers.first().contains("/v1/messages"))
                    assertTrue(headers.any { it.equals("x-api-key: test-key", true) })
                    assertTrue(headers.any { it.equals("anthropic-version: 2023-06-01", true) })
                    assertEquals("public-alias", body.getString("model"))
                    assertEquals("summarized", body.getJSONObject("thinking").getString("display"))
                }
                assertTrue(headers.none { it.startsWith("Authorization:", true) })
            }
        }
    }

    @Test
    fun gatewaySummaryIsRequestedOverHttpBeforeReasoningCanBeDisplayed() = runBlocking {
        val received = java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
        withServer(200, "text/event-stream", listOf(byteArrayOf()), responseForRequest = { raw, _ ->
            val request = JSONObject(raw)
            received += request
            // Model the gateway contract: reasoning text is sent only when visibility is requested.
            val showThoughts = request.optJSONObject("reasoning")?.optString("summary") == "auto"
            if (!request.optBoolean("stream")) {
                val message = JSONObject().put("content", "hello")
                if (showThoughts) message.put("reasoning_content", "A short thought summary")
                JSONObject().put("choices", JSONArray().put(JSONObject().put("message", message)))
                    .toString().toByteArray()
            } else buildString {
                if (showThoughts) append("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"A short thought summary\"}}]}\n\n")
                append("data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\ndata: [DONE]\n\n")
            }.toByteArray()
        }) { baseUrl ->
            val client = LlmChatClient()
            val settings = LlmSettings(baseUrl = baseUrl, model = "public-alias")
            // Reproduce the previous request: DeepSeek's extension does not request a Gemini summary.
            val oldBody = client.buildRequestBody(settings, JSONArray()).put(
                "thinking", JSONObject().put("type", "enabled"),
            )
            val connection = java.net.URL("$baseUrl/chat/completions").openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.doOutput = true
                connection.outputStream.use { it.write(oldBody.toString().toByteArray()) }
                val oldResponse = connection.inputStream.bufferedReader().use { it.readText() }
                assertTrue(oldResponse.contains("hello"))
                assertTrue(!oldResponse.contains("reasoning_content"))
            } finally {
                connection.disconnect()
            }
            val events = client.streamCompletion(
                settings.copy(thinkingMode = ThinkingMode.Enabled), "system", emptyList(),
            ).toList()
            assertEquals(listOf(
                LlmStreamEvent.ReasoningStarted,
                LlmStreamEvent.Reasoning("A short thought summary"),
                LlmStreamEvent.Content("hello"),
            ), events)
            assertEquals(2, received.size)
            assertEquals("medium", received.last().getString("reasoning_effort"))
            assertEquals("auto", received.last().getJSONObject("reasoning").getString("summary"))

            // Non-streaming paths share the same request builder and must request visibility too.
            assertEquals("hello", client.complete(settings.copy(thinkingMode = ThinkingMode.Enabled), emptyList()))
            assertEquals(3, received.size)
            assertTrue(!received.last().getBoolean("stream"))
            assertEquals("auto", received.last().getJSONObject("reasoning").getString("summary"))
        }
    }

    @Test
    fun scrollBridgeReasoningAliasesReachTheExistingReasoningEvents() = runBlocking {
        val deltas = listOf(
            JSONObject().put("reasoning_content", "first ").put("reasoning", "duplicate"),
            JSONObject().put("reasoning_content", JSONObject.NULL).put("reasoning", "second "),
            JSONObject().put("reasoning_details", JSONArray()
                .put(JSONObject().put("type", "reasoning.encrypted").put("data", "opaque"))
                .put(JSONObject().put("type", "reasoning.text").put("text", "third"))
                .put(JSONObject().put("type", "reasoning.summary").put("summary", " summary")))
                .put("content", "hello"),
        )
        // Like ScrollBridge's SSE forwarding: preserve fields and split reasoning across events.
        val body = deltas.joinToString("\n\n") { delta ->
            "data: " + JSONObject().put("choices", JSONArray().put(JSONObject().put("delta", delta)))
        }
        // No trailing blank line: the final event must also be consumed at EOF.
        withServer(200, "text/event-stream", body.toByteArray()) { baseUrl ->
            val events = LlmChatClient().streamCompletion(
                LlmSettings(baseUrl = baseUrl), "system", emptyList(),
            ).toList()
            assertEquals(listOf(
                LlmStreamEvent.ReasoningStarted,
                LlmStreamEvent.Reasoning("first "),
                LlmStreamEvent.Reasoning("second "),
                LlmStreamEvent.Reasoning("third summary"),
                LlmStreamEvent.Content("hello"),
            ), events)
        }
    }

    @Test
    fun aliasReasoningOnlyDoesNotBecomeTheFinalReply() = runBlocking {
        assertStreamingFailure(
            "data: {\"choices\":[{\"delta\":{\"reasoning\":\"reasoning only\"}}]}\n\ndata: [DONE]\n\n",
            "LLM_EMPTY_RESPONSE",
        )
        for (field in listOf("reasoning", "reasoning_details")) {
            val message = JSONObject().put("content", JSONObject.NULL).put(field, "reasoning only")
            val body = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", message)))
            withServer(200, "application/json", body.toString().toByteArray()) { baseUrl ->
                val error = runCatching {
                    LlmChatClient().complete(LlmSettings(baseUrl = baseUrl), emptyList())
                }.exceptionOrNull()
                assertEquals("LLM_EMPTY_RESPONSE", error?.message)
            }
        }
    }

    @Test
    fun forbiddenStatusReachesUiForStreamingAndNonStreamingRequests() = runBlocking {
        val settings = LlmSettings(apiKey = "test-key")
        val responses = listOf(
            "application/json" to "{\"error\":{\"message\":\"Access denied for test-key\"}}",
            "text/plain" to "Forbidden",
            "text/plain" to "",
        )
        for ((contentType, body) in responses) {
            withServer(403, contentType, body.toByteArray()) { baseUrl ->
                val configured = settings.copy(baseUrl = baseUrl)
                val streamingError = runCatching {
                    LlmChatClient().streamCompletion(configured, "system", emptyList()).toList()
                }.exceptionOrNull()
                val completeError = runCatching {
                    LlmChatClient().complete(configured, listOf("user" to "hello"))
                }.exceptionOrNull()
                val expected = when (body) {
                    "" -> "HTTP 403"
                    "Forbidden" -> "HTTP 403: Forbidden"
                    else -> "HTTP 403: Access denied for ***"
                }
                for (error in listOf(streamingError, completeError)) {
                    assertTrue("Expected HTTP failure, got $error", error is java.io.IOException)
                    assertEquals(expected, llmFailureMessage(requireNotNull(error), configured))
                }
            }
        }
    }

    @Test
    fun reportsProviderErrorInsideSuccessfulSseResponse() = runBlocking {
        assertStreamingFailure("data: {\"error\":{\"message\":\"quota exhausted\"}}\n\n", "quota exhausted")
    }

    @Test
    fun reportsNamedSseErrorAtEndOfFileAndRedactsCredentials() = runBlocking {
        assertStreamingFailure("event: error\ndata: {\"message\":\"bad test-key\"}", "bad ***")
    }

    @Test
    fun reportsJsonErrorFromNonStreamingGateway() = runBlocking {
        assertStreamingFailure("{\"error\":{\"message\":\"model unavailable\"}}", "model unavailable")
    }

    @Test
    fun reportsEmptyAndMalformedResponses() = runBlocking {
        assertStreamingFailure("data: [DONE]\n\n", "LLM_EMPTY_RESPONSE")
        assertStreamingFailure("data: broken-json\n\n", "LLM_INVALID_RESPONSE")
    }

    @Test
    fun reportsFailureAfterPartialContent() = runBlocking {
        assertStreamingFailure(
            "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n" +
                "data: {\"error\":{\"message\":\"upstream disconnected\"}}\n\n",
            "upstream disconnected",
        )
    }

    @Test
    fun reportsContentFilterInsteadOfSilentlySavingPartialReply() = runBlocking {
        assertStreamingFailure("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"content_filter\"}]}\n\n", "LLM_CONTENT_FILTERED")
    }

    @Test
    fun ignoresUsageAndHandlesLastContentWithoutBlankLine() = runBlocking {
        withServer(200, "text/event-stream", (
            "data: {\"choices\":[],\"usage\":{\"total_tokens\":1}}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}"
        ).toByteArray()) { baseUrl ->
            assertEquals(listOf(LlmStreamEvent.Content("hello")), LlmChatClient().streamCompletion(
                LlmSettings(baseUrl = baseUrl, apiKey = "test-key"), "system", emptyList(),
            ).toList())
        }
    }

    @Test
    fun nonStreamingHttpErrorsAreNotSwallowed() = runBlocking {
        withServer(429, "application/json", "{\"error\":{\"message\":\"limit test-key\"}}".toByteArray()) { baseUrl ->
            val error = runCatching { LlmChatClient().complete(
                LlmSettings(baseUrl = baseUrl, apiKey = "test-key"), listOf("user" to "hello"),
            ) }.exceptionOrNull()
            assertEquals("HTTP 429: limit ***", error?.message)
        }
    }

    @Test
    fun nonStreamingSuccessAndEmptyResponse() = runBlocking {
        withServer(200, "application/json", "{\"choices\":[{\"message\":{\"content\":\"hello\"}}]}".toByteArray()) { baseUrl ->
            assertEquals("hello", LlmChatClient().complete(LlmSettings(baseUrl = baseUrl), emptyList()))
        }
        withServer(200, "application/json", "{\"choices\":[]}".toByteArray()) { baseUrl ->
            val error = runCatching { LlmChatClient().complete(LlmSettings(baseUrl = baseUrl), emptyList()) }.exceptionOrNull()
            assertEquals("LLM_EMPTY_RESPONSE", error?.message)
        }
    }

    @Test
    fun nonStreamingReasoningIsNotPublishedAsReply() = runBlocking {
        val body = "{\"choices\":[{\"message\":{\"content\":null,\"reasoning_content\":\"private reasoning\"}}]}"
        withServer(200, "application/json", body.toByteArray()) { baseUrl ->
            val error = runCatching { LlmChatClient().complete(LlmSettings(baseUrl = baseUrl), emptyList()) }.exceptionOrNull()
            assertEquals("LLM_EMPTY_RESPONSE", error?.message)
        }
    }

    @Test
    fun repeatedToolCallsFailWithExplicitLimit() = runBlocking {
        assertStreamingFailure(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call1\",\"function\":{\"name\":\"missing\",\"arguments\":\"{}\"}}]}}]}\n\ndata: [DONE]\n\n",
            "LLM_TOOL_LIMIT",
        )
    }

    private suspend fun assertStreamingFailure(body: String, expected: String) {
        withServer(200, "text/event-stream", body.toByteArray()) { baseUrl ->
            val error = runCatching { LlmChatClient().streamCompletion(
                LlmSettings(baseUrl = baseUrl, apiKey = "test-key"), "system", emptyList(),
            ).toList() }.exceptionOrNull()
            assertTrue("Expected $expected, got $error", error?.message?.contains(expected) == true)
        }
    }

    @Test
    fun parsesReasoningAndContentSseEvents() = runBlocking {
        val body = buildString {
            append("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hidden\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"\u4f60\u597d\"}}]}\n\n")
            append("data: [DONE]\n\n")
        }.toByteArray()
        withServer(200, "text/event-stream", body) { baseUrl ->
            val events = LlmChatClient().streamCompletion(
                LlmSettings(baseUrl = baseUrl, apiKey = "test-key"),
                "system",
                emptyList(),
            ).toList()
            // 当前实现顺序：ReasoningStarted → Reasoning(text) → Content(text)
            assertEquals(
                listOf(
                    LlmStreamEvent.ReasoningStarted,
                    LlmStreamEvent.Reasoning("hidden"),
                    LlmStreamEvent.Content("\u4f60\u597d"),
                ),
                events,
            )
        }
    }

    @Test
    fun redactsApiKeyFromHttpError() = runBlocking {
        val key = "very-secret-key"
        withServer(401, "text/plain", "bad key: $key".toByteArray()) { baseUrl ->
            val error = runCatching {
                LlmChatClient().streamCompletion(
                    LlmSettings(baseUrl = baseUrl, apiKey = key),
                    "system",
                    emptyList(),
                ).toList()
            }.exceptionOrNull()
            assertTrue(error?.message?.contains("***") == true)
            assertTrue(error?.message?.contains(key) == false)
        }
    }

    @Test
    fun stripsTextFormatToolCallAndContinues() = runBlocking {
        val toolCallBody = buildString {
            append("data: {\"choices\":[{\"delta\":{\"content\":\"<tool_call><function=memory_get><parameter=limit>50</parameter></function></tool_call>\"}}]}\n\n")
            append("data: [DONE]\n\n")
        }.toByteArray()
        val replyBody = buildString {
            append("data: {\"choices\":[{\"delta\":{\"content\":\"\u641c\u7d22\u5230\u4e86\u4f60\u7684\u8bb0\u5fc6\uff1a\u5f53\u521d\u7684\u7ea6\u5b9a\u3002\"}}]}\n\n")
            append("data: [DONE]\n\n")
        }.toByteArray()
        withServer(200, "text/event-stream", listOf(toolCallBody, replyBody)) { baseUrl ->
            val events = LlmChatClient().streamCompletion(
                LlmSettings(baseUrl = baseUrl, apiKey = "test-key"),
                "system",
                emptyList(),
            ).toList()
            // 工具调用标记不应作为可见文本输出
            assertTrue(events.none { it is LlmStreamEvent.Content && it.text.contains("<tool_call>") })
            // 回传工具结果后，模型继续输出的正文应该出现
            assertTrue(events.any { it is LlmStreamEvent.Content && it.text.contains("\u641c\u7d22") })
        }
    }

    /** 极简 HTTP 服务器：读完请求头（含按 Content-Length 读 body）后按请求序号返回对应响应。 */
    private suspend fun withServer(
        code: Int,
        contentType: String,
        response: List<ByteArray>,
        responseForRequest: ((String, List<String>) -> ByteArray)? = null,
        block: suspend (String) -> Unit,
    ) {
        val responses = java.util.concurrent.atomic.AtomicInteger(0)
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress("127.0.0.1", 0))
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    try {
                        socket.use {
                            val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.UTF_8))
                            var contentLength = 0
                            val headers = mutableListOf<String>()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                headers += line
                                if (line.lowercase().startsWith("content-length:")) {
                                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                                }
                            }
                            val requestBody = StringBuilder()
                            if (contentLength > 0) {
                                val buffer = CharArray(contentLength)
                                var total = 0
                                while (total < contentLength) {
                                    val n = reader.read(buffer, total, contentLength - total)
                                    if (n < 0) break
                                    requestBody.append(buffer, total, n)
                                    total += n
                                }
                            }
                            val idx = responses.getAndIncrement().coerceAtMost(response.lastIndex)
                            val payload = responseForRequest?.invoke(requestBody.toString(), headers) ?: response[idx]
                            val reason = if (code == 200) "OK" else "Unauthorized"
                            val header = buildString {
                                append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
                                append("Content-Type: ").append(contentType).append("\r\n")
                                append("Content-Length: ").append(payload.size).append("\r\n")
                                append("\r\n")
                            }
                            it.getOutputStream().use { out ->
                                out.write(header.toByteArray(Charsets.UTF_8))
                                out.write(payload)
                                out.flush()
                            }
                        }
                    } catch (_: Exception) {
                        // 客户端可能提前断开，忽略
                    }
                }
            } catch (_: Exception) {
                // 服务器关闭
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}")
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }

    private suspend fun withServer(
        code: Int,
        contentType: String,
        response: ByteArray,
        block: suspend (String) -> Unit,
    ): Unit = withServer(code, contentType, listOf(response), block = block)
}
