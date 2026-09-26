package com.bangdream.pet

import android.content.Context

data class ChatDisplayPreferences(
    val showMessageTime: Boolean = true,
    val showMonthDay: Boolean = false,
    val showReadAloud: Boolean = true,
    val showRetryIcon: Boolean = true,
    val showContextUsage: Boolean = true,
    val showModelTimeControl: Boolean = true,
    val multiPartReplies: Boolean = false,
    val multiPartIntervalMs: Int = 200,
    val lineOwnBubbleColor: Int = DEFAULT_LINE_OWN_BUBBLE_COLOR,
    val lineOtherBubbleColor: Int = DEFAULT_LINE_OTHER_BUBBLE_COLOR,
    val lineBackgroundColor: Int = DEFAULT_LINE_BACKGROUND_COLOR,
    val lineBackgroundImagePath: String? = null,
) {
    fun save(context: Context) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("chat_show_message_time", showMessageTime)
            .putBoolean("chat_show_month_day", showMonthDay)
            .putBoolean("chat_show_read_aloud", showReadAloud)
            .putBoolean("chat_show_retry_icon", showRetryIcon)
            .putBoolean("chat_show_context_usage", showContextUsage)
            .putBoolean("chat_show_model_time_control", showModelTimeControl)
            .putBoolean("chat_multi_part_replies", multiPartReplies)
            .putInt("chat_multi_part_interval_ms", multiPartIntervalMs.coerceIn(0, 10000))
            .putInt("chat_line_own_bubble_color", lineOwnBubbleColor)
            .putInt("chat_line_other_bubble_color", lineOtherBubbleColor)
            .putInt("chat_line_background_color", lineBackgroundColor)
            .apply {
                if (lineBackgroundImagePath.isNullOrBlank()) remove("chat_line_background_image_path")
                else putString("chat_line_background_image_path", lineBackgroundImagePath)
            }
            .apply()
    }

    companion object {
        val DEFAULT_LINE_OWN_BUBBLE_COLOR: Int = 0xFFFFE327.toInt()
        val DEFAULT_LINE_OTHER_BUBBLE_COLOR: Int = 0xFFFFFFFF.toInt()
        val DEFAULT_LINE_BACKGROUND_COLOR: Int = 0xFFAAC2D3.toInt()

        fun load(context: Context): ChatDisplayPreferences {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            return ChatDisplayPreferences(
                showMessageTime = prefs.getBoolean("chat_show_message_time", true),
                showMonthDay = prefs.getBoolean("chat_show_month_day", false),
                showReadAloud = prefs.getBoolean("chat_show_read_aloud", true),
                showRetryIcon = prefs.getBoolean("chat_show_retry_icon", true),
                showContextUsage = prefs.getBoolean("chat_show_context_usage", true),
                showModelTimeControl = prefs.getBoolean("chat_show_model_time_control", true),
                multiPartReplies = prefs.getBoolean("chat_multi_part_replies", false),
                multiPartIntervalMs = prefs.getInt("chat_multi_part_interval_ms", 200).coerceIn(0, 10000),
                lineOwnBubbleColor = prefs.getInt("chat_line_own_bubble_color", DEFAULT_LINE_OWN_BUBBLE_COLOR),
                lineOtherBubbleColor = prefs.getInt("chat_line_other_bubble_color", DEFAULT_LINE_OTHER_BUBBLE_COLOR),
                lineBackgroundColor = prefs.getInt("chat_line_background_color", DEFAULT_LINE_BACKGROUND_COLOR),
                lineBackgroundImagePath = prefs.getString("chat_line_background_image_path", null),
            )
        }
    }
}
