package com.craznail.flashnote.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Fullscreen TYPE_APPLICATION_OVERLAY: dimmed outside + clear crop hole + UI-locked stroke.
 * Uses FLAG_NOT_TOUCHABLE so it does not block underlying interaction.
 *
 * UI lock:
 * - Outside mask #000000 α0.45 → 0x73000000
 * - Stroke #3B82F6 2.5dp + white 1dp α0.7
 * - Corner L 12dp decorative, same blue
 */
class CropSelectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x73000000 // #000000 α0.45
        style = Paint.Style.FILL
    }

    /** Primary selection stroke: #3B82F6 @ 2.5dp */
    private val strokeBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (2.5f * density).coerceAtLeast(2f)
        color = PRIMARY_BLUE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }

    /**
     * White contrast ring α0.7 @ 1dp visible outside the blue stroke
     * (stroke centered on edge → total width = 2.5 + 2×1 = 4.5dp).
     */
    private val strokeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ((2.5f + 2f * 1f) * density).coerceAtLeast(3f)
        color = 0xB3FFFFFF.toInt() // white α0.7
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }

    /** Decorative corner L ticks — same blue, 12dp arms. */
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (2.5f * density).coerceAtLeast(2f)
        color = PRIMARY_BLUE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }

    private val holePath = Path()
    private val selection = Rect()
    private var windowParams: WindowManager.LayoutParams? = null
    private var attached = false
    private var capturingHidden = false

    fun attach(wm: WindowManager) {
        if (attached) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // Below the floating ball in z-order when added first; OverlayService adds this first.
        }
        windowParams = params
        visibility = View.GONE
        wm.addView(this, params)
        attached = true
    }

    fun detach(wm: WindowManager) {
        if (!attached) return
        runCatching { wm.removeView(this) }
        attached = false
        windowParams = null
    }

    fun showSelection(rect: Rect) {
        if (capturingHidden) {
            selection.set(rect)
            return
        }
        selection.set(rect)
        visibility = View.VISIBLE
        invalidate()
    }

    fun updateSelection(rect: Rect) {
        selection.set(rect)
        if (!capturingHidden && visibility != View.VISIBLE) {
            visibility = View.VISIBLE
        }
        invalidate()
    }

    fun hideSelection() {
        visibility = View.GONE
    }

    fun hideForCapture() {
        capturingHidden = true
        visibility = View.GONE
    }

    fun showAfterCapture() {
        if (!capturingHidden) return
        capturingHidden = false
        if (selection.width() > 0 && selection.height() > 0) {
            visibility = View.VISIBLE
            invalidate()
        }
    }

    fun currentSelection(): Rect = Rect(selection)

    override fun onDraw(canvas: Canvas) {
        if (selection.width() <= 0 || selection.height() <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        holePath.reset()
        holePath.fillType = Path.FillType.EVEN_ODD
        holePath.addRect(0f, 0f, w, h, Path.Direction.CW)
        holePath.addRect(RectF(selection), Path.Direction.CCW)
        canvas.drawPath(holePath, dimPaint)
        // White α0.7 outer contrast, then blue 2.5dp primary
        canvas.drawRect(selection, strokeWhitePaint)
        canvas.drawRect(selection, strokeBluePaint)
        // Decorative corner L ticks (12dp), same blue
        val tick = (12 * density).roundToInt()
        val l = selection.left.toFloat()
        val t = selection.top.toFloat()
        val r = selection.right.toFloat()
        val b = selection.bottom.toFloat()
        canvas.drawLine(l, t, l + tick, t, cornerPaint)
        canvas.drawLine(l, t, l, t + tick, cornerPaint)
        canvas.drawLine(r, t, r - tick, t, cornerPaint)
        canvas.drawLine(r, t, r, t + tick, cornerPaint)
        canvas.drawLine(l, b, l + tick, b, cornerPaint)
        canvas.drawLine(l, b, l, b - tick, cornerPaint)
        canvas.drawLine(r, b, r - tick, b, cornerPaint)
        canvas.drawLine(r, b, r, b - tick, cornerPaint)
    }

    companion object {
        const val PRIMARY_BLUE = 0xFF3B82F6.toInt()
    }
}
