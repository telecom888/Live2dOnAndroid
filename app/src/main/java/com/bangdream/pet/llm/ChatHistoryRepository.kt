package com.bangdream.pet.llm

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class ChatHistoryRepository internal constructor(
    private val root: File,
) {
    constructor(context: Context) : this(File(context.filesDir, "chat_history"))

    /**
     * 会话摘要缓存：key 为文件绝对路径，命中条件为「文件未被修改」（mtime + 大小一致）。
     * 之前每次发消息都会重新解析该角色目录下的全部会话 JSON（含 base64 图片），
     * 会话越多越卡；这里只在文件真正变化时才重新解析。
     */
    private val summaryCache = object : LinkedHashMap<String, CachedSummary>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSummary>): Boolean =
            size > MAX_CACHED_SUMMARIES
    }

    private class CachedSummary(
        val lastModified: Long,
        val length: Long,
        val summary: ChatConversationSummary,
    )

    @Synchronized
    fun loadSnapshot(characterId: String): ChatHistorySnapshot {
        val conversations = listConversations(characterId)
        val requestedId = readActiveIds()[characterId]
        val selectedSummary = conversations.firstOrNull { it.id == requestedId } ?: conversations.firstOrNull()
        val selected = selectedSummary?.let { loadConversation(characterId, it.id) }
        if (selected?.id != requestedId) {
            runCatching { setActiveConversation(characterId, selected?.id) }
        }
        return ChatHistorySnapshot(conversations = conversations, activeConversation = selected)
    }

    @Synchronized
    fun listConversations(characterId: String): List<ChatConversationSummary> {
        ensureLegacyMigrated(characterId)
        val directory = characterDirectory(characterId)
        return directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.mapNotNull { file -> cachedSummary(file, characterId) }
            ?.sortedWith(
                compareByDescending<ChatConversationSummary> { it.updatedAt }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id },
            )
            ?.toList()
            .orEmpty()
    }

    @Synchronized
    fun loadConversation(characterId: String, conversationId: String): ChatConversation? {
        ensureLegacyMigrated(characterId)
        val file = conversationFile(characterId, conversationId) ?: return null
        return readConversation(file, characterId)
    }

    @Synchronized
    fun saveConversation(conversation: ChatConversation): ChatConversation {
        val normalized = conversation.copy(
            title = conversation.title.ifBlank { titleFromMessages(conversation.messages) },
            messages = conversation.messages,
        )
        val file = conversationFile(normalized.characterId, normalized.id)
            ?: throw IllegalArgumentException("Invalid conversation id")
        val payload = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("id", normalized.id)
            .put("characterId", normalized.characterId)
            .put("title", normalized.title)
            .put("createdAt", normalized.createdAt)
            .put("updatedAt", normalized.updatedAt)
            .put("messages", messagesToJson(normalized.messages))
        writeAtomically(file, payload.toString())
        putSummary(file, normalized.toSummary())
        return normalized
    }

    @Synchronized
    fun setActiveConversation(characterId: String, conversationId: String?) {
        val activeIds = readActiveIds().toMutableMap()
        if (conversationId == null) activeIds.remove(characterId) else activeIds[characterId] = conversationId
        val payload = JSONObject()
        activeIds.forEach { (id, activeConversationId) -> payload.put(id, activeConversationId) }
        writeAtomically(activeStateFile(), payload.toString())
    }

    @Synchronized
    fun renameConversation(characterId: String, conversationId: String, title: String): Boolean {
        val file = conversationFile(characterId, conversationId) ?: return false
        val conversation = readConversation(file, characterId) ?: return false
        saveConversation(
            conversation.copy(
                title = title.trim().takeIf(String::isNotBlank) ?: conversation.title,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    @Synchronized
    fun deleteConversation(characterId: String, conversationId: String): Boolean {
        val file = conversationFile(characterId, conversationId) ?: return false
        val deleted = !file.exists() || file.delete()
        if (deleted) summaryCache.remove(file.absolutePath)
        if (deleted && readActiveIds()[characterId] == conversationId) {
            setActiveConversation(characterId, null)
        }
        return deleted
    }

    @Synchronized
    fun clearCharacter(characterId: String) {
        val legacy = legacyFile(characterId)
        check(!legacy.exists() || legacy.delete()) { "Cannot delete legacy chat history" }
        val directory = characterDirectory(characterId)
        check(!directory.exists() || directory.deleteRecursively()) { "Cannot delete character chat history" }
        summaryCache.keys.removeAll { it.startsWith(directory.absolutePath) }
        setActiveConversation(characterId, null)
    }

    @Synchronized
    fun clearAll() {
        check(!root.exists() || root.deleteRecursively()) { "Cannot delete chat history" }
        summaryCache.clear()
    }

    /** 命中缓存则直接返回摘要，否则解析文件并写入缓存。 */
    private fun cachedSummary(file: File, characterId: String): ChatConversationSummary? {
        val path = file.absolutePath
        val lastModified = file.lastModified()
        val length = file.length()
        summaryCache[path]?.let { cached ->
            if (cached.lastModified == lastModified && cached.length == length) return cached.summary
        }
        val summary = readConversation(file, characterId)?.toSummary()
        if (summary == null) {
            summaryCache.remove(path)
            return null
        }
        summaryCache[path] = CachedSummary(lastModified, length, summary)
        return summary
    }

    private fun putSummary(file: File, summary: ChatConversationSummary) {
        summaryCache[file.absolutePath] = CachedSummary(file.lastModified(), file.length(), summary)
    }

    private fun ensureLegacyMigrated(characterId: String) {
        val legacy = legacyFile(characterId)
        if (!legacy.isFile) return
        val messages = readLegacyMessages(legacy, characterId) ?: return
        if (messages.isEmpty()) {
            legacy.delete()
            return
        }

        val migratedFile = conversationFile(characterId, LEGACY_CONVERSATION_ID) ?: return
        if (readConversation(migratedFile, characterId) == null) {
            val validTimestamps = messages.map(ChatMessage::timestamp).filter { it > 0L }
            val fallbackTimestamp = legacy.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
            val conversation = ChatConversation(
                id = LEGACY_CONVERSATION_ID,
                characterId = characterId,
                title = titleFromMessages(messages),
                createdAt = validTimestamps.firstOrNull() ?: fallbackTimestamp,
                updatedAt = validTimestamps.lastOrNull() ?: fallbackTimestamp,
                messages = messages,
            )
            saveConversation(conversation)
        }
        if (readActiveIds()[characterId] == null) {
            runCatching { setActiveConversation(characterId, LEGACY_CONVERSATION_ID) }
        }
        if (migratedFile.isFile && readConversation(migratedFile, characterId) != null) legacy.delete()
    }

    private fun readLegacyMessages(file: File, characterId: String): List<ChatMessage>? = runCatching {
        val array = JSONArray(file.readText())
        parseMessages(array, characterId)
    }.getOrNull()

    private fun readConversation(file: File, expectedCharacterId: String): ChatConversation? = runCatching {
        if (!file.isFile) return@runCatching null
        val payload = JSONObject(file.readText())
        val characterId = payload.optString("characterId", expectedCharacterId)
        if (characterId != expectedCharacterId) return@runCatching null
        val id = payload.optString("id", file.nameWithoutExtension)
        if (!VALID_CONVERSATION_ID.matches(id) || id != file.nameWithoutExtension) return@runCatching null
        val messages = parseMessages(payload.optJSONArray("messages") ?: JSONArray(), id)
        val fallbackTimestamp = file.lastModified().takeIf { it > 0L } ?: 0L
        val createdAt = payload.optLong("createdAt", fallbackTimestamp)
        val updatedAt = payload.optLong("updatedAt", createdAt)
        ChatConversation(
            id = id,
            characterId = characterId,
            title = payload.optString("title").ifBlank { titleFromMessages(messages) },
            createdAt = createdAt,
            updatedAt = updatedAt,
            messages = messages,
        )
    }.getOrNull()

    private fun parseMessages(array: JSONArray, fallbackPrefix: String): List<ChatMessage> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                ChatMessage(
                    id = item.optString("id", "$fallbackPrefix-$index"),
                    role = item.optString("role", "user"),
                    content = item.optString("content"),
                    reasoning = item.optString("reasoning").takeIf(String::isNotEmpty),
                    timestamp = item.optLong("timestamp", 0L),
                    images = item.optJSONArray("images")
                        ?.let { array -> buildList { for (i in 0 until array.length()) add(array.optString(i)) } }
                        .orEmpty(),
                    read = item.optBoolean("read", false),
                ),
            )
        }
    }

    private fun messagesToJson(messages: List<ChatMessage>): JSONArray = JSONArray().apply {
        messages.forEach { message ->
            put(
                JSONObject()
                    .put("id", message.id)
                    .put("role", message.role)
                    .put("content", message.content)
                    .put("reasoning", message.reasoning)
                    .put("timestamp", message.timestamp)
                    .put("images", JSONArray().apply { message.images.forEach { put(it) } })
                    .put("read", message.read),
            )
        }
    }

    private fun ChatConversation.toSummary(): ChatConversationSummary = ChatConversationSummary(
        id = id,
        characterId = characterId,
        title = title.ifBlank { titleFromMessages(messages) },
        preview = messages.lastOrNull { it.content.isNotBlank() }
            ?.content
            ?.let { compactAndTruncate(it, PREVIEW_MAX_CODE_POINTS) }
            .orEmpty(),
        searchableContent = messages.joinToString("\n") { it.content }.trim(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        messageCount = messages.size,
    )

    private fun readActiveIds(): Map<String, String> = runCatching {
        val file = activeStateFile()
        if (!file.isFile) return@runCatching emptyMap()
        val payload = JSONObject(file.readText())
        buildMap {
            val keys = payload.keys()
            while (keys.hasNext()) {
                val characterId = keys.next()
                payload.optString(characterId).takeIf(String::isNotBlank)?.let { put(characterId, it) }
            }
        }
    }.getOrDefault(emptyMap())

    private fun writeAtomically(file: File, text: String) {
        val directory = file.parentFile ?: throw IllegalStateException("History file has no parent")
        check(directory.exists() || directory.mkdirs()) { "Cannot create chat history directory" }
        val temp = File(directory, "${file.name}.${UUID.randomUUID()}.tmp")
        try {
            temp.writeText(text)
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    private fun legacyFile(characterId: String): File = File(root, "${safeCharacterId(characterId)}.json")

    private fun characterDirectory(characterId: String): File = File(File(root, "v2"), safeCharacterId(characterId))

    private fun activeStateFile(): File = File(File(root, "v2"), "_active.json")

    private fun conversationFile(characterId: String, conversationId: String): File? {
        if (!VALID_CONVERSATION_ID.matches(conversationId)) return null
        return File(characterDirectory(characterId), "$conversationId.json")
    }

    private fun safeCharacterId(characterId: String): String =
        characterId.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    companion object {
        private const val SCHEMA_VERSION = 2
        private const val MAX_CACHED_SUMMARIES = 256
        private const val LEGACY_CONVERSATION_ID = "legacy"
        private const val TITLE_MAX_CODE_POINTS = 32
        private const val PREVIEW_MAX_CODE_POINTS = 64
        private val VALID_CONVERSATION_ID = Regex("[A-Za-z0-9_.-]+")
        private val WHITESPACE = Regex("\\s+")

        fun titleFromMessages(messages: List<ChatMessage>): String {
            val source = messages.firstOrNull { it.role == "user" && it.content.isNotBlank() }
                ?: messages.firstOrNull { it.content.isNotBlank() }
            return source?.content?.let { compactAndTruncate(it, TITLE_MAX_CODE_POINTS) }.orEmpty()
        }

        fun titleFromFirstMessage(content: String): String = compactAndTruncate(content, TITLE_MAX_CODE_POINTS)

        private fun compactAndTruncate(content: String, maxCodePoints: Int): String {
            val compact = WHITESPACE.replace(content.trim(), " ")
            var end = 0
            var codePoints = 0
            while (end < compact.length && codePoints < maxCodePoints) {
                val current = compact[end]
                end += if (
                    current.isHighSurrogate() &&
                    end + 1 < compact.length &&
                    compact[end + 1].isLowSurrogate()
                ) {
                    2
                } else {
                    1
                }
                codePoints += 1
            }
            if (end == compact.length) return compact
            return compact.substring(0, end).trimEnd() + "…"
        }
    }
}
