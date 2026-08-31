package com.bangdream.pet.llm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRequestTest {
    @Test
    fun endpointIsNormalized() {
        assertEquals("https://api.example.com/v1/chat/completions", LlmSettings(baseUrl = "https://api.example.com/v1/").endpoint())
        assertEquals("http://192.168.1.2:11434/chat/completions", LlmSettings(baseUrl = "http://192.168.1.2:11434").endpoint())
        assertEquals(
            "https://api.example.com/chat/completions",
            LlmSettings(baseUrl = "https://api.example.com/chat/completions").endpoint(),
        )
    }

    @Test
    fun thinkingModesUseDeepSeekExtensionOnlyWhenExplicit() {
        val client = LlmChatClient()
        val base = LlmSettings(apiKey = "secret")
        val auto = client.buildRequestBody(base, JSONArray())
        val enabled = client.buildRequestBody(base.copy(thinkingMode = ThinkingMode.Enabled), JSONArray())
        val disabled = client.buildRequestBody(base.copy(thinkingMode = ThinkingMode.Disabled), JSONArray())
        assertFalse(auto.has("thinking"))
        assertEquals("enabled", enabled.getJSONObject("thinking").getString("type"))
        assertEquals("disabled", disabled.getJSONObject("thinking").getString("type"))
        assertFalse(auto.toString().contains("secret"))
    }

    @Test
    fun requestKeepsAllTextHistory() {
        val messages = (0 until 45).map { index -> ChatMessage("$index", "user", "m$index", index.toLong()) }
        val body = LlmChatClient().buildRequestBody(LlmSettings(), LlmChatClient().messagesToJsonArray("persona", messages))
        assertEquals(46, body.getJSONArray("messages").length())
        assertEquals("m0", body.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals("m44", body.getJSONArray("messages").getJSONObject(45).getString("content"))
        assertTrue(body.getBoolean("stream"))
    }

    @Test
    fun imageHistoryKeepsAllImageMessages() {
        // 图片历史不裁剪：所有带图消息都以图片内容块重发
        val messages = (0 until 5).map { index ->
            ChatMessage("img$index", "user", "pic$index", index.toLong(), images = listOf("data:image/png;base64,abc$index"))
        }
        val request = LlmChatClient().messagesToJsonArray("persona", messages)
        assertEquals(6, request.length())
        val retainedAsContentBlock = (1 until 6).count { index ->
            request.optJSONObject(index)?.opt("content") is JSONArray
        }
        assertEquals(5, retainedAsContentBlock)
        // 每条带图消息：1 个 image_url 块 + 1 个 text 块，文本仍保留
        for (index in 1 until 6) {
            val content = request.getJSONObject(index).getJSONArray("content")
            assertEquals(2, content.length())
            assertEquals("image_url", content.getJSONObject(0).getString("type"))
            assertEquals("text", content.getJSONObject(1).getString("type"))
            assertTrue(content.getJSONObject(1).getString("text").contains("pic${index - 1}"))
        }
    }
}
