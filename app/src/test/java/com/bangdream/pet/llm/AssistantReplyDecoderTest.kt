package com.bangdream.pet.llm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AssistantReplyDecoderTest {
    private fun reply(vararg parts: String) = JSONObject().put("messages",
        JSONArray(parts.map { JSONObject().put("text", it) })).toString()

    @Test fun buffersChunksAndDecodesEscapesBeforeActionTags() {
        val parser = AssistantReplyDecoder(setOf("smile", "sad"), true)
        val json = reply("[smile]早。", "你说\"你好\"了吗？\n我记得。", "[sad]第三段")
        json.chunked(3).forEach { assertEquals("", parser.consume(it)) }
        val result = parser.finish()
        assertEquals(listOf("早。", "你说\"你好\"了吗？\n我记得。", "第三段"), result.segments)
        assertEquals(result.segments.joinToString("\n"), result.text)
        assertEquals("smile", result.action)
    }

    @Test fun supportsFencesAndPlainTextWithoutSplittingPunctuation() {
        val fenced = AssistantReplyDecoder(emptySet(), true)
        fenced.consume("```json\n" + reply("一", "二") + "\n```")
        assertEquals(listOf("一", "二"), fenced.finish().segments)
        val plain = AssistantReplyDecoder(setOf("smile"), true)
        plain.consume("[smile]普通回复。保持完整！")
        assertEquals(listOf("普通回复。保持完整！"), plain.finish().segments)
    }

    @Test fun rejectsBrokenOrEmptyProtocolRatherThanDisplayingJson() {
        listOf("{\"messages\":[", "{\"messages\":[]}", "{\"messages\":[{\"text\":3}]}",
            "{\"wrong\":1}", reply("ok") + " extra", "回复如下：" + reply("ok"), "[smile]" + reply("ok"), "").forEach { raw ->
            val parser = AssistantReplyDecoder(emptySet(), true)
            parser.consume(raw)
            try { parser.finish(); fail("Should reject: $raw") }
            catch (error: IllegalArgumentException) { assertEquals(AssistantReplyDecoder.ERROR_FORMAT, error.message) }
        }
    }

    @Test fun mergesOverflowWithoutLosingContentAndDropsEmptyParts() {
        val parser = AssistantReplyDecoder(emptySet(), true)
        parser.consume(reply("", *Array(8) { "段${it + 1}" }))
        val result = parser.finish()
        assertEquals(6, result.segments.size)
        assertEquals("段6\n段7\n段8", result.segments.last())
        assertEquals((1..8).joinToString("\n") { "段$it" }, result.text)
    }

    @Test fun cancellingEvenValidStructuredDataDoesNotSaveAnAssistantReply() {
        val parser = AssistantReplyDecoder(emptySet(), true)
        parser.consume(reply("未完成的请求"))
        assertEquals("", parser.finish(completed = false).text)
        val legacy = AssistantReplyDecoder(setOf("smile"), false)
        assertEquals("可保留部分文本", legacy.consume("可保留部分文本[sm"))
        assertEquals("可保留部分文本", legacy.finish(completed = false).text)
        assertTrue(legacy.finish().segments.isEmpty())
    }
}
