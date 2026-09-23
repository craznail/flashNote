package com.craznail.flashnote.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.craznail.flashnote.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class CaptureSelectionBounds(
    val top: Int,
    val bottom: Int
) {
    val height: Int get() = bottom - top
}

/**
 * Modal full-screen overlay used by screenshot selection mode.
 *
 * The selection always spans the full display width. Users can move the whole selection
 * vertically by dragging inside it and can fine tune the top/bottom edges with matching
 * blue/white handles.
 */
internal class CaptureSelectionWindow(
    context: Context,
    private val windowManager: WindowManager,
    overlayType: Int,
    private val initialAnchor: CaptureSelectionAnchor,
    private val onConfirm: (CaptureSelectionBounds) -> Unit,
    private val onCancel: () -> Unit
) {
    private val root = SelectionView(context)
    private var attached = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayType,
        OverlayCapturePolicy.captureSafeFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        ),
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 0
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    init {
        root.onConfirm = onConfirm
        root.onCancel = onCancel
    }

    fun show() {
        if (attached) return
        windowManager.addView(root, params)
        attached = true
        root.configureInitialAnchor(initialAnchor)
        root.isFocusableInTouchMode = true
        root.requestFocus()
    }

    fun close(animated: Boolean = true, onClosed: () -> Unit = {}) {
        if (!attached) {
            onClosed()
            return
        }
        root.isEnabled = false
        root.animate().cancel()
        if (!animated) {
            detach()
            onClosed()
            return
        }
        root.animate()
            .alpha(0f)
            .setDuration(CaptureSelectionMotion.EXIT_DURATION_MS)
            .setInterpolator(PathInterpolator(0.4f, 0f, 1f, 1f))
            .withEndAction {
                detach()
                onClosed()
            }
            .start()
    }

    private fun detach() {
        if (!attached) return
        attached = false
        runCatching { windowManager.removeView(root) }
    }

    fun setHidden(hidden: Boolean) {
        if (!attached) return
        root.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    private class SelectionView(context: Context) : View(context) {
        var onConfirm: ((CaptureSelectionBounds) -> Unit)? = null
        var onCancel: (() -> Unit)? = null

        private val density = resources.displayMetrics.density
        private val touchSlop = 8f * density
        private val minSelectionHeight = 96f * density
        private val edgeInset = 10f * density
        private val blue = Color.rgb(69, 160, 255)

        private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(126, 9, 24, 43)
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blue
            strokeWidth = 1.25f * density
            style = Paint.Style.STROKE
            setShadowLayer(4f * density, 0f, 0f, Color.argb(230, 24, 137, 255))
        }
        private val borderCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 239, 249, 255)
            strokeWidth = 0.55f * density
            style = Paint.Style.STROKE
        }
        private val controlFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(246, 235, 247, 255)
            style = Paint.Style.FILL
        }
        private val controlStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blue
            strokeWidth = 1f * density
            style = Paint.Style.STROKE
        }
        private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blue
            strokeWidth = 1.15f * density
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        private val captureGlass: Drawable? =
            ContextCompat.getDrawable(context, R.drawable.bg_ball_glass)?.mutate()
        private val captureIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_menu_capture)
            ?.mutate()
            ?.also { DrawableCompat.setTint(it, Color.rgb(20, 111, 239)) }

        private var selectionTop = 0f
        private var selectionBottom = 0f
        private var captureAnchor: CaptureSelectionAnchor? = null
        private var activeTarget = TouchTarget.NONE
        private var downX = 0f
        private var downY = 0f
        private var startTop = 0f
        private var startBottom = 0f
        private var moved = false

        private val topHandleRect = RectF()
        private val bottomHandleRect = RectF()
        private val captureRect = RectF()
        private val handler = Handler(Looper.getMainLooper())
        private val inactivityRunnable = Runnable { onCancel?.invoke() }
        private val backCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OnBackInvokedCallback { onCancel?.invoke() }
        } else null
        private var registeredBackDispatcher: OnBackInvokedDispatcher? = null

        init {
            isClickable = true
            isFocusable = true
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onCancel?.invoke()
                    true
                } else {
                    false
                }
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val dispatcher = findOnBackInvokedDispatcher()
                backCallback?.let { callback ->
                    dispatcher?.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        callback
                    )
                }
                registeredBackDispatcher = dispatcher
            }
            resetInactivityTimer()
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(inactivityRunnable)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                backCallback?.let { callback ->
                    registeredBackDispatcher?.unregisterOnBackInvokedCallback(callback)
                }
                registeredBackDispatcher = null
            }
            super.onDetachedFromWindow()
        }

        fun configureInitialAnchor(anchor: CaptureSelectionAnchor) {
            captureAnchor = anchor
            if (height > 0) initializeSelection()
            resetInactivityTimer()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            initializeSelection()
        }

        private fun initializeSelection() {
            if (height <= 0) return
            val desiredHeight = max(240f * density, height * 0.36f).coerceAtMost(height * 0.62f)
            val desiredCenter = captureAnchor?.centerY?.toFloat()?.coerceIn(
                desiredHeight / 2f + edgeInset,
                height - desiredHeight / 2f - edgeInset
            ) ?: height * 0.46f
            selectionTop = desiredCenter - desiredHeight / 2f
            selectionBottom = desiredCenter + desiredHeight / 2f
            updateControlRects()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0 || selectionBottom <= selectionTop) return

            canvas.drawRect(0f, 0f, width.toFloat(), selectionTop, dimPaint)
            canvas.drawRect(0f, selectionBottom, width.toFloat(), height.toFloat(), dimPaint)
            canvas.drawLine(0f, selectionTop, width.toFloat(), selectionTop, borderPaint)
            canvas.drawLine(0f, selectionBottom, width.toFloat(), selectionBottom, borderPaint)
            canvas.drawLine(0f, selectionTop, width.toFloat(), selectionTop, borderCorePaint)
            canvas.drawLine(0f, selectionBottom, width.toFloat(), selectionBottom, borderCorePaint)

            updateControlRects()
            drawSecondaryControl(canvas, topHandleRect)
            drawHandleGlyph(canvas, topHandleRect)
            drawSecondaryControl(canvas, bottomHandleRect)
            drawHandleGlyph(canvas, bottomHandleRect)
            drawCaptureControl(canvas)
        }

        private fun drawSecondaryControl(canvas: Canvas, rect: RectF) {
            val radius = min(rect.width(), rect.height()) / 2f
            controlFillPaint.setShadowLayer(
                4f * density,
                0f,
                0f,
                Color.argb(150, 37, 145, 255)
            )
            canvas.drawRoundRect(rect, radius, radius, controlFillPaint)
            controlFillPaint.clearShadowLayer()
            canvas.drawRoundRect(rect, radius, radius, controlStrokePaint)
        }

        private fun drawHandleGlyph(canvas: Canvas, rect: RectF) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val half = 5f * density
            val gap = 1.6f * density
            canvas.drawLine(cx - half, cy - gap, cx + half, cy - gap, glyphPaint)
            canvas.drawLine(cx - half, cy + gap, cx + half, cy + gap, glyphPaint)
        }

        private fun drawCaptureControl(canvas: Canvas) {
            val left = captureRect.left.roundToInt()
            val top = captureRect.top.roundToInt()
            val right = captureRect.right.roundToInt()
            val bottom = captureRect.bottom.roundToInt()
            captureGlass?.setBounds(left, top, right, bottom)
            captureGlass?.draw(canvas)

            val iconSize = (captureRect.width() * 0.43f).roundToInt()
            val iconLeft = (captureRect.centerX() - iconSize / 2f).roundToInt()
            val iconTop = (captureRect.centerY() - iconSize / 2f).roundToInt()
            captureIcon?.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            captureIcon?.draw(canvas)
        }

        private fun updateControlRects() {
            if (width <= 0 || height <= 0) return
            CaptureSelectionDesign.handleBounds(width, selectionTop, density).also { bounds ->
                topHandleRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
            CaptureSelectionDesign.handleBounds(width, selectionBottom, density).also { bounds ->
                bottomHandleRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }

            val anchor = captureAnchor ?: return
            val screenBounds = CaptureSelectionDesign.captureButtonBounds(anchor)
            val location = IntArray(2)
            getLocationOnScreen(location)
            captureRect.set(
                screenBounds.left - location[0],
                screenBounds.top - location[1],
                screenBounds.right - location[0],
                screenBounds.bottom - location[1]
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            resetInactivityTimer()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    startTop = selectionTop
                    startBottom = selectionBottom
                    moved = false
                    activeTarget = hitTarget(event.x, event.y)
                    return activeTarget != TouchTarget.NONE
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - downY
                    if (abs(event.x - downX) > touchSlop || abs(dy) > touchSlop) moved = true
                    when (activeTarget) {
                        TouchTarget.TOP_HANDLE -> {
                            selectionTop = (startTop + dy).coerceIn(
                                edgeInset,
                                selectionBottom - minSelectionHeight
                            )
                            invalidate()
                        }
                        TouchTarget.BOTTOM_HANDLE -> {
                            selectionBottom = (startBottom + dy).coerceIn(
                                selectionTop + minSelectionHeight,
                                height - edgeInset
                            )
                            invalidate()
                        }
                        TouchTarget.MOVE_SELECTION -> if (moved) {
                            moveSelectionBy(dy)
                            invalidate()
                        }
                        else -> Unit
                    }
                    return activeTarget != TouchTarget.NONE
                }

                MotionEvent.ACTION_UP -> {
                    val target = activeTarget
                    activeTarget = TouchTarget.NONE
                    when (target) {
                        TouchTarget.OUTSIDE -> if (!moved) {
                            onCancel?.invoke()
                        }
                        TouchTarget.CAPTURE -> if (!moved) {
                            onConfirm?.invoke(boundsOnScreen())
                        }
                        else -> Unit
                    }
                    return target != TouchTarget.NONE
                }

                MotionEvent.ACTION_CANCEL -> {
                    activeTarget = TouchTarget.NONE
                    return true
                }
            }
            return false
        }

        private fun hitTarget(x: Float, y: Float): TouchTarget {
            updateControlRects()
            val hitPad = 10f * density
            return when {
                expanded(topHandleRect, hitPad).contains(x, y) -> TouchTarget.TOP_HANDLE
                expanded(bottomHandleRect, hitPad).contains(x, y) -> TouchTarget.BOTTOM_HANDLE
                expanded(captureRect, 6f * density).contains(x, y) -> TouchTarget.CAPTURE
                y in selectionTop..selectionBottom -> TouchTarget.MOVE_SELECTION
                else -> TouchTarget.OUTSIDE
            }
        }

        private fun resetInactivityTimer() {
            handler.removeCallbacks(inactivityRunnable)
            handler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS)
        }

        private fun moveSelectionBy(dy: Float) {
            val selectionHeight = startBottom - startTop
            var top = startTop + dy
            top = top.coerceIn(edgeInset, height - edgeInset - selectionHeight)
            selectionTop = top
            selectionBottom = top + selectionHeight
        }

        private fun boundsOnScreen(): CaptureSelectionBounds {
            val location = IntArray(2)
            getLocationOnScreen(location)
            val top = (location[1] + selectionTop).roundToInt()
            val bottom = (location[1] + selectionBottom).roundToInt()
            return CaptureSelectionBounds(top = top, bottom = bottom)
        }

        private fun expanded(rect: RectF, amount: Float): RectF = RectF(rect).apply {
            inset(-amount, -amount)
        }

        private enum class TouchTarget {
            NONE,
            OUTSIDE,
            TOP_HANDLE,
            BOTTOM_HANDLE,
            MOVE_SELECTION,
            CAPTURE
        }

        companion object {
            private const val INACTIVITY_TIMEOUT_MS = 10_000L
        }
    }
}
