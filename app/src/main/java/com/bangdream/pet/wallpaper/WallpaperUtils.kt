package com.bangdream.pet.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 壁纸读取工具，移植自「逸风工具箱」的 WallpaperUtils（l7.n）：
 * - HOME：WallpaperManager.getDrawable() -> BitmapDrawable.getBitmap()
 * - LOCK：WallpaperManager.getWallpaperFile(FLAG_LOCK) -> decodeFileDescriptor，
 *   失败再退回 getDrawable(FLAG_LOCK)
 *
 * Android 13+ 系统限制普通应用读取真实壁纸，需要「所有文件访问」
 * （MANAGE_EXTERNAL_STORAGE）权限（逸风工具箱同款方案）。
 */
enum class WallpaperTarget { HOME, LOCK }

object WallpaperUtils {
    private const val TAG = "WallpaperUtils"

    /** 读取当前壁纸位图；返回 null 表示无法获取（动态壁纸 / 系统限制 / 未单独设置）。 */
    fun readBitmap(wm: WallpaperManager, target: WallpaperTarget): Bitmap? = runCatching {
        when (target) {
            WallpaperTarget.HOME -> {
                val drawable = wm.drawable
                (drawable as? BitmapDrawable)?.bitmap
            }
            WallpaperTarget.LOCK -> readLockWallpaper(wm)
        }
    }.onFailure { e ->
        Log.w(TAG, "readBitmap($target) failed", e)
    }.getOrNull()

    private fun readLockWallpaper(wm: WallpaperManager): Bitmap? {
        // 锁屏壁纸可能单独设置，优先走文件描述符（FLAG_LOCK = 2）
        val pfd = try {
            wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)
        } catch (e: Exception) {
            Log.w(TAG, "getWallpaperFile(FLAG_LOCK) failed", e)
            null
        }
        if (pfd != null) {
            return pfd.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor) }
        }
        val drawable = try {
            wm.getDrawable(WallpaperManager.FLAG_LOCK)
        } catch (e: Exception) {
            Log.w(TAG, "getDrawable(FLAG_LOCK) failed", e)
            null
        }
        return (drawable as? BitmapDrawable)?.bitmap
    }

    /**
     * Android 13+ 只有授予「所有文件访问」权限后才能读到真实壁纸；
     * 在此之前 getDrawable() 返回的是默认壁纸，捕获结果无意义。
     */
    fun canReadRealWallpaper(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return hasAllFilesAccess(context)
    }

    /** 是否已授予「所有文件访问」（Android 11+），低版本回退到存储权限。 */
    fun hasAllFilesAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        val read = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        return read && write
    }

    /** 打开系统「所有文件访问」授权页（Android 11+），返回是否成功拉起。 */
    fun openAllFilesAccessSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 把 Drawable 转成 Bitmap（非 BitmapDrawable 时自绘到画布，兜底）。 */
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }
}
