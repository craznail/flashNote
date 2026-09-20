package com.craznail.flashnote.overlay

import kotlin.math.PI
import kotlin.math.sin

/** Pure geometry/timing rules for sliding the feedback badge around the ball when docking sides change. */
internal object FeedbackBadgeDockMotion {
    const val durationMs = 280L
    const val arcLiftDp = 4f
    const val squeezedScaleX = 0.86f
    const val squeezedScaleY = 1.05f

    fun startTranslationDp(
        toDockLeft: Boolean,
        ballSizeDp: Float = ArcMenuDesign.ballSizeDp,
        badgeSizeDp: Float = ArcMenuDesign.feedbackBadgeSizeDp
    ): Float {
        val travel = (ballSizeDp - badgeSizeDp).coerceAtLeast(0f)
        return if (toDockLeft) -travel else travel
    }

    fun arcTranslationYDp(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return -arcLiftDp * sin(PI * p).toFloat()
    }

    fun scaleX(progress: Float): Float {
        val squeeze = sin(PI * progress.coerceIn(0f, 1f)).toFloat()
        return 1f - (1f - squeezedScaleX) * squeeze
    }

    fun scaleY(progress: Float): Float {
        val squeeze = sin(PI * progress.coerceIn(0f, 1f)).toFloat()
        return 1f + (squeezedScaleY - 1f) * squeeze
    }
}
