package com.bangdream.pet.llm

import org.json.JSONObject
import org.json.JSONTokener

/** Decodes one logical reply before exposing any structured protocol text to the UI. */
internal class AssistantReplyDecoder(private val tags: Set<String>, private val segmented: Boolean) {
    private val plain = ActionTagParser(tags)
    private val raw = StringBuilder()

    fun consume(delta: String): String {
        return if (segmented) {
            raw.append(delta)
            ""
        } else plain.consume(delta)
    }

    fun finish(completed: Boolean = true): Result {
        if (!segmented) return plain.finish().let { Result(it.text, emptyList(), it.action) }
        if (!completed) return Result("", emptyList(), null)
        var value = raw.toString().trim()
        if (value.startsWith("```")) {
            val firstLine = value.indexOf('\n')
            if (firstLine < 0 || !value.endsWith("```")) throw IllegalArgumentException(ERROR_FORMAT)
            value = value.substring(firstLine + 1, value.length - 3).trim()
        }
        if (!value.startsWith("{") && Regex("\"messages\"\\s*:").containsMatchIn(value)) {
            throw IllegalArgumentException(ERROR_FORMAT)
        }
        val startsWithActionTag = Regex("^\\[[A-Za-z0-9_.]+]").containsMatchIn(value)
        val parts = if (value.startsWith("{") || (value.startsWith("[") && !startsWithActionTag)) {
            try {
                val tokener = JSONTokener(value)
                val root = tokener.nextValue() as? JSONObject ?: throw IllegalArgumentException(ERROR_FORMAT)
                if (tokener.nextClean() != '\u0000') throw IllegalArgumentException(ERROR_FORMAT)
                val array = root.getJSONArray("messages")
                (0 until array.length()).map { array.getJSONObject(it).getString("text").trim() }
            } catch (_: Exception) {
                throw IllegalArgumentException(ERROR_FORMAT)
            }
        } else listOf(value)
        var action: String? = null
        val cleaned = parts.mapNotNull { part ->
            val decoded = ActionTagParser(tags).apply { consume(part) }.finish()
            if (action == null) action = decoded.action
            decoded.text.takeIf(String::isNotBlank)
        }
        if (cleaned.isEmpty()) throw IllegalArgumentException(ERROR_FORMAT)
        val segments = if (cleaned.size > MAX_SEGMENTS) {
            cleaned.take(MAX_SEGMENTS - 1) + cleaned.drop(MAX_SEGMENTS - 1).joinToString("\n")
        } else cleaned
        return Result(segments.joinToString("\n"), segments, action)
    }

    data class Result(val text: String, val segments: List<String>, val action: String?)

    companion object {
        const val ERROR_FORMAT = "CHAT_REPLY_FORMAT_INVALID"
        const val MAX_SEGMENTS = 6
        fun systemPrompt(base: String, enabled: Boolean): String = if (!enabled) base else
            base + "\n\n【回复输出格式】本次最终回复只输出一个有效 JSON 对象，不要代码围栏或 JSON 外的文字。" +
                "格式：{\"messages\":[{\"text\":\"第一段\"},{\"text\":\"第二段\"}]}。" +
                "messages 按发送顺序包含 1 至 6 个 text 字符串。按自然聊天语义分成简短消息，允许只发一段，" +
                "不要为了分段重复或扩写内容。JSON 字符串里的换行、引号等必须正确转义。" +
                "角色动作标签可保留在 text 中，思考过程不要写入 messages。"
    }
}
