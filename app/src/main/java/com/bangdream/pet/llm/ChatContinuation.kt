package com.bangdream.pet.llm

import java.io.IOException

internal class IncompleteLlmStream(val outputLimit: Boolean = false) :
    IOException(if (outputLimit) "LLM_OUTPUT_LIMIT" else "LLM_STREAM_INTERRUPTED")

internal object ChatContinuation {
    const val PROMPT = "上一条回复中途中断，请从末尾继续，不要重复已有内容，不要解释续写过程，直接输出后续正文。"

    fun canContinue(message: ChatMessage?): Boolean = message != null && message.role == "assistant" &&
        message.incomplete && message.content.isNotBlank() && message.segments.isEmpty()

    fun turns(message: ChatMessage): List<ChatRequestTurn> = message.requestHistory.ifEmpty {
        listOf(ChatRequestTurn(message.role, message.content))
    }

    fun requestMessage(message: ChatMessage): ChatMessage = message.copy(
        requestHistory = turns(message) + ChatRequestTurn("user", PROMPT),
    )

    fun merge(original: ChatMessage, text: String, reasoning: String, completed: Boolean): ChatMessage =
        original.copy(
            content = original.content + text,
            reasoning = listOfNotNull(original.reasoning, reasoning.takeIf(String::isNotBlank))
                .joinToString("\n\n").takeIf(String::isNotBlank),
            incomplete = !completed,
            requestHistory = if (text.isEmpty()) original.requestHistory else
                turns(original) + ChatRequestTurn("user", PROMPT) + ChatRequestTurn("assistant", text),
        )
}
