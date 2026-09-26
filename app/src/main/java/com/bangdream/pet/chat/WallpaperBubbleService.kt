package com.bangdream.pet.chat

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
import android.widget.LinearLayout
import com.bangdream.pet.loadBubbleDurationSeconds

/** 壁纸文字气泡（悬浮文本，非模型窗口）。开关见设置「壁纸文字气泡」。 */
class WallpaperBubbleService : Service() {
    private var bubbleView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { removeBubble() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra(EXTRA_ACTION, ACTION_SHOW)) {
            ACTION_SHOW -> showBubble(
                intent.getStringArrayListExtra(EXTRA_PARTS) ?: listOf(intent.getStringExtra(EXTRA_TEXT).orEmpty()),
                intent.getIntExtra(EXTRA_INTERVAL, 0).coerceIn(0, 10000),
            )
            ACTION_HIDE -> removeBubble()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideRunnable)
        removeBubble()
        super.onDestroy()
    }

    private fun showBubble(parts: List<String>, intervalMs: Int) {
        removeBubble()
        val messages = parts.filter(String::isNotBlank)
        if (messages.isEmpty()) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun addPart(text: String) {
            val tv = TextView(this).apply {
                this.text = text
                maxWidth = metrics.widthPixels - (metrics.density * 32).toInt()
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
            root.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (metrics.density * 5).toInt()
            })
        }
        addPart(messages.first())
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
        if (runCatching { wm.addView(root, params) }.isFailure) return
        bubbleView = root
        messages.drop(1).forEachIndexed { index, text ->
            if (intervalMs == 0) addPart(text)
            else handler.postDelayed({ addPart(text) }, (index + 1) * intervalMs.toLong())
        }
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, (messages.size - 1) * intervalMs.toLong() + loadBubbleDurationSeconds(this) * 1000L)
    }

    private fun removeBubble() {
        handler.removeCallbacksAndMessages(null)
        val view = bubbleView ?: return
        bubbleView = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view) }
    }

    companion object {
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_PARTS = "parts"
        private const val EXTRA_INTERVAL = "interval"
        private const val ACTION_SHOW = 1
        private const val ACTION_HIDE = 2

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

        fun show(context: Context, parts: List<String>, intervalMs: Int) {
            runCatching {
                context.startService(
                    Intent(context, WallpaperBubbleService::class.java)
                        .putExtra(EXTRA_ACTION, ACTION_SHOW)
                        .putStringArrayListExtra(EXTRA_PARTS, ArrayList(parts))
                        .putExtra(EXTRA_INTERVAL, intervalMs),
                )
            }
        }
    }
}
