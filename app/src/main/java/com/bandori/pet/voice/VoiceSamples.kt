package com.bandori.pet.voice

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VoiceSampleInfo(
    val id: String,
    val name: String,
    val file: File,
    val active: Boolean,
)

/** 每角色音色样本管理：预置默认样本 + 应用内导入多个样本，可增删查改、选择当前音色。 */
object VoiceSamples {
    private const val PREFS = "voice_samples"
    private fun sampleDir(context: Context): File = File(context.filesDir, "voices").apply { mkdirs() }
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun listSamples(context: Context, characterId: String): List<VoiceSampleInfo> {
        val active = prefs(context).getString("active_$characterId", null)
        val stored = decode(prefs(context).getString("samples_$characterId", null))
        val dir = sampleDir(context)
        val files = dir.listFiles()?.filter { it.isFile && it.name.startsWith("${characterId}_") }.orEmpty()
        val byName = files.associateBy { it.name }
        val ids = mutableSetOf<String>()
        val result = mutableListOf<VoiceSampleInfo>()
        for (item in stored) {
            val file = byName[item.id] ?: continue
            ids.add(item.id)
            result.add(VoiceSampleInfo(item.id, item.name, file, item.id == active))
        }
        // 未入库的历史文件（如旧默认样本）也列出
        for (file in files) {
            if (file.name in ids) continue
            val id = file.name
            ids.add(id)
            result.add(VoiceSampleInfo(id, file.name.removePrefix("${characterId}_").substringBeforeLast('.'), file, id == active))
        }
        return result
    }

    fun activeSampleFile(context: Context, characterId: String): File? {
        val active = listSamples(context, characterId).firstOrNull { it.active }?.file
        if (active != null && active.exists()) return active
        // 预置默认样本
        val dir = sampleDir(context)
        for (name in listOf("$characterId.mp3", "$characterId.wav")) {
            val target = File(dir, "${characterId}_default.${name.substringAfterLast('.')}")
            val copied = runCatching {
                context.assets.open("voices/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }.getOrDefault(false)
            if (copied && target.exists()) {
                setActiveSample(context, characterId, target.name)
                return target
            }
        }
        return listSamples(context, characterId).firstOrNull()?.file
    }

    fun importSample(context: Context, characterId: String, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val name = resolver.getDisplayName(uri) ?: "sample_${System.currentTimeMillis()}"
        val ext = name.substringAfterLast('.', "mp3").lowercase().takeIf { it in setOf("mp3", "wav") } ?: "mp3"
        val id = "${characterId}_${System.currentTimeMillis()}.$ext"
        val target = File(sampleDir(context), id)
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching false
        if (target.length() == 0L) {
            target.delete()
            return@runCatching false
        }
        val displayName = name.substringBeforeLast('.').take(40)
        addStored(context, characterId, id, displayName)
        setActiveSample(context, characterId, id)
        true
    }.getOrDefault(false)

    fun setActiveSample(context: Context, characterId: String, id: String) {
        prefs(context).edit().putString("active_$characterId", id).apply()
    }

    fun renameSample(context: Context, characterId: String, id: String, newName: String) {
        val stored = decode(prefs(context).getString("samples_$characterId", null)).toMutableList()
        val index = stored.indexOfFirst { it.id == id }
        val name = newName.trim().take(40)
        if (name.isEmpty()) return
        if (index >= 0) {
            stored[index] = stored[index].copy(name = name)
        } else {
            stored.add(StoredSample(id, name))
        }
        prefs(context).edit().putString("samples_$characterId", encode(stored)).apply()
    }

    fun deleteSample(context: Context, characterId: String, id: String) {
        val dir = sampleDir(context)
        val file = File(dir, id)
        file.delete()
        val stored = decode(prefs(context).getString("samples_$characterId", null)).filterNot { it.id == id }
        prefs(context).edit().putString("samples_$characterId", encode(stored)).apply()
        if (prefs(context).getString("active_$characterId", null) == id) {
            prefs(context).edit().remove("active_$characterId").apply()
        }
    }

    private fun addStored(context: Context, characterId: String, id: String, name: String) {
        val stored = decode(prefs(context).getString("samples_$characterId", null)).toMutableList()
        stored.removeAll { it.id == id }
        stored.add(StoredSample(id, name))
        prefs(context).edit().putString("samples_$characterId", encode(stored)).apply()
    }

    private data class StoredSample(val id: String, val name: String)
    private fun decode(value: String?): List<StoredSample> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(StoredSample(obj.optString("id"), obj.optString("name")))
                }
            }
        }.getOrDefault(emptyList())
    }
    private fun encode(items: List<StoredSample>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
    }.toString()
}

private fun android.content.ContentResolver.getDisplayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
