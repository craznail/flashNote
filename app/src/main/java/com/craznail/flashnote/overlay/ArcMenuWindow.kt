package com.craznail.flashnote.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ArcMenuItem(
    val iconRes: Int,
    val labelRes: Int,
    val tintColor: Int? = null,
    val dismissOnClick: Boolean = true,
    val onClick: () -> Unit
)

/** A dedicated overlay window for the radial menu; it never resizes or moves the ball window. */
internal class ArcMenuWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    overlayType: Int,
    private val dockLeft: Boolean,
    anchorScreenX: Int,
    anchorScreenY: Int,
    items: List<ArcMenuItem>,
    private val onDismissed: () -> Unit
) {
    private val density = context.resources.displayMetrics.density
    private val buttonSizePx = (ArcMenuDesign.buttonSizeDp * density).roundToInt()
    private val radiusPx = ArcMenuDesign.radiusDp * density
    private val reachPx = (radiusPx + buttonSizePx / 2f + 8 * density).roundToInt()
    private val ballTouchRadiusPx = (ArcMenuDesign.ballTouchSizeDp * density / 2f).roundToInt()
    private val horizontalLayout = ArcMenuGeometry.windowLayout(
        dockLeft = dockLeft,
        anchorScreenX = anchorScreenX,
        reach = reachPx,
        ballTouchRadius = ballTouchRadiusPx
    )
    private val windowWidthPx = horizontalLayout.width
    private val windowHeightPx = reachPx * 2
    private val anchorX = horizontalLayout.anchorX
    private val anchorY = windowHeightPx / 2f
    private val root = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(Color.TRANSPARENT)
    }
    private val params = WindowManager.LayoutParams(
        windowWidthPx,
        windowHeightPx,
        overlayType,
        OverlayCapturePolicy.captureSafeFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ),
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = horizontalLayout.screenX
        y = anchorScreenY - windowHeightPx / 2
    }
    private val buttons: List<ImageView>
    private val handler = Handler(Looper.getMainLooper())
    private val idleDeadline = ArcMenuIdleDeadline(ArcMenuDesign.idleTimeoutMs)
    private var idleCloseRunnable: Runnable? = null
    private var attached = false
    private var closing = false
    private var removed = false

    init {
        require(items.isNotEmpty() && items.size <= 4)
        val centers = ArcMenuGeometry.itemCenters(
            dockLeft = dockLeft,
            anchorX = anchorX,
            anchorY = anchorY,
            radius = radiusPx
        ).take(items.size)

        buttons = items.zip(centers).map { (item, center) ->
            ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(buttonSizePx, buttonSizePx).apply {
                    gravity = Gravity.TOP or Gravity.START
                    leftMargin = (center.x - buttonSizePx / 2f).roundToInt()
                    topMargin = (center.y - buttonSizePx / 2f).roundToInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(
                        ArcMenuPalette.highlightArgb,
                        ArcMenuPalette.fillArgb,
                        ArcMenuPalette.edgeArgb
                    )
                    gradientRadius = buttonSizePx * 0.72f
                    setGradientCenter(0.42f, 0.36f)
                    setStroke((density).roundToInt().coerceAtLeast(1), ArcMenuPalette.strokeArgb)
                }
                val padding = (9 * density).roundToInt()
                setPadding(padding, padding, padding, padding)
                setImageResource(item.iconRes)
                setColorFilter(item.tintColor ?: ArcMenuPalette.iconArgb)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = context.getString(item.labelRes)
                elevation = 4 * density
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (item.dismissOnClick) {
                        close(animated = true, afterClosed = item.onClick)
                    } else {
                        item.onClick()
                        scheduleAutoClose()
                    }
                }
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> cancelAutoClose()
                        MotionEvent.ACTION_CANCEL -> scheduleAutoClose()
                    }
                    false
                }
                root.addView(this)
            }
        }

        // The window now overlaps the ball (top/bottom items sit above/below it), so a
        // tap on empty space — including the ball area — dismisses the menu.
        var downX = 0f
        var downY = 0f
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    cancelAutoClose()
                    downX = event.x
                    downY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val slop = 8 * density
                    if (abs(event.x - downX) <= slop && abs(event.y - downY) <= slop) {
                        close(animated = true)
                    } else {
                        scheduleAutoClose()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    scheduleAutoClose()
                    true
                }
                else -> true
            }
        }
    }

    fun show() {
        if (attached) return
        windowManager.addView(root, params)
        attached = true
        root.animate().cancel()
        root.alpha = 1f
        buttons.forEachIndexed { index, button ->
            val layout = button.layoutParams as FrameLayout.LayoutParams
            val centerX = layout.leftMargin + buttonSizePx / 2f
            val centerY = layout.topMargin + buttonSizePx / 2f
            button.alpha = 0.18f
            button.scaleX = ArcMenuDesign.startScale
            button.scaleY = ArcMenuDesign.startScale
            button.translationX = anchorX - centerX
            button.translationY = anchorY - centerY
            button.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(ArcMenuDesign.openDurationMs)
                .setStartDelay(index * ArcMenuDesign.openStaggerMs)
                .setInterpolator(OvershootInterpolator(0.85f))
                .start()
        }
        scheduleAutoClose(
            ArcMenuDesign.openTotalDuration(buttons.size) + ArcMenuDesign.idleTimeoutMs
        )
    }

    fun close(animated: Boolean, afterClosed: (() -> Unit)? = null) {
        if (removed) return
        cancelAutoClose()
        if (closing) {
            if (!animated) removeWindow(afterClosed)
            return
        }
        closing = true
        if (!attached || !animated) {
            removeWindow(afterClosed)
            return
        }

        root.animate().cancel()
        root.animate()
            .alpha(0f)
            .setDuration(ArcMenuDesign.closeTotalDuration(buttons.size))
            .setInterpolator(EXIT_EASING)
            .start()

        var pending = buttons.size
        buttons.forEachIndexed { index, button ->
            val layout = button.layoutParams as FrameLayout.LayoutParams
            val centerX = layout.leftMargin + buttonSizePx / 2f
            val centerY = layout.topMargin + buttonSizePx / 2f
            button.animate().cancel()
            button.animate()
                .scaleX(ArcMenuDesign.startScale)
                .scaleY(ArcMenuDesign.startScale)
                .translationX(anchorX - centerX)
                .translationY(anchorY - centerY)
                .setDuration(ArcMenuDesign.closeDurationMs)
                .setStartDelay((buttons.lastIndex - index) * ArcMenuDesign.closeStaggerMs)
                .setInterpolator(FastOutSlowInInterpolator())
                .withEndAction {
                    pending--
                    if (pending == 0) removeWindow(afterClosed)
                }
                .start()
        }
    }

    private fun removeWindow(afterClosed: (() -> Unit)?) {
        if (removed) return
        removed = true
        cancelAutoClose()
        if (attached) {
            root.animate().cancel()
            buttons.forEach { it.animate().cancel() }
            runCatching { windowManager.removeView(root) }
            attached = false
        }
        onDismissed()
        afterClosed?.invoke()
    }

    private fun scheduleAutoClose(delayMs: Long = ArcMenuDesign.idleTimeoutMs) {
        if (closing || removed) return
        idleCloseRunnable?.let(handler::removeCallbacks)
        val delay = idleDeadline.restart(SystemClock.uptimeMillis(), delayMs)
        postIdleCheck(delay)
    }

    private fun postIdleCheck(delayMs: Long) {
        val check = Runnable {
            idleCloseRunnable = null
            val now = SystemClock.uptimeMillis()
            if (idleDeadline.shouldClose(now)) {
                close(animated = true)
            } else {
                idleDeadline.remainingMs(now)?.let(::postIdleCheck)
            }
        }
        idleCloseRunnable = check
        handler.postDelayed(check, delayMs)
    }

    private fun cancelAutoClose() {
        idleCloseRunnable?.let(handler::removeCallbacks)
        idleCloseRunnable = null
        idleDeadline.cancel()
    }

    private companion object {
        val EXIT_EASING = PathInterpolator(0.4f, 0f, 1f, 1f)
    }
}
