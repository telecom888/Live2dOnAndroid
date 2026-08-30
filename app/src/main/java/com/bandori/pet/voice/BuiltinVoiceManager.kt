package com.bandori.pet.voice

import android.content.Context
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

/** 内置语音：参考项目 tips.js 台词（assets/builtin_tips/tips.json）→ 克隆 TTS 批量合成缓存。 */
object BuiltinVoiceManager {
    private const val ASSET_TIPS = "builtin_tips/tips.json"
    private const val MAX_GENERATE = 60

    fun loadLines(context: Context, characterId: String): List<BuiltinLine> {
        val tips = readTips(context).optJSONArray(characterId) ?: return emptyList()
        val dir = builtinDir(context, characterId)
        return buildList {
            for (i in 0 until tips.length()) {
                val item = tips.optJSONObject(i) ?: continue
                val wav = File(dir, "$i.wav").takeIf { it.exists() }
                add(BuiltinLine(i, item.optString("text"), item.optString("motion"), wav))
            }
        }
    }

    fun generatedCount(context: Context, characterId: String): Int =
        loadLines(context, characterId).count { it.wavFile != null }

    fun hasGenerated(context: Context, characterId: String): Boolean =
        generatedCount(context, characterId) > 0

    fun randomLineWithVoice(context: Context, characterId: String): BuiltinLine? {
        val voiced = loadLines(context, characterId).filter { it.wavFile != null }
        if (voiced.isEmpty()) return null
        return voiced.random()
    }

    fun clear(context: Context, characterId: String) {
        builtinDir(context, characterId).deleteRecursively()
    }

    suspend fun generate(
        context: Context,
        characterId: String,
        count: Int,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val settings = com.bandori.pet.VoiceSettings.load(context)
        if (!settings.isConfigured) return@withContext 0
        val sample = VoiceSamples.activeSampleFile(context, characterId) ?: return@withContext 0
        val lines = loadLines(context, characterId)
        val dir = builtinDir(context, characterId).apply { mkdirs() }
        val client = VoiceCloneClient(settings.baseUrl, settings.model, settings.apiKey)
        val target = lines.filter { it.wavFile == null }.take(count.coerceAtMost(MAX_GENERATE))
        var done = generatedCount(context, characterId)
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

    private fun builtinDir(context: Context, characterId: String): File =
        File(context.filesDir, "voices_builtin/$characterId").apply { mkdirs() }

    private fun readTips(context: Context): JSONObject = runCatching {
        context.assets.open(ASSET_TIPS).bufferedReader().use { JSONObject(it.readText()) }
    }.getOrDefault(JSONObject())
}
