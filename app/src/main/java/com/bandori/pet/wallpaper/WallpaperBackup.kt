package com.bandori.pet.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.bandori.pet.loadWallpaperOriginalBackupPath
import com.bandori.pet.saveWallpaperOriginalBackupPath
import com.bandori.pet.saveWallpaperBackgroundUri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** 保留系统原壁纸：启用动态壁纸前捕获当前静态壁纸，作为背景层绘制；提供恢复入口。 */
object WallpaperBackup {
    private const val MAX_EDGE = 2048

    /**
     * 捕获当前系统壁纸并设为壁纸背景 URI。
     * 兜底顺序：
     * 1. WallpaperManager.getDrawable()
     * 2. WallpaperManager.getWallpaper()（Bitmap）
     * 3. 已存在的旧备份文件（复用，不重新捕获）
     * 返回 uri；全部失败返回 null（例如当前已是动态壁纸且无备份）。
     */
    fun captureAndUseAsBackground(context: Context): String? {
        val file = captureToFile(context) ?: return null
        saveWallpaperOriginalBackupPath(context, file.absolutePath)
        val uri = "file://" + file.absolutePath
        saveWallpaperBackgroundUri(context, uri)
        return uri
    }

    /** 当前系统壁纸状态：动态壁纸（含组件名） / 静态壁纸可捕获 / 静态壁纸但系统限制读取。 */
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
        // 兜底：复用旧备份
        val path = loadWallpaperOriginalBackupPath(context)
        val old = path?.let { File(it) }
        return old?.takeIf { it.exists() }
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

    private fun captureCurrentWallpaperBitmap(context: Context): Bitmap? = runCatching {
        val wm = WallpaperManager.getInstance(context)
        val fromDrawable = runCatching { wm.drawable }.getOrNull()
        if (fromDrawable != null) {
            return@runCatching scaleToMaxEdge(drawableToBitmap(fromDrawable))
        }
        val fromPeek = runCatching { wm.peekDrawable() }.getOrNull()
        fromPeek?.let { scaleToMaxEdge(drawableToBitmap(it)) }
    }.getOrNull()

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
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
