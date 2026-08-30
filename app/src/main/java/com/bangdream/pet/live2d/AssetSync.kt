package com.bangdream.pet.live2d

import android.content.Context
import com.bangdream.pet.data.ZstModelArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AssetSync {
    private val runtimeCopyLock = Any()

    suspend fun prepareModel(context: Context, modelAssetPath: String): PreparedModel = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "live2d_assets")
        val runtimeRoot = File(root, "third_party/Live2D-v2-Lua")
        copyRuntimeIfNeeded(context, runtimeRoot)

        val archive = ZstModelArchive.readModelPrefix(context, modelAssetPath)
        if (archive != null) {
            PreparedModel(
                runtimeRoot = runtimeRoot.absolutePath,
                modelPath = archive.modelPath,
                resourcePaths = archive.resources.keys.toList(),
                resourceBytes = archive.resources.values.toList(),
            )
        } else {
            // 兼容 index.json 布局（live2d-widget-mygo）：模型目录为 models/<角色>/<服装>/index.json，
            // 资源可能引用同级 ../036_general/ 等目录，因此整棵角色目录树复制到 filesDir 后走磁盘读取。
            val isIndexJson = modelAssetPath.endsWith("index.json")
            val assetRoot = if (isIndexJson) {
                val parts = modelAssetPath.split('/')
                parts.take(2).joinToString("/")
            } else {
                modelAssetPath.substringBeforeLast('/')
            }
            val targetRoot = File(root, assetRoot)
            copyTree(context, assetRoot, targetRoot)
            PreparedModel(
                runtimeRoot = runtimeRoot.absolutePath,
                modelPath = File(root, modelAssetPath).absolutePath,
                resourcePaths = emptyList(),
                resourceBytes = emptyList(),
            )
        }
    }

    private fun copyRuntimeIfNeeded(context: Context, runtimeRoot: File) {
        synchronized(runtimeCopyLock) {
            val marker = File(runtimeRoot, ".copied")
            val packageTime = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            if (marker.exists() && marker.readText() == packageTime) return

            val parent = runtimeRoot.parentFile ?: return
            parent.mkdirs()
            val tempRoot = File(parent, "${runtimeRoot.name}.tmp")
            if (tempRoot.exists()) tempRoot.deleteRecursively()
            copyTree(context, "third_party/Live2D-v2-Lua", tempRoot)
            File(tempRoot, ".copied").writeText(packageTime)

            if (runtimeRoot.exists()) runtimeRoot.deleteRecursively()
            if (!tempRoot.renameTo(runtimeRoot)) {
                tempRoot.copyRecursively(runtimeRoot, overwrite = true)
                tempRoot.deleteRecursively()
            }
        }
    }

    private fun copyTree(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return
        }

        target.mkdirs()
        for (child in children) {
            copyTree(context, "$assetPath/$child", File(target, child))
        }
    }

    private fun readAssetDirFiles(context: Context, assetDir: String, targetDir: File): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        collectAssetFiles(context, assetDir, targetDir, result)
        return result
    }

    private fun collectAssetFiles(context: Context, assetPath: String, targetFile: File, result: MutableMap<String, ByteArray>) {
        val entries = context.assets.list(assetPath)
        if (entries.isNullOrEmpty()) {
            runCatching {
                result[targetFile.absolutePath] = context.assets.open(assetPath).use { it.readBytes() }
            }
            return
        }
        for (entry in entries) {
            collectAssetFiles(context, "$assetPath/$entry", File(targetFile, entry), result)
        }
    }
}

data class PreparedModel(
    val runtimeRoot: String,
    val modelPath: String,
    val resourcePaths: List<String> = emptyList(),
    val resourceBytes: List<ByteArray> = emptyList(),
)
