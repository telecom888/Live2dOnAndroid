package com.bangdream.pet.llm

/**
 * 聊天文本搜索与 token 估算工具。
 *
 * 搜索算法：
 * - 归一化（小写、全角转半角、空白折叠）后做子串匹配；
 * - 支持空格分隔的多关键词（AND 匹配，允许乱序）；
 * - 评分：命中次数、关键词长度、消息开头命中、命中位置越靠前得分越高；
 * - 返回按相关度排序的匹配消息，附带高亮范围（基于归一化文本）。
 */
object ChatTextSearch {

    data class MessageMatch(
        val messageId: String,
        val messageIndex: Int,
        val role: String,
        val content: String,
        val score: Int,
        val ranges: List<IntRange>,
    )

    /** 归一化：全角转半角、小写、零宽字符剔除。 */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            val c = when {
                code in 0xFF01..0xFF5E -> (code - 0xFEE0).toChar()
                ch == '\u3000' -> ' '
                code == 0x200B || code == 0x200C || code == 0x200D || code == 0xFEFF -> continue
                else -> ch
            }
            sb.append(c)
        }
        return sb.toString().lowercase()
    }

    fun searchMessages(messages: List<ChatMessage>, query: String): List<MessageMatch> {
        val normalizedQuery = normalize(query)
        val terms = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        val result = mutableListOf<MessageMatch>()
        for ((index, message) in messages.withIndex()) {
            val normalized = normalize(message.content)
            if (terms.any { !normalized.contains(it) }) continue
            val ranges = mutableListOf<IntRange>()
            var score = 0
            var searchFrom = 0
            for (term in terms) {
                var pos = normalized.indexOf(term, searchFrom)
                if (pos < 0) pos = normalized.indexOf(term)
                while (pos >= 0) {
                    ranges += pos until pos + term.length
                    score += 10 + term.length * 2
                    if (pos == 0) score += 20
                    pos = normalized.indexOf(term, pos + term.length)
                }
                if (searchFrom < normalized.length) searchFrom = normalized.length
            }
            val first = ranges.minOfOrNull { it.first } ?: 0
            score += ((normalized.length - first) * 10 / normalized.length.coerceAtLeast(1)).toInt()
            result.add(MessageMatch(message.id, index, message.role, message.content, score, ranges))
        }
        return result.sortedByDescending { it.score }
    }

    /** 在原文中定位查询词的高亮范围（大小写不敏感，多关键词 AND）。 */
    fun findHighlightRanges(content: String, query: String): List<IntRange> {
        val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        val lower = content.lowercase()
        val ranges = mutableListOf<IntRange>()
        for (term in terms) {
            var pos = lower.indexOf(term)
            while (pos >= 0) {
                ranges += pos until pos + term.length
                pos = lower.indexOf(term, pos + term.length)
            }
        }
        return ranges
    }

    /** 简易 token 估算：CJK/日文/韩文每字符约 1 token，其它每 4 字符约 1 token。 */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (isCjk(ch)) cjk++ else other++
        }
        return cjk + (other + 3) / 4
    }

    fun isCjk(ch: Char): Boolean {
        val cp = ch.code
        return cp in 0x4E00..0x9FFF ||
            cp in 0x3400..0x4DBF ||
            cp in 0x20000..0x2A6DF ||
            cp in 0x3040..0x30FF ||
            cp in 0xAC00..0xD7AF
    }
}
