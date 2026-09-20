package com.craznail.flashnote.overlay

import kotlin.math.cos
import kotlin.math.sin

internal enum class ArcMenuTransition {
    OPEN,
    CLOSE
}

internal class ArcMenuState {
    var isOpen: Boolean = false
        private set

    fun toggle(): ArcMenuTransition {
        isOpen = !isOpen
        return if (isOpen) ArcMenuTransition.OPEN else ArcMenuTransition.CLOSE
    }

    fun close() {
        isOpen = false
    }
}

/** Tracks the latest inactivity deadline so stale callbacks cannot close a refreshed menu. */
internal class ArcMenuIdleDeadline(
    private val timeoutMs: Long
) {
    private var deadlineMs: Long? = null

    init {
        require(timeoutMs > 0L)
    }

    fun restart(nowMs: Long, delayMs: Long = timeoutMs): Long {
        require(delayMs >= 0L)
        deadlineMs = nowMs + delayMs
        return delayMs
    }

    fun cancel() {
        deadlineMs = null
    }

    fun remainingMs(nowMs: Long): Long? =
        deadlineMs?.let { (it - nowMs).coerceAtLeast(0L) }

    fun shouldClose(nowMs: Long): Boolean =
        deadlineMs?.let { nowMs >= it } ?: false
}

internal data class ArcPoint(val x: Float, val y: Float)

internal data class ArcMenuWindowLayout(
    val screenX: Int,
    val width: Int,
    val anchorX: Float
)

internal object ArcMenuDesign {
    const val ballSizeDp = 44f
    const val ballTouchSizeDp = 56f
    const val buttonSizeDp = 40f
    const val dockedVisibleRatio = 0.75f
    const val idleDockedVisibleRatio = 0.26f
    const val idleBallAlpha = 0.68f
    const val idleBallScale = 0.96f
    const val idleCollapseDelayMs = 2_600L
    const val idleCollapseDurationMs = 220L
    const val idleExpandDurationMs = 140L
    const val feedbackBadgeSizeDp = 14f

    // Compact inward fan. It keeps the first action visually closest to the top while
    // avoiding the rigid half-circle feel of the previous 75/25-degree layout.
    const val radiusDp = 57f
    const val startScale = 0.70f
    const val openDurationMs = 230L
    const val openStaggerMs = 28L
    const val closeDurationMs = 180L
    const val closeStaggerMs = 16L
    const val idleTimeoutMs = 5_000L

    fun dockedVisibleDp(ballSizeDp: Float): Float = ballSizeDp * dockedVisibleRatio
    fun idleDockedVisibleDp(ballSizeDp: Float): Float = ballSizeDp * idleDockedVisibleRatio

    fun remainingIdleExpandMs(elapsedMs: Long): Long =
        (idleExpandDurationMs - elapsedMs.coerceAtLeast(0L)).coerceAtLeast(0L)

    fun openTotalDuration(itemCount: Int): Long =
        openDurationMs + (itemCount - 1).coerceAtLeast(0) * openStaggerMs

    fun closeTotalDuration(itemCount: Int): Long =
        closeDurationMs + (itemCount - 1).coerceAtLeast(0) * closeStaggerMs
}

internal object ArcMenuPalette {
    const val fillArgb = 0xD944474F.toInt()
    const val strokeArgb = 0x30FFFFFF
    const val iconArgb = 0xFFFFFFFF.toInt()
}

internal object ArcMenuGeometry {
    // Inward fan inspired by Android's accessibility floating shortcut: compact,
    // vertically ordered, and slightly flatter than a mathematical half-ring.
    private val leftDockAngles = floatArrayOf(-66f, -22f, 22f, 66f)
    private val rightDockAngles = floatArrayOf(246f, 202f, 158f, 114f)

    fun itemCenters(
        dockLeft: Boolean,
        anchorX: Float,
        anchorY: Float,
        radius: Float
    ): List<ArcPoint> {
        val angles = if (dockLeft) leftDockAngles else rightDockAngles
        return angles.map { angle ->
            val radians = Math.toRadians(angle.toDouble())
            ArcPoint(
                x = anchorX + radius * cos(radians).toFloat(),
                y = anchorY + radius * sin(radians).toFloat()
            )
        }
    }

    /**
     * The ring's top/bottom items sit exactly above/below the anchor, so the window
     * now spans from the ball's touch area across the full reach toward screen centre.
     */
    fun windowLayout(
        dockLeft: Boolean,
        anchorScreenX: Int,
        reach: Int,
        ballTouchRadius: Int
    ): ArcMenuWindowLayout {
        require(reach > ballTouchRadius)
        return ArcMenuWindowLayout(
            screenX = if (dockLeft) {
                anchorScreenX - ballTouchRadius
            } else {
                anchorScreenX - reach
            },
            width = reach + ballTouchRadius,
            anchorX = if (dockLeft) ballTouchRadius.toFloat() else reach.toFloat()
        )
    }
}
