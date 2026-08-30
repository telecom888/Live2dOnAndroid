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
    val index: Int,
    val text: String,
    val motion: String,
    val wavFile: File?,
)

/** 内置语音：参考项目台词（tips.json 中文 / tips_ja.json 日语）→ 克隆 TTS 批量合成缓存，缓存按语言分目录。 */
object BuiltinVoiceManager {
    private const val ASSET_TIPS_ZH = "builtin_tips/tips.json"
    private const val ASSET_TIPS_JA = "builtin_tips/tips_ja.json"
    private const val MAX_GENERATE = 60

    fun assetFor(language: BuiltinVoiceLanguage): String = when (language) {
        BuiltinVoiceLanguage.ZH -> ASSET_TIPS_ZH
        BuiltinVoiceLanguage.JA -> ASSET_TIPS_JA
    }

    fun loadLines(context: Context, characterId: String, language: BuiltinVoiceLanguage): List<BuiltinLine> {
        val tips = readTips(context, language).optJSONArray(characterId) ?: return emptyList()
        val dir = builtinDir(context, characterId, language)
        return buildList {
            for (i in 0 until tips.length()) {
                val item = tips.optJSONObject(i) ?: continue
                val wav = File(dir, "$i.wav").takeIf { it.exists() }
                add(BuiltinLine(i, item.optString("text"), item.optString("motion"), wav))
            }
        }
    }

    fun generatedCount(context: Context, characterId: String, language: BuiltinVoiceLanguage): Int =
        loadLines(context, characterId, language).count { it.wavFile != null }

    fun hasGenerated(context: Context, characterId: String, language: BuiltinVoiceLanguage): Boolean =
        generatedCount(context, characterId, language) > 0

    fun randomLineWithVoice(context: Context, characterId: String, language: BuiltinVoiceLanguage): BuiltinLine? {
        val voiced = loadLines(context, characterId, language).filter { it.wavFile != null }
        if (voiced.isEmpty()) return null
        return voiced.random()
    }

    fun clear(context: Context, characterId: String, language: BuiltinVoiceLanguage) {
        builtinDir(context, characterId, language).deleteRecursively()
    }

    suspend fun generate(
        context: Context,
        characterId: String,
        count: Int,
        language: BuiltinVoiceLanguage,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val settings = com.bangdream.pet.VoiceSettings.load(context)
        if (!settings.isConfigured) return@withContext 0
        val sample = VoiceSamples.activeSampleFile(context, characterId) ?: return@withContext 0
        val lines = loadLines(context, characterId, language)
        val dir = builtinDir(context, characterId, language).apply { mkdirs() }
        val client = VoiceCloneClient(settings.baseUrl, settings.model, settings.apiKey)
        val target = lines.filter { it.wavFile == null }.take(count.coerceAtMost(MAX_GENERATE))
        var done = generatedCount(context, characterId, language)
        val total = (done + target.size).coerceAtLeast(1)
        onProgress(done, total)
        for (line in target) {
            coroutineContext.ensureActive()
            val wav = client.synthesize(line.text, sample) ?: continue
            val file = File(dir, "${line.index}.wav")
            file.writeBytes(wav)
            done++
            onProgress(done, total)
        }
        done
    }

    private fun builtinDir(context: Context, characterId: String, language: BuiltinVoiceLanguage): File =
        File(context.filesDir, "voices_builtin/$characterId/${language.value}").apply { mkdirs() }

    private fun readTips(context: Context, language: BuiltinVoiceLanguage): JSONObject = runCatching {
        context.assets.open(assetFor(language)).bufferedReader().use { JSONObject(it.readText()) }
    }.getOrDefault(JSONObject())
}
