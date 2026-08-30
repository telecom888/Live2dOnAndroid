package com.bangdream.pet.voice

import android.content.Context
import com.bangdream.pet.BuiltinVoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

data class BuiltinLine(
    val characterId: String,
    val language: BuiltinVoiceLanguage,
    val index: Int,
    val text: String,
    val display: String,
    val motion: String,
) {
    fun hasWav(context: Context): Boolean = runCatching {
        context.assets.open(BuiltinVoiceManager.assetVoicePath(characterId, language, index)).use { it.available() > 0 }
    }.getOrDefault(false)

    fun readWav(context: Context): ByteArray? = runCatching {
        context.assets.open(BuiltinVoiceManager.assetVoicePath(characterId, language, index)).use { it.readBytes() }
    }.getOrNull()
}

/** 内置语音：参考项目台词（tips.json 中文 / tips_ja.json 日语）→ 克隆 TTS 批量合成缓存，缓存按语言分目录。 */
object BuiltinVoiceManager {
    private const val ASSET_TIPS_ZH = "builtin_tips/tips.json"
    private const val ASSET_TIPS_JA = "builtin_tips/tips_ja.json"

    fun assetFor(language: BuiltinVoiceLanguage): String = when (language) {
        BuiltinVoiceLanguage.ZH -> ASSET_TIPS_ZH
        BuiltinVoiceLanguage.JA -> ASSET_TIPS_JA
    }

    fun loadLines(context: Context, characterId: String, language: BuiltinVoiceLanguage): List<BuiltinLine> {
        val tips = readTips(context, language).optJSONArray(characterId) ?: return emptyList()
        return buildList {
            for (i in 0 until tips.length()) {
                val item = tips.optJSONObject(i) ?: continue
                add(BuiltinLine(characterId, language, i, item.optString("text"), item.optString("display", item.optString("text")), item.optString("motion")))
            }
        }
    }

    fun generatedCount(context: Context, characterId: String, language: BuiltinVoiceLanguage): Int =
        loadLines(context, characterId, language).count { it.hasWav(context) }

    fun hasGenerated(context: Context, characterId: String, language: BuiltinVoiceLanguage): Boolean =
        generatedCount(context, characterId, language) > 0

    /** 预生成语音在 assets 中的路径（随版本内置，mp3 减小体积）。 */
    fun assetVoicePath(characterId: String, language: BuiltinVoiceLanguage, index: Int): String =
        "voices_builtin/$characterId/${language.value}/$index.mp3"

    fun randomLineWithVoice(context: Context, characterId: String, language: BuiltinVoiceLanguage): BuiltinLine? {
        val voiced = loadLines(context, characterId, language).filter { it.hasWav(context) }
        if (voiced.isEmpty()) return null
        return voiced.random()
    }

    fun clear(context: Context, characterId: String, language: BuiltinVoiceLanguage) {
        builtinDir(context, characterId, language).deleteRecursively()
    }


    private fun builtinDir(context: Context, characterId: String, language: BuiltinVoiceLanguage): File =
        File(context.filesDir, "voices_builtin/$characterId/${language.value}").apply { mkdirs() }

    private fun readTips(context: Context, language: BuiltinVoiceLanguage): JSONObject = runCatching {
        context.assets.open(assetFor(language)).bufferedReader().use { JSONObject(it.readText()) }
    }.getOrDefault(JSONObject())
}
