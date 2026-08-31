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

class LlmStreamingTest {
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
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.lowercase().startsWith("content-length:")) {
                                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                                }
                            }
                            if (contentLength > 0) {
                                val buffer = CharArray(contentLength)
                                var total = 0
                                while (total < contentLength) {
                                    val n = reader.read(buffer, total, contentLength - total)
                                    if (n < 0) break
                                    total += n
                                }
                            }
                            val idx = responses.getAndIncrement().coerceAtMost(response.lastIndex)
                            val payload = response[idx]
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

    /** 极简 HTTP 服务器：读完请求头（含按 Content-Length 读 body）后返回固定响应。 */
    private suspend fun withServer(
        code: Int,
        contentType: String,
        response: ByteArray,
        block: suspend (String) -> Unit,
    ) {
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
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.lowercase().startsWith("content-length:")) {
                                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                                }
                            }
                            if (contentLength > 0) {
                                val buffer = CharArray(contentLength)
                                var total = 0
                                while (total < contentLength) {
                                    val n = reader.read(buffer, total, contentLength - total)
                                    if (n < 0) break
                                    total += n
                                }
                            }
                            val reason = if (code == 200) "OK" else "Unauthorized"
                            val header = buildString {
                                append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
                                append("Content-Type: ").append(contentType).append("\r\n")
                                append("Content-Length: ").append(response.size).append("\r\n")
                                append("\r\n")
                            }
                            it.getOutputStream().use { out ->
                                out.write(header.toByteArray(Charsets.UTF_8))
                                out.write(response)
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
}
