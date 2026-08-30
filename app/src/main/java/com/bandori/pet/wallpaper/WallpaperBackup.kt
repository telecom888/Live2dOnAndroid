package com.bandori.pet.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bandori.pet.loadWallpaperOriginalBackupPath
import com.bandori.pet.saveWallpaperOriginalBackupPath
import com.bandori.pet.saveWallpaperBackgroundUri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** 保留系统原壁纸：启用动态壁纸前捕获当前静态壁纸，作为背景层绘制；提供恢复入口。 */
object WallpaperBackup {
    private const val MAX_EDGE = 2048

    /** 捕获当前系统壁纸，保存到应用私有目录，并设为壁纸背景 URI。返回 uri。 */
    fun captureAndUseAsBackground(context: Context): String? {
        val drawable = runCatching {
            WallpaperManager.getInstance(context).drawable
        }.getOrNull() ?: return null

        val bitmap = runCatching {
            val src = when (drawable) {
                is android.graphics.drawable.BitmapDrawable -> drawable.bitmap
                else -> {
                    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080
                    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(canvas)
                    bmp
                }
            }
            scaleToMaxEdge(src)
        }.getOrNull() ?: return null

        val file = File(context.filesDir, "wallpaper_backup.png")
        runCatching {
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 90, out) }
        }.getOrNull() ?: run { bitmap.recycle(); return null }
        bitmap.recycle()

        saveWallpaperOriginalBackupPath(context, file.absolutePath)
        val uri = "file://" + file.absolutePath
        saveWallpaperBackgroundUri(context, uri)
        return uri
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
