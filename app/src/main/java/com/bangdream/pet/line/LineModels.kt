package com.bangdream.pet.line

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class LineConversationType { ONE_TO_ONE, GROUP }
enum class LineConversationStatus { ACTIVE, ENDED }

data class LineMessage(
    val id: String,
    val fromRoleId: String,
    val content: String,
    val timestamp: Long,
    val readBy: List<String> = emptyList(),
)

data class LineConversation(
    val id: String,
    val type: LineConversationType,
    val title: String,
    val topic: String,
    val memberRoleIds: List<String>,
    val status: LineConversationStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<LineMessage> = emptyList(),
) {
    val preview: String get() = messages.lastOrNull()?.content.orEmpty()

    fun readCount(message: LineMessage): Int =
        message.readBy.count { it in memberRoleIds && it != message.fromRoleId }

    fun membersExcluding(roleId: String): List<String> = memberRoleIds.filter { it != roleId }
}

class LineSceneRepository(private val context: Context) {
    private val root = File(context.filesDir, "line_scenes")

    @Synchronized
    fun list(): List<LineConversation> {
        root.mkdirs()
        return root.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.mapNotNull { read(it) }
            ?.sortedByDescending { it.updatedAt }
            ?.toList()
            .orEmpty()
    }

    fun listForRole(roleId: String): List<LineConversation> =
        list().filter { roleId in it.memberRoleIds }

    @Synchronized
    fun get(id: String): LineConversation? {
        val file = File(root, "$id.json")
        return if (file.isFile) read(file) else null
    }

    @Synchronized
    fun save(conversation: LineConversation): LineConversation {
        root.mkdirs()
        File(root, "${conversation.id}.json").writeText(toJson(conversation).toString())
        return conversation
    }

    @Synchronized
    fun delete(id: String) {
        File(root, "$id.json").delete()
    }

    private fun toJson(c: LineConversation): JSONObject = JSONObject()
        .put("id", c.id)
        .put("type", c.type.name)
        .put("title", c.title)
        .put("topic", c.topic)
        .put(
            "memberRoleIds",
            JSONArray().apply { c.memberRoleIds.forEach { put(it) } },
        )
        .put("status", c.status.name)
        .put("createdAt", c.createdAt)
        .put("updatedAt", c.updatedAt)
        .put(
            "messages",
            JSONArray().apply {
                c.messages.forEach { m ->
                    put(
                        JSONObject()
                            .put("id", m.id)
                            .put("from", m.fromRoleId)
                            .put("content", m.content)
                            .put("timestamp", m.timestamp)
                            .put("readBy", JSONArray().apply { m.readBy.forEach { put(it) } }),
                    )
                }
            },
        )

    private fun read(file: File): LineConversation? = runCatching {
        val j = JSONObject(file.readText())
        LineConversation(
            id = j.getString("id"),
            type = runCatching { LineConversationType.valueOf(j.getString("type")) }
                .getOrDefault(LineConversationType.ONE_TO_ONE),
            title = j.optString("title"),
            topic = j.optString("topic"),
            memberRoleIds = buildList {
                val a = j.optJSONArray("memberRoleIds") ?: JSONArray()
                for (i in 0 until a.length()) add(a.getString(i))
            },
            status = runCatching { LineConversationStatus.valueOf(j.getString("status")) }
                .getOrDefault(LineConversationStatus.ACTIVE),
            createdAt = j.optLong("createdAt", 0L),
            updatedAt = j.optLong("updatedAt", 0L),
            messages = buildList {
                val a = j.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until a.length()) {
                    val m = a.optJSONObject(i) ?: continue
                    add(
                        LineMessage(
                            id = m.optString("id"),
                            fromRoleId = m.optString("from"),
                            content = m.optString("content"),
                            timestamp = m.optLong("timestamp", 0L),
                            readBy = buildList {
                                val r = m.optJSONArray("readBy") ?: JSONArray()
                                for (k in 0 until r.length()) add(r.getString(k))
                            },
                        ),
                    )
                }
            },
        )
    }.getOrNull()

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
