package com.craznail.flashnote.overlay

internal data class PillMotionOffsets(
    val enterFromDp: Float,
    val exitToDp: Float
)

internal data class FailureRecoveryTimeline(
    val pillHoldMs: Long,
    val pillExitDurationMs: Long,
    val badgeExitDelayMs: Long,
    val badgeExitDurationMs: Long,
    val resetDelayMs: Long
)

internal data class SuccessFeedbackTimeline(
    val enterDurationMs: Long,
    val holdDurationMs: Long,
    val exitDurationMs: Long
) {
    val exitDelayMs: Long
        get() = enterDurationMs + holdDurationMs

    val totalDurationMs: Long
        get() = exitDelayMs + exitDurationMs
}

/** Shared timing and direction rules for overlay feedback. */
internal object FeedbackMotion {
    const val pillEnterDurationMs = 180L
    const val badgeEnterDurationMs = 180L

    val failureRecovery = FailureRecoveryTimeline(
        pillHoldMs = 2_000L,
        pillExitDurationMs = 180L,
        badgeExitDelayMs = 1_920L,
        badgeExitDurationMs = 240L,
        resetDelayMs = 2_180L
    )

    val successFeedback = SuccessFeedbackTimeline(
        enterDurationMs = 260L,
        holdDurationMs = 1_500L,
        exitDurationMs = 420L
    )

    fun pillOffsets(dockLeft: Boolean): PillMotionOffsets {
        val towardBall = if (dockLeft) -1f else 1f
        return PillMotionOffsets(
            enterFromDp = towardBall * 6f,
            exitToDp = towardBall * 4f
        )
    }
}
