package com.bangdream.pet.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bangdream.pet.loadWallpaperOriginalBackupPath
import com.bangdream.pet.saveWallpaperOriginalBackupPath
import com.bangdream.pet.saveWallpaperBackgroundUri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** 保留系统原壁纸：启用动态壁纸前捕获当前静态壁纸，作为背景层绘制；提供恢复入口。 */
object WallpaperBackup {
    private const val MAX_EDGE = 2048

    /** 捕获结果：成功 / 需要「所有文件访问」权限（Android 13+ 静态壁纸）/ 失败。 */
    sealed interface WallpaperCaptureResult {
        /** uri 为 file:// 背景地址；fromBackup=true 表示复用了旧备份（非本次新捕获）。 */
        data class Success(val uri: String, val fromBackup: Boolean = false) : WallpaperCaptureResult
        data object NeedAllFilesAccess : WallpaperCaptureResult
        data object Failed : WallpaperCaptureResult
    }

    /**
     * 捕获当前系统壁纸并设为壁纸背景 URI。
     * 逻辑（移植逸风工具箱）：
     * 1. 当前是动态壁纸 -> 无法捕获（Failed）
     * 2. Android 13+ 未授予「所有文件访问」-> 若有旧备份复用（Success+fromBackup），否则 NeedAllFilesAccess
     * 3. WallpaperUtils.readBitmap(HOME) -> 保存 PNG 到应用私有目录
     * 4. 失败则复用旧备份
     */
    fun captureAndUseAsBackgroundResult(context: Context): WallpaperCaptureResult {
        val wm = WallpaperManager.getInstance(context)
        if (wm.wallpaperInfo != null) return WallpaperCaptureResult.Failed

        if (!WallpaperUtils.canReadRealWallpaper(context)) {
            return reuseOldBackup(context)
                ?.let { WallpaperCaptureResult.Success(it, fromBackup = true) }
                ?: WallpaperCaptureResult.NeedAllFilesAccess
        }

        val file = captureToFile(context)
        if (file != null) {
            saveWallpaperOriginalBackupPath(context, file.absolutePath)
            val uri = "file://" + file.absolutePath
            saveWallpaperBackgroundUri(context, uri)
            return WallpaperCaptureResult.Success(uri)
        }
        return reuseOldBackup(context)
            ?.let { WallpaperCaptureResult.Success(it, fromBackup = true) }
            ?: WallpaperCaptureResult.Failed
    }

    /** 兼容旧调用：返回 uri；失败/需授权返回 null。 */
    fun captureAndUseAsBackground(context: Context): String? =
        (captureAndUseAsBackgroundResult(context) as? WallpaperCaptureResult.Success)?.uri

    /** 当前系统壁纸状态：动态壁纸（含组件名） / 静态壁纸可捕获 / 需授权 / 静态壁纸但系统限制读取。 */
    fun wallpaperStatus(context: Context): String {
        val wm = WallpaperManager.getInstance(context)
        val info = wm.wallpaperInfo
        return when {
            info != null -> {
                val label = runCatching { info.loadLabel(context.packageManager).toString() }
                    .getOrNull()
                    ?: info.component.flattenToShortString()
                "动态壁纸：$label（${info.component.flattenToShortString()}）"
            }
            !WallpaperUtils.canReadRealWallpaper(context) ->
                "静态壁纸，但 Android 13+ 需要授予「所有文件访问」权限才能读取（可在下方授权后捕获）"
            runCatching { wm.drawable != null || wm.peekDrawable() != null }.getOrDefault(false) ->
                "静态壁纸（可捕获为背景）"
            else -> "静态壁纸，但系统限制应用读取（可用「选择照片」作为背景）"
        }
    }

    private fun captureToFile(context: Context): File? {
        val bitmap = captureCurrentWallpaperBitmap(context)
        if (bitmap != null) {
            val file = File(context.filesDir, "wallpaper_backup.png")
            val ok = runCatching {
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 90, out) }
            }.getOrDefault(false)
            bitmap.recycle()
            if (ok) return file
        }
        return null
    }

    private fun reuseOldBackup(context: Context): String? {
        val path = loadWallpaperOriginalBackupPath(context) ?: return null
        val old = File(path)
        if (!old.exists()) return null
        return "file://" + old.absolutePath
    }

    fun loadBackupBitmap(context: Context): Bitmap? {
        val path = loadWallpaperOriginalBackupPath(context) ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = computeSampleSize(file.length()) }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()
    }

    /** 用备份位图恢复系统静态壁纸。 */
    fun restoreSystemWallpaper(context: Context): Boolean {
        val bitmap = loadBackupBitmap(context) ?: return false
        return runCatching {
            WallpaperManager.getInstance(context).setBitmap(bitmap)
            bitmap.recycle()
            true
        }.getOrElse {
            bitmap.recycle()
            false
        }
    }

    private fun captureCurrentWallpaperBitmap(context: Context): Bitmap? {
        if (WallpaperManager.getInstance(context).wallpaperInfo != null) return null
        if (!WallpaperUtils.canReadRealWallpaper(context)) return null
        return WallpaperUtils.readBitmap(WallpaperManager.getInstance(context), WallpaperTarget.HOME)
            ?.let { scaleToMaxEdge(it) }
    }

    private fun computeSampleSize(bytes: Long): Int {
        var sample = 1
        var size = bytes
        while (size > 8 * 1024 * 1024) {
            sample *= 2
            size /= 4
        }
        return sample
    }

    private fun scaleToMaxEdge(bitmap: Bitmap): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= MAX_EDGE) return bitmap
        val scale = MAX_EDGE.toFloat() / edge.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
