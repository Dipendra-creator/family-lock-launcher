package com.familylock.launcher.lock

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.familylock.launcher.Config
import com.familylock.launcher.R

/**
 * Full-screen PIN keypad drawn on top of a blocked app.
 *
 * It uses TYPE_ACCESSIBILITY_OVERLAY, which an accessibility service may show
 * without the "display over other apps" permission. Built with plain Views
 * (not Compose) because it lives in a Service window, not an Activity. The
 * look mirrors the Compose PIN pad: black, dot grid, corner rings, outlined keys.
 */
class PinOverlay(
    private val service: AccessibilityService,
    private val onPinEntered: (String) -> Unit,
    private val onCancel: () -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var reasonView: TextView? = null
    private var dotsRow: LinearLayout? = null
    private var pin = ""

    val isShowing: Boolean get() = root != null

    fun show(reason: String) {
        if (root != null) {
            reasonView?.text = reason
            return
        }
        pin = ""
        val view = buildView(reason)
        val params = WindowManager.LayoutParams(
            MATCH_PARENT, MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Focusable (no FLAG_NOT_FOCUSABLE) so the overlay receives the Back key.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        // Cover the whole display, including the status-bar and cutout areas.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) params.fitInsetsTypes = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        runCatching { wm.addView(view, params) }.onSuccess { root = view }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        reasonView = null
        dotsRow = null
        pin = ""
    }

    // ---- View construction ---------------------------------------------------

    private fun dp(v: Float) = v * service.resources.displayMetrics.density
    private fun dp(v: Int) = dp(v.toFloat()).toInt()

    /** Root view: swallows Back (= cancel) and paints the dot-grid + rings pattern. */
    private inner class PatternFrame : FrameLayout(service) {
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(26, 255, 255, 255) }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }

        init {
            setWillNotDraw(false)
            setBackgroundColor(Color.BLACK)
            isClickable = true // swallow touches so nothing reaches the app underneath
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onCancel()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val step = dp(22f)
            var y = step / 2
            while (y < height) {
                var x = step / 2
                while (x < width) {
                    canvas.drawCircle(x, y, dp(0.75f), dotPaint)
                    x += step
                }
                y += step
            }
            val gap = dp(46f)
            for (i in 1..10) {
                ringPaint.color = Color.argb(((0.16f - i * 0.013f) * 255).toInt(), 255, 255, 255)
                canvas.drawCircle(width.toFloat(), 0f, gap * i, ringPaint)
            }
            for (i in 1..6) {
                ringPaint.color = Color.argb(((0.12f - i * 0.016f) * 255).toInt(), 255, 255, 255)
                canvas.drawCircle(0f, height.toFloat(), gap * i, ringPaint)
            }
        }
    }

    private fun buildView(reason: String): View {
        val frame = PatternFrame()

        val column = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // White padlock inside a thin ring.
        column.addView(
            ImageView(service).apply {
                setImageResource(R.drawable.ic_launcher_foreground)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(1), Color.argb(90, 255, 255, 255))
                }
            },
            LinearLayout.LayoutParams(dp(84), dp(84)),
        )
        column.addView(
            text("FAMILY LOCK", 11f, MUTED).apply {
                letterSpacing = 0.3f
                setPadding(0, dp(18), 0, dp(8))
            }
        )
        column.addView(text("Locked", 28f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
        reasonView = text(reason, 14f, MUTED).apply { setPadding(dp(24), dp(8), dp(24), 0) }
        column.addView(reasonView)
        column.addView(text("Enter the parent PIN", 14f, MUTED).apply { setPadding(0, dp(2), 0, 0) })

        dotsRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(20))
            minimumHeight = dp(55)
        }
        column.addView(dotsRow, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        refreshDots()

        val grid = GridLayout(service).apply { columnCount = 3 }
        for (key in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")) {
            grid.addView(keyButton(key))
        }
        column.addView(grid, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        column.addView(
            text("CANCEL", 13f, MUTED).apply {
                letterSpacing = 0.12f
                setPadding(dp(24), dp(20), dp(24), dp(12))
                setOnClickListener { onCancel() }
            }
        )

        frame.addView(column, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        return frame
    }

    private fun text(value: String, sizeSp: Float, color: Int = Color.WHITE) = TextView(service).apply {
        text = value
        textSize = sizeSp
        gravity = Gravity.CENTER
        setTextColor(color)
    }

    private fun keyButton(key: String): View {
        val isOk = key == "OK"
        return text(key, if (isOk) 16f else 27f, if (isOk) Color.BLACK else Color.WHITE).apply {
            contentDescription = if (key == "⌫") "Delete" else key
            typeface = if (isOk) Typeface.DEFAULT_BOLD else Typeface.create("sans-serif-light", Typeface.NORMAL)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (isOk) setColor(Color.WHITE) else setStroke(dp(1), Color.argb(66, 255, 255, 255))
            }
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(74)
                height = dp(74)
                setMargins(dp(9), dp(9), dp(9), dp(9))
            }
            setOnClickListener { onKey(key) }
        }
    }

    private fun onKey(key: String) {
        when (key) {
            "⌫" -> pin = pin.dropLast(1)
            "OK" -> {
                if (pin.isEmpty()) return
                val entered = pin
                pin = ""
                refreshDots()
                onPinEntered(entered)
                return
            }
            else -> if (pin.length < Config.PIN_MAX_LENGTH) pin += key
        }
        refreshDots()
    }

    /** Filled dots for typed digits, hollow rings for the remaining placeholders. */
    private fun refreshDots() {
        val row = dotsRow ?: return
        row.removeAllViews()
        repeat(maxOf(Config.PIN_MIN_LENGTH, pin.length)) { i ->
            val dot = View(service).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (i < pin.length) setColor(Color.WHITE) else setStroke(dp(1), Color.argb(102, 255, 255, 255))
                }
            }
            row.addView(dot, LinearLayout.LayoutParams(dp(11), dp(11)).apply { setMargins(dp(7), 0, dp(7), 0) })
        }
    }

    private companion object {
        val MUTED = Color.parseColor("#8E8E8E")
    }
}
