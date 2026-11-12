package com.bangdream.pet.llm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VertexAiProtocolTest {
    private val base =
        "https://aiplatform.googleapis.com/v1/projects/demo-project/locations/global/publishers/google/models"

    private fun settings(url: String = base) = LlmSettings(
        baseUrl = url, apiKey = "fake-key", model = "gemini-3.8-flash", protocol = LlmProtocol.VERTEX_AI,
    )

    @Test
    fun projectScopedStreamingAndUnaryUrls() {
        val s = settings()
        assertEquals("$base/gemini-3.8-flash:streamGenerateContent?alt=sse", s.endpoint())
        assertEquals("$base/gemini-3.8-flash:generateContent", s.endpoint(stream = false))
        assertEquals(base, s.vertexAiModelsBaseUrl())
        assertEquals(base, settings(base.removeSuffix("/models")).vertexAiModelsBaseUrl())
        assertEquals("$base/gemini-3.8-flash:generateContent",
            settings(base.removeSuffix("/models")).endpoint(stream = false))
        assertEquals("gemini-3.8-flash", modelId(LlmProtocol.VERTEX_AI, "models/gemini-3.8-flash"))
    }

    @Test
    fun rejectsPlaceholderAndWrongPaths() {
        assertNull(settings(base.replace("demo-project", "PROJECT_ID")).vertexAiModelsBaseUrl())
        assertNull(settings("https://generativelanguage.googleapis.com").vertexAiModelsBaseUrl())
        assertNull(settings("https://aiplatform.googleapis.com/v1beta/models").vertexAiModelsBaseUrl())
        assertNull(settings("https://aiplatform.googleapis.com/v1/projects/demo/locations/global/publishers/google/models?key=abc").vertexAiModelsBaseUrl())
    }

    @Test
    fun reusesGeminiNativeRequestAndResponse() {
        val s = settings()
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", "Be brief"))
            .put(JSONObject().put("role", "user").put("content", "Hi"))
            .put(JSONObject().put("role", "assistant").put("content", "Hello"))
        val request = buildNativeRequest(s, messages, stream = true)
        assertEquals("Be brief", request.getJSONObject("systemInstruction")
            .getJSONArray("parts").getJSONObject(0).getString("text"))
        assertEquals("user", request.getJSONArray("contents").getJSONObject(0).getString("role"))
        assertEquals("model", request.getJSONArray("contents").getJSONObject(1).getString("role"))
        assertEquals("Hi", request.getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text"))
        assertFalse(request.has("messages"))

        val response = JSONObject("""{"candidates":[{"content":{"parts":[{"text":"Works"}]},"finishReason":"STOP"}]}""")
        assertEquals(listOf(LlmStreamEvent.Content("Works")), parseNativeResponse(s, response, stream = false))
    }

    @Test
    fun modelCatalogUsesOAuthAndExistingGoogleRemainsUnchanged() {
        assertEquals("https://aiplatform.googleapis.com/v1beta1/publishers/google/models",
            LlmProvider("vertex", connection = settings()).catalogUrl().toString())
        assertNull(LlmProvider("invalid", connection = settings("https://example.com")).catalogUrl())
        val gemini = LlmSettings(baseUrl = "https://generativelanguage.googleapis.com",
            apiKey = "fake-key", model = "gemini-test", protocol = LlmProtocol.GOOGLE)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent",
            gemini.endpoint(stream = false))
        assertTrue(LlmProtocol.VERTEX_AI.usesGeminiSchema)
        assertFalse(LlmProtocol.OPENAI.usesGeminiSchema)
    }

    @Test
    fun parsesOnlyGeminiChatModelsFromModelGarden() {
        fun model(name: String) = JSONObject().put("name", name)
        val result = vertexPublisherChatModel(
            model("publishers/google/models/gemini-3.8-flash").put("displayName", "Gemini Flash")
        )
        assertEquals("gemini-3.8-flash", result?.model?.id)
        assertEquals("Gemini Flash", result?.model?.name)
        assertNull(vertexPublisherChatModel(model("publishers/google/models/textembedding-gecko")))
        assertNull(vertexPublisherChatModel(model("publishers/google/models/gemini-3.1-flash-image")))
        assertNull(vertexPublisherChatModel(model("publishers/anthropic/models/gemini-3.8-flash")))
    }

    @Test
    fun persistsDistinctProtocolId() {
        val provider = LlmProvider("vertex", "Vertex AI", settings(), models = listOf(LlmModel("gemini-3.8-flash")))
        val saved = LlmProviderSettings(providers = listOf(provider)).toJson()
        val restored = LlmProviderSettings.fromJson(saved)
        assertEquals(LlmProtocol.VERTEX_AI, restored.providers.single().connection.protocol)
    }
}
