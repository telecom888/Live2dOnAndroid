package com.bangdream.pet.live2d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Surface
import com.bangdream.pet.I18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object NativeLive2D {
    private const val MAX_BACKGROUND_EDGE = 2048

    init {
        System.loadLibrary("bangdreampet")
    }

    external fun create(
        surface: Surface,
        runtimeRoot: String,
        width: Int,
        height: Int,
        fpsLimit: Int,
        vsyncEnabled: Boolean,
        renderScale: Float,
    ): Long
    external fun resize(handle: Long, width: Int, height: Int)
    external fun loadModel(handle: Long, modelPath: String, resourcePaths: Array<String>, resourceBytes: Array<ByteArray>): Boolean
    /** 多角色壁纸：在指定 slot 加载模型（slot 0..n），可同时放置多个模型。 */
    external fun loadModelAt(handle: Long, slot: Int, modelPath: String, resourcePaths: Array<String>, resourceBytes: Array<ByteArray>): Boolean
    /** 多角色壁纸：卸载指定 slot 的模型实例（释放 GL 资源，不影响其它 slot）。 */
    external fun unloadModelAt(handle: Long, slot: Int)
    external fun setRenderOptions(handle: Long, fpsLimit: Int, vsyncEnabled: Boolean)
    external fun setPaused(handle: Long, paused: Boolean)
    /** surface 被系统销毁重建后，把渲染器重新绑定到新 Surface（保留模型/动作状态）。 */
    external fun attachSurface(handle: Long, surface: Surface): Boolean
    external fun setRenderScale(handle: Long, scale: Float)
    external fun setFpsDisplayEnabled(handle: Long, enabled: Boolean)
    external fun setTransform(handle: Long, offsetX: Float, offsetY: Float, scale: Float)
    external fun setTransformAt(handle: Long, slot: Int, offsetX: Float, offsetY: Float, scale: Float)
    external fun setBackgroundPixels(handle: Long, pixels: IntArray?, width: Int, height: Int)
    external fun touch(handle: Long, x: Float, y: Float)
    external fun touchAt(handle: Long, slot: Int, x: Float, y: Float)
    external fun lookAt(handle: Long, x: Float, y: Float)
    external fun playAction(handle: Long, tag: String)
    external fun playActionAt(handle: Long, slot: Int, tag: String)
    external fun setLipSync(handle: Long, open: Float, form: Float)
    external fun lastError(handle: Long): String
    external fun destroy(handle: Long)

    suspend fun loadBackground(context: Context, uri: String?): BackgroundPixels? {
        if (uri.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeBackground(context, Uri.parse(uri))
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    BackgroundPixels(pixels, bitmap.width, bitmap.height)
                } finally {
                    bitmap.recycle()
                }
            }.getOrNull()
        }
    }

    fun setBackground(handle: Long, background: BackgroundPixels?) {
        if (handle == 0L) return
        if (background != null) {
            setBackgroundPixels(handle, background.pixels, background.width, background.height)
        } else {
            setBackgroundPixels(handle, null, 0, 0)
        }
    }

    data class BackgroundPixels(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
    )

    private fun decodeBackground(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: error(I18n.t("error_read_background"))
        return scaleToMaxEdge(decoded)
    }

    private fun computeSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var edge = max(width, height)
        while (edge / sampleSize > MAX_BACKGROUND_EDGE) sampleSize *= 2
        return sampleSize
    }

    private fun scaleToMaxEdge(bitmap: Bitmap): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= MAX_BACKGROUND_EDGE) return bitmap
        val scale = MAX_BACKGROUND_EDGE.toFloat() / edge.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        bitmap.recycle()
        return scaled
    }
}
