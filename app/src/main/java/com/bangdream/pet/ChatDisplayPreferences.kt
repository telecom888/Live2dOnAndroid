package com.bangdream.pet

import android.content.Context

data class ChatDisplayPreferences(
    val showMessageTime: Boolean = true,
    val showMonthDay: Boolean = false,
    val showReadAloud: Boolean = true,
    val showRetryIcon: Boolean = true,
    val showContextUsage: Boolean = true,
    val showModelTimeControl: Boolean = true,
) {
    fun save(context: Context) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("chat_show_message_time", showMessageTime)
            .putBoolean("chat_show_month_day", showMonthDay)
            .putBoolean("chat_show_read_aloud", showReadAloud)
            .putBoolean("chat_show_retry_icon", showRetryIcon)
            .putBoolean("chat_show_context_usage", showContextUsage)
            .putBoolean("chat_show_model_time_control", showModelTimeControl)
            .apply()
    }

    companion object {
        fun load(context: Context): ChatDisplayPreferences {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            return ChatDisplayPreferences(
                showMessageTime = prefs.getBoolean("chat_show_message_time", true),
                showMonthDay = prefs.getBoolean("chat_show_month_day", false),
                showReadAloud = prefs.getBoolean("chat_show_read_aloud", true),
                showRetryIcon = prefs.getBoolean("chat_show_retry_icon", true),
                showContextUsage = prefs.getBoolean("chat_show_context_usage", true),
                showModelTimeControl = prefs.getBoolean("chat_show_model_time_control", true),
            )
        }
    }
}
