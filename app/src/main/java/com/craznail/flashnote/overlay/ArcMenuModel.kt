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
    const val feedbackBadgeSizeDp = 14f
    // Ball edge ↔ button edge gap shrunk another 1/3 (19dp → 13dp): 24 + 20 + 13 = 57.
    const val radiusDp = 57f
    const val startScale = 0.42f
    const val openDurationMs = 380L
    const val openStaggerMs = 48L
    const val closeDurationMs = 260L
    const val closeStaggerMs = 24L
    const val idleTimeoutMs = 5_000L

    fun dockedVisibleDp(ballSizeDp: Float): Float = ballSizeDp * dockedVisibleRatio

    fun openTotalDuration(itemCount: Int): Long =
        openDurationMs + (itemCount - 1).coerceAtLeast(0) * openStaggerMs

    fun closeTotalDuration(itemCount: Int): Long =
        closeDurationMs + (itemCount - 1).coerceAtLeast(0) * closeStaggerMs
}

internal object ArcMenuPalette {
    const val highlightArgb = 0xE05B606B.toInt()
    const val fillArgb = 0xD13A3D46.toInt()
    const val edgeArgb = 0xE0262931.toInt()
    const val strokeArgb = 0xBFDDE3EE.toInt()
    const val iconArgb = 0xFFFFFFFF.toInt()
}

internal object ArcMenuGeometry {
    // Even half-ring around the ball, first/last pulled 15° inward from straight
    // above/below so they do not hug the screen edge.
    private val leftDockAngles = floatArrayOf(-75f, -25f, 25f, 75f)
    private val rightDockAngles = floatArrayOf(255f, 205f, 155f, 105f)

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
