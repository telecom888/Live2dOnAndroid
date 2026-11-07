package com.bangdream.pet.llm

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ChatContinuationTest {
    private val user = ChatMessage("u", "user", "original question", 1)
    private val partial = ChatMessage("a", "assistant", "Hello ", 2, parentId = "u", incomplete = true)

    @Test
    fun repeatedContinuationsSurviveReloadAndKeepWireTurnsBehindOneBubble() {
        val root = Files.createTempDirectory("continuation-history").toFile()
        try {
            val history = ChatHistoryRepository(root)
            val route = LlmReplyRoute(LlmModelRef("provider", "model"), "endpoint-fingerprint")
            val original = ChatConversation("chat", "anon", "chat", 1, 2, listOf(user, partial.copy(continuationRoute = route)))
            history.saveConversation(original)
            val loaded = history.loadConversation("anon", "chat")!!
            assertEquals(route, loaded.messages.last().continuationRoute)
            val resumed = ChatContinuation.merge(loaded.messages.last(), "world", "", completed = false)
            val saved = history.saveConversation(ChatBranchGraph.updateMessage(loaded, "a") { resumed })
            val reloaded = history.loadConversation("anon", "chat")!!
            val completed = ChatContinuation.merge(reloaded.messages.last(), "!", "", completed = true)
            val final = history.saveConversation(ChatBranchGraph.updateMessage(saved, "a") { completed })
            assertEquals(listOf("u", "a"), final.messages.map { it.id })
            assertEquals("Hello world!", final.messages.last().content)
            assertFalse(ChatContinuation.canContinue(final.messages.last()))

            val request = LlmChatClient().messagesToJsonArray("persona", final.messages)
            assertEquals(listOf("system", "user", "assistant", "user", "assistant", "user", "assistant"),
                (0 until request.length()).map { request.getJSONObject(it).getString("role") })
            assertEquals(listOf("persona", "original question", "Hello ", ChatContinuation.PROMPT, "world", ChatContinuation.PROMPT, "!"),
                (0 until request.length()).map { request.getJSONObject(it).getString("content") })

            // A new user turn hides continuation without changing the earlier reply's recovery data.
            val next = ChatBranchGraph.append(final, user.copy(id = "u2", content = "new question"))
            assertFalse(ChatContinuation.canContinue(next.messages.last()))
            assertEquals(2, final.messages.size)
        } finally { root.deleteRecursively() }
    }

    @Test
    fun emptyOrCancelledContinuationDoesNotLeaveAnExtraInstructionInHistory() {
        val unchanged = ChatContinuation.merge(partial, "", "", completed = false)
        assertEquals(partial, unchanged)
        assertEquals(ChatContinuation.PROMPT, ChatContinuation.requestMessage(partial).requestHistory.last().content)
        assertTrue(partial.requestHistory.isEmpty())
        assertFalse(ChatContinuation.canContinue(partial.copy(segments = listOf("Hello"))))
        assertFalse(ChatContinuation.canContinue(partial.copy(content = "")))
        assertFalse(ChatContinuation.canContinue(partial.copy(incomplete = false)))
    }

    @Test
    fun singlePartRecoveryPreservesWhitespaceAtTheJoin() {
        val decoder = AssistantReplyDecoder(emptySet(), false)
        decoder.consume("Hello ")
        val result = decoder.finish(completed = false, preserveWhitespace = true)
        assertEquals("Hello ", result.text)
        assertEquals("Hello world", ChatContinuation.merge(partial.copy(content = result.text), "world", "", true).content)
    }

    @Test
    fun continuationIncludesFullContextAndMapsToBothNativeProtocols() {
        val source = listOf(user, ChatContinuation.requestMessage(partial))
        val request = LlmChatClient().messagesToJsonArray("persona", source)
        for (protocol in listOf(LlmProtocol.ANTHROPIC, LlmProtocol.GOOGLE)) {
            val native = buildNativeRequest(LlmSettings(protocol = protocol), request, true)
            val turns = native.getJSONArray(if (protocol == LlmProtocol.GOOGLE) "contents" else "messages")
            assertEquals(3, turns.length())
            assertEquals("user", turns.getJSONObject(2).getString("role"))
            val parts = turns.getJSONObject(1).getJSONArray(if (protocol == LlmProtocol.GOOGLE) "parts" else "content")
            assertEquals("Hello ", parts.getJSONObject(0).getString("text"))
        }
    }
}
