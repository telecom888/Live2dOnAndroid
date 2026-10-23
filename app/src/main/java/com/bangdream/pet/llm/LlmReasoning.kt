package com.bangdream.pet.llm

import org.json.JSONArray
import org.json.JSONObject

/** ScrollBridge passes upstream Chat Completions fields through unchanged. */
internal fun extractReasoningText(delta: JSONObject): String? {
    // Providers can repeat the same text in multiple aliases. Prefer one field per delta,
    // matching CLIProxyAPI's compatibility order, and fall back on empty/null fields.
    for (field in listOf("reasoning_content", "reasoning", "reasoning_details")) {
        reasoningText(delta.opt(field), depth = 0)?.takeIf(String::isNotEmpty)?.let { return it }
    }
    return null
}

private fun reasoningText(value: Any?, depth: Int): String? {
    if (depth > 8) return null
    return when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (index in 0 until value.length()) {
                reasoningText(value.opt(index), depth + 1)?.let(::append)
            }
        }
        is JSONObject -> {
            // Read only known text blocks. Never stringify signatures, encrypted data,
            // IDs or metadata into the user-visible reasoning panel.
            when (value.optString("type")) {
                "", "text", "summary_text", "reasoning.text", "reasoning.summary" ->
                    (value.opt("text") as? String)?.takeIf(String::isNotEmpty)
                        ?: (value.opt("summary") as? String)
                else -> null
            }
        }
        else -> null
    }
}
