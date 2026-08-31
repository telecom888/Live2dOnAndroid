package com.bangdream.pet.live2d

import android.content.Context
import com.bangdream.pet.data.ZstModelArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.SoftReference

object AssetSync {
    private val runtimeCopyLock = Any()
    private val prepareLock = Any()

    /** 已就绪的运行时包版本号（同一进程内只需一次 PackageManager binder IPC）。 */
    @Volatile
    private var runtimeReadyForPackageTime: String? = null

    /**
     * 最近一次准备好的模型（软引用，内存紧张时可被回收）。
     * 旋转屏幕、切换 Tab、壁纸重启都会重新 prepareModel，
     * 之前每次都要把整个 .zst 归档重新解压一遍（数十 MB），是首屏/切换卡顿的主因。
     */
    @Volatile
    private var lastPrepared: SoftReference<CachedPrepared>? = null

    private class CachedPrepared(val modelAssetPath: String, val model: PreparedModel)

    suspend fun prepareModel(context: Context, modelAssetPath: String): PreparedModel = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "live2d_assets")
        val runtimeRoot = File(root, "third_party/Live2D-v2-Lua")
        copyRuntimeIfNeeded(context, runtimeRoot)

        lastPrepared?.get()?.let { cached ->
            if (cached.modelAssetPath == modelAssetPath && cached.model.runtimeRoot == runtimeRoot.absolutePath) {
                return@withContext cached.model
            }
        }

        val archive = ZstModelArchive.readModelPrefix(context, modelAssetPath)
        val prepared = if (archive != null) {
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
        lastPrepared = SoftReference(CachedPrepared(modelAssetPath, prepared))
        prepared
    }

    /** 模型下载/更新/删除后必须让缓存失效，否则会继续用旧归档内容。 */
    fun invalidatePreparedCache() {
        lastPrepared = null
    }

    private fun copyRuntimeIfNeeded(context: Context, runtimeRoot: File) {
        synchronized(runtimeCopyLock) {
            val cachedPackageTime = runtimeReadyForPackageTime
            if (cachedPackageTime != null) return

            val marker = File(runtimeRoot, ".copied")
            val packageTime = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            if (marker.exists() && marker.readText() == packageTime) {
                runtimeReadyForPackageTime = packageTime
                return
            }

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
            runtimeReadyForPackageTime = packageTime
        }
    }

    /**
     * 资源树复制：先写 .part 再改名，避免中断留下截断文件后被 `exists()` 判定为"已复制"而永不修复。
     */
    private fun copyTree(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, "${target.name}.part")
                context.assets.open(assetPath).use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
            return
        }

        target.mkdirs()
        for (child in children) {
            copyTree(context, "$assetPath/$child", File(target, child))
        }
    }
}

data class PreparedModel(
    val runtimeRoot: String,
    val modelPath: String,
    val resourcePaths: List<String> = emptyList(),
    val resourceBytes: List<ByteArray> = emptyList(),
)
