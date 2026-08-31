package com.bangdream.pet.line

import android.content.Context
import com.bangdream.pet.data.DataRepository
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.llm.CharacterPromptRepository
import com.bangdream.pet.llm.LlmChatClient
import com.bangdream.pet.llm.LlmSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Line 多角色仲裁器（旁观者模式）。
 * - 每个角色独立上下文（只看自己已读的消息）；
 * - 决策回合：一次发多条（--- 分隔）/ 保持沉默 [silent] / 结束 [scene_end]；
 * - 已读协议：发消息后对每个接收方先问“是否阅读”，返回后再决定是否注入上下文；
 * - 时间注入：每条消息带时间戳，提示当前时间。
 */
class LineOrchestrator(private val context: Context) {
    private val settings = LlmSettings.load(context)
    private val client = LlmChatClient()
    private val prompts = CharacterPromptRepository(context)
    private val roleNames: Map<String, String> by lazy {
        runCatching {
            DataRepository(context).load().characters.mapValues { (_, info) -> info.display }
        }.getOrDefault(emptyMap())
    }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    suspend fun runConversation(
        conversation: LineConversation,
        onUpdated: suspend (LineConversation) -> Unit,
    ): LineConversation {
        var conv = conversation
        if (conv.status != LineConversationStatus.ACTIVE || conv.memberRoleIds.isEmpty()) return conv

        val contexts = mutableMapOf<String, MutableList<LineMessage>>()
        conv.memberRoleIds.forEach { rid ->
            contexts[rid] = conv.messages
                .filter { it.fromRoleId == rid || rid in it.readBy }
                .toMutableList()
        }

        var silentStreak = 0
        var round = 0
        while (conv.status == LineConversationStatus.ACTIVE && round < MAX_ROUNDS) {
            round++
            var spokeInRound = false
            for (rid in conv.memberRoleIds) {
                if (conv.status != LineConversationStatus.ACTIVE) break
                if (silentStreak >= SILENT_LIMIT) {
                    conv = conv.copy(status = LineConversationStatus.ENDED)
                    onUpdated(conv)
                    return conv
                }
                val decision = decide(rid, conv, contexts[rid].orEmpty())
                when (decision.action) {
                    Decision.Action.SILENT -> {
                        silentStreak++
                        continue
                    }
                    Decision.Action.END -> {
                        conv = conv.copy(status = LineConversationStatus.ENDED)
                        onUpdated(conv)
                        return conv
                    }
                    Decision.Action.REPLY -> {
                        silentStreak = 0
                        for (text in decision.messages) {
                            val trimmed = text.trim()
                            if (trimmed.isEmpty()) continue
                            val now = System.currentTimeMillis()
                            val message = LineMessage(
                                id = LineSceneRepository.newId(),
                                fromRoleId = rid,
                                content = trimmed,
                                timestamp = now,
                            )
                            conv = conv.copy(messages = conv.messages + message, updatedAt = now)
                            contexts[rid]?.add(message)
                            for (other in conv.memberRoleIds) {
                                if (other == rid) continue
                                if (askRead(other, rid, trimmed, conv)) {
                                    contexts[other]?.add(message)
                                    conv = conv.copy(
                                        messages = conv.messages.map {
                                            if (it.id == message.id) it.copy(readBy = it.readBy + other) else it
                                        },
                                    )
                                }
                            }
                            spokeInRound = true
                            onUpdated(conv)
                            delay(Random.nextLong(500L, 1400L))
                        }
                    }
                }
            }
            if (!spokeInRound) break
        }
        if (conv.status == LineConversationStatus.ACTIVE) {
            conv = conv.copy(status = LineConversationStatus.ENDED)
            onUpdated(conv)
        }
        return conv
    }

    private suspend fun decide(
        roleId: String,
        conv: LineConversation,
        visible: List<LineMessage>,
    ): Decision {
        val chatType = if (conv.memberRoleIds.size > 2) "群聊" else "一对一"
        val system = buildString {
            append(persona(roleId))
            appendLine("现在是 ${timeFormat.format(Date())}，你在一个$chatType（主题：${conv.topic}）里。请用自然的中文说话。")
            if (visible.isNotEmpty()) {
                appendLine("你已看到的消息（按时间顺序）：")
                visible.forEach { m ->
                    appendLine("[${timeFormat.format(Date(m.timestamp))}] ${roleNames[m.fromRoleId] ?: m.fromRoleId}：${m.content}")
                }
            } else {
                appendLine("（你还没有看到任何消息）")
            }
        }
        val user = """接下来你想说什么？直接输出你要发出的消息内容。规则：
1) 可以一次发多条，用一行三个横线 --- 分隔多条。
2) 如果这轮不想说话，直接输出 [silent]。
3) 如果觉得这个对话可以结束，在你的消息最后加 [scene_end]。
不要解释，直接输出消息内容："""
        val response = client.complete(settings, listOf("system" to system, "user" to user))
        if (response.isBlank()) return Decision.silent()
        val lower = response.lowercase()
        if (lower.contains("[silent]")) return Decision.silent()
        val end = lower.contains("[scene_end]")
        val cleaned = response.replace("[scene_end]", "").replace("[silent]", "")
        val parts = cleaned.split("---").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty() && !end) return Decision.silent()
        return Decision.reply(parts, end)
    }

    private suspend fun askRead(readerId: String, senderId: String, content: String, conv: LineConversation): Boolean {
        val prompt = """你是${roleNames[readerId] ?: readerId}。主题「${conv.topic}」下，
${roleNames[senderId] ?: senderId}给你发来一条新消息：
「$content」
你是否阅读这条消息？只回答 是 或 否。"""
        val response = client.complete(settings, listOf("system" to persona(readerId), "user" to prompt))
        val lower = response.lowercase()
        return lower.contains("是") || lower.contains("true") || lower.contains("read")
    }

    private fun persona(roleId: String): String {
        val name = roleNames[roleId] ?: roleId
        val model = ModelChoice(
            characterId = roleId,
            characterName = name,
            costumeId = "",
            costumeName = "",
            modelAssetPath = "",
        )
        return runCatching { prompts.buildSystemPrompt(model).text }
            .getOrElse { "你是$name。" }
    }

    private data class Decision(
        val action: Action,
        val messages: List<String> = emptyList(),
    ) {
        enum class Action { REPLY, SILENT, END }
        companion object {
            fun silent() = Decision(Action.SILENT)
            fun reply(messages: List<String>, end: Boolean) =
                Decision(if (end) Action.END else Action.REPLY, messages)
        }
    }

    companion object {
        private const val MAX_ROUNDS = 30
        private const val SILENT_LIMIT = 3
    }
}
