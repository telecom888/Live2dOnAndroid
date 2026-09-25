package com.bangdream.pet.llm

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatBranchGraphTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun message(id: String, role: String, content: String, time: Long) =
        ChatMessage(id = id, role = role, content = content, timestamp = time)

    @Test
    fun editingAnEarlyUserMessagePreservesItsOriginalFuture() {
        val original = ChatBranchGraph.normalized(ChatConversation(
            id = "chat", characterId = "kasumi", title = "first", createdAt = 1, updatedAt = 4,
            messages = listOf(
                message("u1", "user", "first", 1), message("a1", "assistant", "answer", 2),
                message("u2", "user", "follow up", 3), message("a2", "assistant", "later answer", 4),
            ),
        ))
        val edited = ChatBranchGraph.append(original, message("u1b", "user", "changed", 5), parentId = null)
        val regenerated = ChatBranchGraph.append(edited, message("a1b", "assistant", "new answer", 6))

        assertEquals(listOf("u1b", "a1b"), regenerated.messages.map { it.id })
        assertEquals(listOf("u1", "a1", "u2", "a2"), ChatBranchGraph.select(regenerated, "u1").messages.map { it.id })
        assertEquals(listOf("u1b", "a1b"), ChatBranchGraph.select(regenerated, "u1b").messages.map { it.id })
        assertEquals(2, ChatBranchGraph.versions(regenerated).getValue("u1b").total)
    }

    @Test
    fun assistantRegenerationOnlyUsesItsParentBranchAndSurvivesReload() {
        val repository = ChatHistoryRepository(temporaryFolder.newFolder("history"))
        val first = ChatBranchGraph.normalized(ChatConversation(
            id = "chat", characterId = "kasumi", title = "hello", createdAt = 1, updatedAt = 2,
            messages = listOf(message("u1", "user", "hello", 1), message("a1", "assistant", "old", 2)),
        ))
        val prefix = ChatBranchGraph.truncateAt(first, "u1")
        val changed = ChatBranchGraph.append(prefix, message("a2", "assistant", "new", 3))
        repository.saveConversation(changed)
        val reloaded = repository.loadConversation("kasumi", "chat")!!

        assertEquals(listOf("u1", "a2"), reloaded.messages.map { it.id })
        assertEquals(2, ChatBranchGraph.versions(reloaded).getValue("a2").total)
        assertEquals(listOf("u1", "a1"), ChatBranchGraph.select(reloaded, "a1").messages.map { it.id })
        assertFalse(File(temporaryFolder.root, "history/v2/kasumi/chat.json").readText().contains("\"schemaVersion\":2"))
    }

    @Test
    fun sendTimeIsRequestOnlyAndRespectsEachMessageFlag() {
        val timed = message("u1", "user", "早上好", 1_790_267_100_000L)
            .copy(timeContextEnabled = true, timeZoneId = "Asia/Macau")
        val plain = message("u2", "user", "角色扮演", 1_790_267_100_000L)
        val payload = LlmChatClient().messagesToJsonArray("system", listOf(timed, plain))
        assertTrue(payload.getJSONObject(1).getString("content").contains("此消息发送时间："))
        assertEquals("角色扮演", payload.getJSONObject(2).getString("content"))
        assertEquals("早上好", timed.content)
    }
}
