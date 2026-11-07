package com.bangdream.pet.llm

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProviderTest {
    @Test
    fun legacyConfigurationMigratesAndRoundTripsWithIndependentDefaultAndReasoningPreference() {
        val legacy = LlmSettings(
            baseUrl = "https://gateway.test/api/v1/chat/completions", model = "org/model", apiKey = "test-key",
            thinkingMode = ThinkingMode.Enabled, showReasoning = false, maxTokens = 96_000,
            headers = listOf(LlmHeader("X-Tenant", "test"), LlmHeader("X-Empty")),
        )
        val migrated = LlmProviderSettings.fromLegacy(legacy)
        val originalRef = requireNotNull(migrated.defaultModel)
        val route = requireNotNull(migrated.route(originalRef))
        assertEquals(legacy.copy(headers = legacy.headers.take(1)), migrated.activeSettings())

        val second = LlmProvider("second", "Other", legacy.copy(baseUrl = "https://other.test"), models = listOf(LlmModel("org/model"))).normalized()
        val next = migrated.replace(second, LlmModelRef(second.id, "org/model"))
        val restored = LlmProviderSettings.fromJson(JSONObject(next.toJson().toString()))
        assertEquals(next, restored)
        assertEquals(route, restored.route(originalRef))
        assertNotEquals(route, restored.route(restored.defaultModel))
        assertFalse(restored.activeSettings().showReasoning)

        val original = requireNotNull(restored.provider(originalRef.providerId))
        val changed = restored.replace(original.copy(connection = original.connection.copy(baseUrl = "https://new.test")), restored.defaultModel)
        assertNotEquals(route, changed.route(originalRef))
        assertNull(changed.copy(providers = emptyList()).validDefault().defaultModel)
        assertFalse(route.toJson().toString().contains("test-key"))
    }

    @Test
    fun maximumOutputTokensRoundTripAndReachAllRequestBodies() {
        val limit = 3_000_000_000L
        for (protocol in LlmProtocol.entries) {
            val config = LlmProviderSettings.fromLegacy(LlmSettings(apiKey = "test-key", maxTokens = limit, protocol = protocol))
            val settings = LlmProviderSettings.fromJson(JSONObject(config.toJson().toString())).activeSettings()
            val body = when (protocol) {
                LlmProtocol.OPENAI -> LlmChatClient().buildRequestBody(settings, JSONArray())
                else -> buildNativeRequest(settings, JSONArray(), stream = true)
            }
            val sent = if (protocol == LlmProtocol.GOOGLE) body.getJSONObject("generationConfig").getLong("maxOutputTokens")
            else body.getLong("max_tokens")
            assertEquals(limit, sent)
        }
    }

    @Test
    fun catalogDerivesEndpointsAndRejectsCrossOriginOverrides() {
        fun url(address: String, protocol: LlmProtocol = LlmProtocol.OPENAI, override: String = "") =
            LlmProvider("test", connection = LlmSettings(baseUrl = address, protocol = protocol), modelsUrl = override).catalogUrl()?.toString()

        assertEquals("https://proxy.test/prefix/v1/models", url("https://proxy.test/prefix/v1/chat/completions/"))
        assertEquals("https://proxy.test/prefix/v1/models", url("https://proxy.test/prefix/v1/messages", LlmProtocol.ANTHROPIC))
        assertEquals("https://proxy.test/prefix/v1beta/models", url("https://proxy.test/prefix/v1", LlmProtocol.GOOGLE))
        assertEquals("https://proxy.test/catalog?type=chat", url("https://proxy.test/v1", override = "https://proxy.test/catalog?type=chat"))
        assertNull(url("https://proxy.test/v1", override = "https://other.test/models"))
        assertNull(url("https://proxy.test/v1", override = "http://proxy.test/models"))
    }

    @Test
    fun catalogsUseGetAndProtocolAuthAndFollowPagination() = runBlocking {
        val requested = CopyOnWriteArrayList<String>()
        withServer({ request ->
            val path = request.path
            val headers = request.headers
            requested += listOf(request.method, path, headers["x-tenant"],
                headers["authorization"], headers["x-api-key"], headers["x-goog-api-key"],
                headers["anthropic-version"]).joinToString("|")
            val payload = when (path) {
                "/router/api/v1/models" -> """{"data":[{"id":"org/chat","name":"Chat","endpoints":["/api/v1/chat/completions"]},{"id":"embed","endpoints":["/embeddings"]}]}"""
                "/claude/v1/models" -> """{"data":[{"id":"claude-1","display_name":"Claude One"}],"has_more":true,"last_id":"claude-1"}"""
                "/claude/v1/models?after_id=claude-1" -> """{"data":[{"id":"claude-2"}],"has_more":false}"""
                "/gemini/v1beta/models" -> """{"models":[{"name":"models/chat-a","supportedGenerationMethods":["generateContent"]},{"name":"models/embed","supportedGenerationMethods":["embedContent"]}],"nextPageToken":"a+b"}"""
                "/gemini/v1beta/models?pageToken=a%2Bb" -> """{"models":[{"name":"models/chat-a"},{"name":"models/chat-b","displayName":"Chat B"}]}"""
                else -> "{}"
            }
            LocalResponse(200, payload)
        }) { base ->
            val catalog = LlmModelCatalog()
            suspend fun fetch(path: String, protocol: LlmProtocol) = catalog.fetch(LlmProvider(
                id = "test",
                connection = LlmSettings(baseUrl = "$base/$path", protocol = protocol, apiKey = "test-key",
                    headers = listOf(LlmHeader("X-Tenant", "test"), LlmHeader("Authorization", "custom"))),
            ))

            assertEquals(listOf(CatalogModel(LlmModel("org/chat", "Chat"), true), CatalogModel(LlmModel("embed"), false)),
                fetch("router/api/v1", LlmProtocol.OPENAI))
            assertEquals(listOf("claude-1", "claude-2"), fetch("claude", LlmProtocol.ANTHROPIC).map { it.model.id })
            assertEquals(listOf("chat-a", "chat-b"), fetch("gemini", LlmProtocol.GOOGLE).map { it.model.id })
        }
        assertEquals(listOf(
            "GET|/router/api/v1/models|test|Bearer test-key|null|null|null",
            "GET|/claude/v1/models|test|null|test-key|null|2023-06-01",
            "GET|/claude/v1/models?after_id=claude-1|test|null|test-key|null|2023-06-01",
            "GET|/gemini/v1beta/models|test|null|null|test-key|null",
            "GET|/gemini/v1beta/models?pageToken=a%2Bb|test|null|null|test-key|null",
        ), requested)
    }

    @Test
    fun catalogSurfacesEmptyAndMalformedResultsAndDoesNotFollowRedirects() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        withServer({ request ->
            val path = request.path
            requests += path
            when (path) {
                "/empty/models" -> LocalResponse(200, """{"data":[]}""")
                "/malformed/models" -> LocalResponse(200, """{"error":"test-key"}""")
                "/redirect/models" -> LocalResponse(302, "", "Location: /target\r\n")
                else -> LocalResponse(401, "test-key")
            }
        }) { base ->
            val catalog = LlmModelCatalog()
            suspend fun fetch(path: String) = catalog.fetch(LlmProvider("test", connection = LlmSettings(baseUrl = "$base/$path", apiKey = "test-key")))
            assertTrue(fetch("empty").isEmpty())
            assertTrue(runCatching { fetch("malformed") }.exceptionOrNull() is InvalidCatalog)
            assertEquals(302, (runCatching { fetch("redirect") }.exceptionOrNull() as CatalogFailure).status)
            val denied = runCatching { fetch("denied") }.exceptionOrNull() as CatalogFailure
            assertEquals(401, denied.status)
            assertEquals("HTTP 401", denied.message)
        }
        assertFalse("/target" in requests)
    }

    private data class LocalRequest(val method: String, val path: String, val headers: Map<String, String>)
    private data class LocalResponse(val code: Int, val body: String, val extraHeaders: String = "")

    private suspend fun withServer(handler: (LocalRequest) -> LocalResponse, block: suspend (String) -> Unit) {
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            while (!server.isClosed) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val (method, path) = reader.readLine().split(' ')
                    val headers = generateSequence(reader::readLine).takeWhile(String::isNotEmpty).associate {
                        it.substringBefore(':').lowercase() to it.substringAfter(':').trim()
                    }
                    val reply = handler(LocalRequest(method, path, headers))
                    val bytes = reply.body.toByteArray()
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 ${reply.code} Test\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${bytes.size}\r\nConnection: close\r\n${reply.extraHeaders}\r\n").toByteArray())
                        write(bytes)
                        flush()
                    }
                }
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
