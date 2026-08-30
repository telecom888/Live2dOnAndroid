package com.bandori.pet.chat

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** 壁纸文字气泡（悬浮文本，非模型窗口）。开关见设置「壁纸文字气泡」。 */
class WallpaperBubbleService : Service() {
    private var bubbleView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { removeBubble() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra(EXTRA_ACTION, ACTION_SHOW)) {
            ACTION_SHOW -> showBubble(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            ACTION_HIDE -> removeBubble()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideRunnable)
        removeBubble()
        super.onDestroy()
    }

    private fun showBubble(text: String) {
        removeBubble()
        if (text.isBlank()) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((metrics.density * 14).toInt(), (metrics.density * 10).toInt(), (metrics.density * 14).toInt(), (metrics.density * 10).toInt())
            background = GradientDrawable().apply {
                cornerRadius = metrics.density * 18
                setColor(Color.argb(220, 24, 24, 24))
            }
            elevation = metrics.density * 8
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (metrics.heightPixels * 0.18f).toInt()
            horizontalMargin = metrics.density * 8
        }
        runCatching { wm.addView(tv, params) }
        bubbleView = tv
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, BUBBLE_DURATION_MS)
    }

    private fun removeBubble() {
        handler.removeCallbacks(hideRunnable)
        val view = bubbleView ?: return
        bubbleView = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view) }
    }

    companion object {
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_TEXT = "text"
        private const val ACTION_SHOW = 1
        private const val ACTION_HIDE = 2
        private const val BUBBLE_DURATION_MS = 6_000L

        fun show(context: Context, text: String) {
            runCatching {
                context.startService(
                    Intent(context, WallpaperBubbleService::class.java)
                        .putExtra(EXTRA_ACTION, ACTION_SHOW)
                        .putExtra(EXTRA_TEXT, text),
                )
            }
        }

        fun hide(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, WallpaperBubbleService::class.java)
                        .putExtra(EXTRA_ACTION, ACTION_HIDE),
                )
            }
        }
    }
}
