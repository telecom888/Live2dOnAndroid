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
    fun imageHistoryIsCappedToRecentMessages() {
        // 带图历史只保留最近 MAX_IMAGE_HISTORY_MESSAGES 条；更早的图片以文字标记代替
        val messages = (0 until 5).map { index ->
            ChatMessage("img$index", "user", "pic$index", index.toLong(), images = listOf("data:image/png;base64,abc$index"))
        }
        val request = LlmChatClient().messagesToJsonArray("persona", messages)
        assertEquals(6, request.length())
        val retainedAsContentBlock = (1 until 6).count { index ->
            request.optJSONObject(index)?.opt("content") is JSONArray
        }
        assertEquals(LlmChatClient.MAX_IMAGE_HISTORY_MESSAGES, retainedAsContentBlock)
        // 更早的图片被文字标记替换，且文本仍在
        val omitted = request.getJSONObject(1).getString("content")
        assertTrue(omitted.contains("pic0"))
        assertTrue(omitted.contains("省略"))
        // 最近一条带图消息：1 个 image_url 块 + 1 个 text 块
        val lastContent = request.getJSONObject(5).getJSONArray("content")
        assertEquals(2, lastContent.length())
        assertEquals("image_url", lastContent.getJSONObject(0).getString("type"))
        assertEquals("text", lastContent.getJSONObject(1).getString("type"))
    }
}
