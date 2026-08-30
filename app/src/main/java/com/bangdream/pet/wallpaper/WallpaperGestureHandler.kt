package com.bangdream.pet.wallpaper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import kotlin.math.hypot

/**
 * 壁纸手势仲裁：
 * - 按下（DOWN）→ onDown（用于视线跟随等全局响应）
 * - 滑动（位移超过阈值，抚摸）→ onSwipe
 * - 短按抬起（无位移）→ 双击判定，无第二次则 onTap
 * - 按住超过阈值未移动 → onLongPress（携带按下坐标）
 * - 移动过程中持续 onMove（视线跟随）
 */
class WallpaperGestureHandler(
    context: Context,
    private val onDown: (x: Float, y: Float) -> Unit,
    private val onTap: (x: Float, y: Float) -> Unit,
    private val onSwipe: (x: Float, y: Float) -> Unit,
    private val onDoubleTap: (x: Float, y: Float) -> Unit,
    private val onLongPress: (x: Float, y: Float) -> Unit,
    private val onMove: (x: Float, y: Float) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val slopPx = (24 * context.resources.displayMetrics.density).toFloat()
    private val longPressMs = 600L
    private val doubleTapWindowMs = 300L

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var swipeConsumed = false
    private var longPressFired = false
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var lastTapTime = 0L
    private var pendingSingleTap: Runnable? = null

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                swipeConsumed = false
                longPressFired = false
                handler.postDelayed(longPressRunnable, longPressMs)
                onDown(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!swipeConsumed && hypot(dx, dy) >= slopPx) {
                    swipeConsumed = true
                    handler.removeCallbacks(longPressRunnable)
                    pendingSingleTap?.let { handler.removeCallbacks(it) }
                    pendingSingleTap = null
                    onSwipe(event.x, event.y)
                } else if (swipeConsumed) {
                    onMove(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (swipeConsumed) {
                    swipeConsumed = false
                    return
                }
                if (longPressFired) {
                    longPressFired = false
                    return
                }
                val now = event.eventTime
                val isDouble = now - lastTapTime <= doubleTapWindowMs &&
                    hypot(event.x - lastTapX, event.y - lastTapY) <= slopPx * 2
                if (isDouble) {
                    pendingSingleTap?.let { handler.removeCallbacks(it) }
                    pendingSingleTap = null
                    onDoubleTap(event.x, event.y)
                    lastTapTime = 0L
                } else {
                    lastTapX = event.x
                    lastTapY = event.y
                    lastTapTime = now
                    val runnable = Runnable { onTap(event.x, event.y) }
                    pendingSingleTap = runnable
                    handler.postDelayed(runnable, doubleTapWindowMs)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                pendingSingleTap?.let { handler.removeCallbacks(it) }
                pendingSingleTap = null
                swipeConsumed = false
            }
        }
    }

    private val longPressRunnable = Runnable {
        if (!swipeConsumed) {
            longPressFired = true
            onLongPress(downX, downY)
        }
    }

    fun destroy() {
        handler.removeCallbacks(longPressRunnable)
        pendingSingleTap?.let { handler.removeCallbacks(it) }
    }
}
