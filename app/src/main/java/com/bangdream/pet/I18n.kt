package com.bangdream.pet

import android.content.Context
import org.json.JSONObject
import java.util.Locale

object I18n {

    private const val LANGUAGE_KEY = "app_language"
    private val supportedLanguages = setOf("zh", "ja", "en")
    @Volatile private var strings: Map<String, String> = emptyMap()
    private val placeholderRegex = Regex("\\{(\\d+)\\}")

    @Volatile
    private var loadedLanguage: String? = null

    fun currentLanguage(context: Context): String {
        val saved = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, null)
        if (saved in supportedLanguages) return saved!!
        return when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "zh" -> "zh"
            "ja" -> "ja"
            else -> "en"
        }
    }

    fun setLanguage(context: Context, language: String) {
        require(language in supportedLanguages)
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().putString(LANGUAGE_KEY, language).apply()
        init(context)
    }

    fun init(context: Context) {
        val lang = currentLanguage(context)
        if (loadedLanguage == lang) return
        synchronized(this) {
            if (loadedLanguage == lang) return
            val updated = mutableMapOf<String, String>()
            val languages = when (lang) {
                "zh" -> listOf("en", "zh")
                "en" -> listOf("zh", "en")
                else -> listOf("zh", "en", lang)
            }
            languages.forEach { loadFromAssets(context, "lang/$it.json", updated) }
            strings = updated
            loadedLanguage = lang
        }
    }

    private fun loadFromAssets(context: Context, path: String, target: MutableMap<String, String>): Boolean {
        return runCatching {
            context.assets.open(path).bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    target[key] = json.optString(key, key)
                }
            }
            true
        }.getOrDefault(false)
    }

    fun t(key: String, vararg args: Any?): String {
        val template = strings[key] ?: key
        if (args.isEmpty()) return template
        return placeholderRegex.replace(template) { match ->
            val index = match.groupValues[1].toIntOrNull()
            if (index != null && index < args.size) args[index].toString() else match.value
        }
    }
}
