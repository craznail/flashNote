package com.craznail.flashnote.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Side tip pill in its own overlay window (same philosophy as ArcMenuWindow).
 *
 * The ball window never resizes for a pill, so a pill show/hide can never shift the
 * ball: MIUI/Android 16 applies a window width change and the gravity re-layout on
 * separate frames, which used to flash the ball inward before it flew back to the edge.
 */
internal class SidePillWindow(
    context: Context,
    private val windowManager: WindowManager,
    overlayType: Int
) {
    private val density = context.resources.displayMetrics.density
    private val pillHeightPx = (28 * density).roundToInt()
    private val gapPx = (8 * density).roundToInt()

    private val pill = TextView(context).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        textSize = 11f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(0xFFF7F8FA.toInt())
        val pad = (10 * density).roundToInt()
        setPadding(pad, 0, pad, 0)
        elevation = 5 * density
    }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        pillHeightPx,
        overlayType,
        OverlayCapturePolicy.captureSafeFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ),
        PixelFormat.TRANSLUCENT
    )

    private var attached = false
    private var dockedLeft = false

    /**
     * Shows the pill beside the ball: a right-docked ball gets the pill on its left,
     * a left-docked ball on its right. The edge-side x offset keeps the pill flush
     * against the ball regardless of pill width.
     */
    fun show(
        text: CharSequence,
        bgColor: Int,
        maxWidthDp: Float,
        dockLeft: Boolean,
        ballCenterScreenX: Int,
        ballCenterScreenY: Int,
        ballRadiusPx: Int
    ) {
        dockedLeft = dockLeft
        val screenWidth = pill.resources.displayMetrics.widthPixels
        pill.maxWidth = (maxWidthDp * density).roundToInt()
        pill.text = text
        pill.background = GradientDrawable().apply {
            cornerRadius = 14 * density
            setColor(bgColor)
            setStroke((1 * density).roundToInt().coerceAtLeast(1), 0x24FFFFFF)
        }
        params.gravity = Gravity.TOP or if (dockLeft) Gravity.START else Gravity.END
        params.x = if (dockLeft) {
            ballCenterScreenX + ballRadiusPx + gapPx
        } else {
            screenWidth - ballCenterScreenX + ballRadiusPx + gapPx
        }
        params.y = ballCenterScreenY - pillHeightPx / 2
        pill.animate().cancel()
        pill.visibility = View.VISIBLE
        val offsets = FeedbackMotion.pillOffsets(dockLeft)
        pill.translationX = offsets.enterFromDp * density
        pill.alpha = 0f
        if (attached) {
            runCatching { windowManager.updateViewLayout(pill, params) }
        } else {
            runCatching { windowManager.addView(pill, params) }
            attached = true
        }
        pill.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(FeedbackMotion.pillEnterDurationMs)
            .setInterpolator(ENTER_EASING)
            .start()
    }

    fun dismiss(animated: Boolean) {
        if (!attached) return
        pill.animate().cancel()
        if (!animated) {
            hideWithoutRemoving()
            return
        }
        val offsets = FeedbackMotion.pillOffsets(dockedLeft)
        pill.animate()
            .alpha(0f)
            .translationX(offsets.exitToDp * density)
            .setDuration(FeedbackMotion.failureRecovery.pillExitDurationMs)
            .setInterpolator(EXIT_EASING)
            .withEndAction { hideWithoutRemoving() }
            .start()
    }

    /**
     * Keep the overlay window attached after a visual dismiss.
     *
     * Some Android/MIUI compositors can briefly flash a translucent overlay when
     * removeView() immediately follows an alpha animation. Reusing the same hidden
     * window avoids that composition-layer teardown/recreate cycle entirely.
     */
    private fun hideWithoutRemoving() {
        pill.animate().cancel()
        pill.alpha = 0f
        pill.translationX = 0f
        pill.visibility = View.INVISIBLE
    }

    fun release() {
        pill.animate().cancel()
        if (attached) {
            pill.visibility = View.INVISIBLE
            runCatching { windowManager.removeViewImmediate(pill) }
            attached = false
        }
    }

    private companion object {
        val ENTER_EASING = PathInterpolator(0.22f, 1f, 0.36f, 1f)
        val EXIT_EASING = PathInterpolator(0.4f, 0f, 1f, 1f)
    }
}
