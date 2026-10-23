package com.bangdream.pet.llm

import com.bangdream.pet.I18n

fun llmErrorText(error: String): String = when (error) {
    "LLM_TIMEOUT" -> I18n.t("chat_error_timeout")
    "LLM_HOST_UNREACHABLE" -> I18n.t("chat_error_host")
    "LLM_EMPTY_RESPONSE" -> I18n.t("chat_error_empty")
    "LLM_INVALID_RESPONSE" -> I18n.t("chat_error_invalid")
    "LLM_TOOL_LIMIT" -> I18n.t("chat_error_tool_limit")
    "LLM_CONTENT_FILTERED" -> I18n.t("chat_error_filtered")
    else -> error
}
