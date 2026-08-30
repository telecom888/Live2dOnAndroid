package com.bangdream.pet.llm

import android.content.Context

/** 本地聊天后端：历史记录、角色提示词与 LLM 请求都在手机端完成。 */
class LocalChatBackend(context: Context) {
    internal val history = ChatHistoryRepository(context)
    internal val prompts = CharacterPromptRepository(context)
    internal val client = LlmChatClient()
}
