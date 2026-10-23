package com.bangdream.pet.llm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeLlmTest {
    private val history = JSONArray().put(JSONObject().put("role", "system").put("content", "persona"))
        .put(JSONObject().put("role", "user").put("content", "question"))

    @Test
    fun explicitProtocolsUseOpaqueAliasesAndNativePaths() {
        val base = LlmSettings(baseUrl = "https://gateway.example/v1/", model = "public-alias")
        assertEquals("https://gateway.example/v1/messages", base.copy(protocol = LlmProtocol.ANTHROPIC).endpoint())
        assertEquals("https://gateway.example/v1beta/models/public-alias:streamGenerateContent?alt=sse", base.copy(protocol = LlmProtocol.GOOGLE).endpoint())
        assertEquals("https://gateway.example/v1beta/models/public-alias:generateContent", base.copy(protocol = LlmProtocol.GOOGLE).endpoint(false))
        assertEquals("https://gateway.example/v1beta/models/public-alias:generateContent", base.copy(protocol = LlmProtocol.GOOGLE, baseUrl = "https://gateway.example/v1beta").endpoint(false))
        assertEquals(LlmProtocol.OPENAI, LlmProtocol.fromValue(null))
        assertEquals(LlmProtocol.OPENAI, LlmProtocol.fromValue("unknown"))
    }

    @Test
    fun googleRequestsSummariesWithoutAssumingModelFamilyOrReasoningLevel() {
        val settings = LlmSettings(protocol = LlmProtocol.GOOGLE, model = "public-alias")
        for (mode in ThinkingMode.entries) {
            val body = buildNativeRequest(settings.copy(thinkingMode = mode), history, true)
            val config = body.getJSONObject("generationConfig")
            assertFalse(body.has("thinking"))
            assertFalse(body.has("reasoning_effort"))
            assertFalse(body.has("model")) // Model is carried verbatim in the URL.
            assertEquals("persona", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
            if (mode == ThinkingMode.Auto) assertFalse(config.has("thinkingConfig"))
            else {
                val thinking = config.getJSONObject("thinkingConfig")
                assertEquals(mode == ThinkingMode.Enabled, thinking.getBoolean("includeThoughts"))
                assertEquals(1, thinking.length())
            }
        }
    }

    @Test
    fun anthropicSupportsExplicitAdaptiveAndLegacyThinkingWithoutModelDetection() {
        val settings = LlmSettings(protocol = LlmProtocol.ANTHROPIC, model = "public-alias", thinkingMode = ThinkingMode.Enabled)
        val adaptive = buildNativeRequest(settings, history, true)
        assertEquals("public-alias", adaptive.getString("model"))
        assertEquals("persona", adaptive.getString("system"))
        assertEquals(1, adaptive.getJSONArray("messages").length())
        assertEquals("adaptive", adaptive.getJSONObject("thinking").getString("type"))
        assertEquals("summarized", adaptive.getJSONObject("thinking").getString("display"))
        assertFalse(adaptive.has("temperature"))
        val manual = buildNativeRequest(settings.copy(anthropicAdaptiveThinking = false), history, false)
        assertFalse(manual.getBoolean("stream"))
        assertTrue(manual.getInt("max_tokens") > manual.getJSONObject("thinking").getInt("budget_tokens"))
        assertEquals("enabled", manual.getJSONObject("thinking").getString("type"))
    }

    @Test
    fun nativeHistoryPreservesImagesAndAssistantRoles() {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", JSONArray()
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/png;base64,YWJj")))
            .put(JSONObject().put("type", "text").put("text", "describe"))))
            .put(JSONObject().put("role", "assistant").put("content", "answer"))
        val google = buildNativeRequest(LlmSettings(protocol = LlmProtocol.GOOGLE), messages, true).getJSONArray("contents")
        assertEquals("YWJj", google.getJSONObject(0).getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData").getString("data"))
        assertEquals("model", google.getJSONObject(1).getString("role"))
        val anthropic = buildNativeRequest(LlmSettings(protocol = LlmProtocol.ANTHROPIC), messages, true).getJSONArray("messages")
        assertEquals("image/png", anthropic.getJSONObject(0).getJSONArray("content").getJSONObject(0).getJSONObject("source").getString("media_type"))
        assertEquals("assistant", anthropic.getJSONObject(1).getString("role"))
    }

    @Test
    fun googleSeparatesThoughtsFromTextAndIgnoresSignatures() {
        val payload = JSONObject("""{"candidates":[{"content":{"parts":[{"thought":true,"text":"summary"},{"thoughtSignature":"opaque"},{"text":"answer"}]},"finishReason":"STOP"}]}""")
        assertEquals(listOf(LlmStreamEvent.Reasoning("summary"), LlmStreamEvent.Content("answer")),
            parseNativeResponse(LlmSettings(protocol = LlmProtocol.GOOGLE), payload, true))
    }

    @Test
    fun anthropicSeparatesThinkingAndDoesNotDisplayOpaqueBlocks() {
        val settings = LlmSettings(protocol = LlmProtocol.ANTHROPIC)
        val delta = JSONObject("""{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"summary"}}""")
        assertEquals(listOf(LlmStreamEvent.Reasoning("summary")), parseNativeResponse(settings, delta, true))
        val signature = JSONObject("""{"type":"content_block_delta","delta":{"type":"signature_delta","signature":"opaque"}}""")
        assertTrue(parseNativeResponse(settings, signature, true).isEmpty())
        val nonstream = JSONObject("""{"content":[{"type":"thinking","thinking":"summary"},{"type":"redacted_thinking","data":"opaque"},{"type":"text","text":"answer"}]}""")
        assertEquals(listOf(LlmStreamEvent.Reasoning("summary"), LlmStreamEvent.Content("answer")), parseNativeResponse(settings, nonstream, false))
    }

    @Test
    fun nativeBlockAndProviderErrorsAreNotSwallowed() {
        val settings = LlmSettings(protocol = LlmProtocol.GOOGLE, apiKey = "secret")
        assertEquals("LLM_CONTENT_FILTERED", runCatching {
            parseNativeResponse(settings, JSONObject("""{"promptFeedback":{"blockReason":"SAFETY"}}"""), true)
        }.exceptionOrNull()?.message)
        assertEquals("denied ***", runCatching {
            parseNativeResponse(settings, JSONObject("""{"error":{"message":"denied secret"}}"""), true)
        }.exceptionOrNull()?.message)
    }
}
