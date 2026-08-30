package com.bangdream.pet.wallpaper

import android.graphics.RectF
import com.bangdream.pet.live2d.Live2DTransform
import com.bangdream.pet.live2d.PreparedModel
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * 计算模型在壁纸 surface 上的显示区域（像素矩形），用于触摸命中检测。
 *
 * 投影公式与 third_party/Live2D-v2-Lua/live2d_moc3_pet_embed.lua 保持一致：
 * - model_width  = canvas.width  / canvas.pixels_per_unit（画布单位）
 * - model_height = canvas.height / canvas.pixels_per_unit
 * - fitScale     = min(surfaceW / model_width, surfaceH / model_height) * 用户缩放
 * - 模型中心 NDC = (offsetX, offsetY)，映射回屏幕像素
 */
object WallpaperHitArea {

    data class ModelCanvas(
        val width: Float,
        val height: Float,
        val pixelsPerUnit: Float,
    )

    /** 命中区域在画布矩形基础上外扩的比例，避免模型边缘点不到。 */
    private const val HIT_PADDING_RATIO = 0.05f

    /** 从已准备模型里找到 .moc3 文件并解析画布信息；读不到返回 null（调用方回退默认矩形）。 */
    fun resolveCanvas(prepared: PreparedModel): ModelCanvas? {
        val bytes = findMoc3Bytes(prepared) ?: return null
        return parseMoc3(bytes)
    }

    private fun findMoc3Bytes(prepared: PreparedModel): ByteArray? {
        if (prepared.resourceBytes.isNotEmpty()) {
            val index = prepared.resourcePaths.indexOfFirst {
                it.substringAfterLast('/').endsWith(".moc3", ignoreCase = true)
            }
            if (index >= 0 && index < prepared.resourceBytes.size) {
                return prepared.resourceBytes[index]
            }
        }
        val modelFile = File(prepared.modelPath)
        val dir = if (modelFile.isDirectory) modelFile else modelFile.parentFile
        dir ?: return null
        if (!dir.exists()) return null
        return dir.walkTopDown()
            .firstOrNull { it.isFile && it.name.endsWith(".moc3", ignoreCase = true) }
            ?.readBytes()
    }

    /** 解析 MOC3 二进制里的 CanvasInfo（与 live2d/cubism3/moc3/canvas.lua 一致）。 */
    fun parseMoc3(bytes: ByteArray): ModelCanvas? {
        if (bytes.size < 64) return null
        if (
            bytes[0] != 'M'.code.toByte() ||
            bytes[1] != 'O'.code.toByte() ||
            bytes[2] != 'C'.code.toByte() ||
            bytes[3] != '3'.code.toByte()
        ) return null
        val bigEndian = bytes[5].toInt() == 1
        val canvasInfoOffset = readU32(bytes, 64 + 1 * 4, bigEndian)
        if (canvasInfoOffset < 0 || canvasInfoOffset + 20 > bytes.size) return null
        val pixelsPerUnit = readF32(bytes, canvasInfoOffset, bigEndian)
        val width = readF32(bytes, canvasInfoOffset + 3 * 4, bigEndian)
        val height = readF32(bytes, canvasInfoOffset + 4 * 4, bigEndian)
        if (width <= 0f || height <= 0f || pixelsPerUnit == 0f) return null
        return ModelCanvas(width = abs(width), height = abs(height), pixelsPerUnit = abs(pixelsPerUnit))
    }

    /**
     * 计算模型在壁纸 surface 上的像素矩形。
     * canvas 为 null 时使用默认近似（宽占屏幕 45%，3:4 竖版），保证 v2 模型也有可点击区域。
     */
    fun computeRect(
        surfaceWidth: Int,
        surfaceHeight: Int,
        transform: Live2DTransform,
        canvas: ModelCanvas?,
    ): RectF {
        val w = surfaceWidth.coerceAtLeast(1).toFloat()
        val h = surfaceHeight.coerceAtLeast(1).toFloat()
        val modelWUnits: Float
        val modelHUnits: Float
        if (canvas != null) {
            modelWUnits = canvas.width / canvas.pixelsPerUnit
            modelHUnits = canvas.height / canvas.pixelsPerUnit
        } else {
            modelWUnits = w * 0.45f
            modelHUnits = modelWUnits * 4f / 3f
        }
        val fitScale = min(w / modelWUnits, h / modelHUnits) * transform.scale
        val modelWPx = modelWUnits * fitScale
        val modelHPx = modelHUnits * fitScale
        val centerX = (transform.offsetX + 1f) / 2f * w
        val centerY = (1f - (transform.offsetY + 1f) / 2f) * h
        val padX = modelWPx * HIT_PADDING_RATIO
        val padY = modelHPx * HIT_PADDING_RATIO
        return RectF(
            centerX - modelWPx / 2f - padX,
            centerY - modelHPx / 2f - padY,
            centerX + modelWPx / 2f + padX,
            centerY + modelHPx / 2f + padY,
        )
    }

    private fun readU32(bytes: ByteArray, offset: Int, bigEndian: Boolean): Int {
        if (offset < 0 || offset + 4 > bytes.size) return -1
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        return if (bigEndian) {
            (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        } else {
            b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }
    }

    private fun readF32(bytes: ByteArray, offset: Int, bigEndian: Boolean): Float {
        if (offset < 0 || offset + 4 > bytes.size) return 0f
        return Float.fromBits(readU32(bytes, offset, bigEndian))
    }
}
