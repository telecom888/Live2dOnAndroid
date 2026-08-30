package com.bandori.pet.voice

import android.content.Context
import java.io.File

/** 每角色音色样本管理（v1：assets 预置默认样本，后续支持导入列表与增删改查）。 */
object VoiceSamples {
    private fun sampleDir(context: Context): File = File(context.filesDir, "voices").apply { mkdirs() }

    /** 获取某角色的当前克隆音色样本文件；无则尝试从 assets 预置样本解出；仍无返回 null。 */
    fun activeSampleFile(context: Context, characterId: String): File? {
        val dir = sampleDir(context)
        val existing = dir.listFiles()?.firstOrNull { it.name.startsWith("${characterId}_") }
        if (existing != null && existing.exists()) return existing

        val assetNames = listOf("$characterId.mp3", "$characterId.wav")
        for (name in assetNames) {
            val target = File(dir, "${characterId}_default.${name.substringAfterLast('.')}")
            val copied = runCatching {
                context.assets.open("voices/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }.getOrDefault(false)
            if (copied && target.exists()) return target
        }
        return null
    }
}
