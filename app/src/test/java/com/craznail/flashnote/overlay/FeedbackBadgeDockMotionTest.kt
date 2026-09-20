package com.craznail.flashnote.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackBadgeDockMotionTest {

    @Test
    fun crossingToLeftDock_startsFromTheOldLeftCornerAndMovesRight() {
        assertEquals(
            -30f,
            FeedbackBadgeDockMotion.startTranslationDp(
                toDockLeft = true,
                ballSizeDp = 44f,
                badgeSizeDp = 14f
            ),
            0f
        )
    }

    @Test
    fun crossingToRightDock_startsFromTheOldRightCornerAndMovesLeft() {
        assertEquals(
            30f,
            FeedbackBadgeDockMotion.startTranslationDp(
                toDockLeft = false,
                ballSizeDp = 44f,
                badgeSizeDp = 14f
            ),
            0f
        )
    }

    @Test
    fun motionLiftsAndSqueezesAtMidpoint_thenReturnsToRest() {
        assertEquals(0f, FeedbackBadgeDockMotion.arcTranslationYDp(0f), 0.001f)
        assertTrue(FeedbackBadgeDockMotion.arcTranslationYDp(0.5f) < 0f)
        assertEquals(0f, FeedbackBadgeDockMotion.arcTranslationYDp(1f), 0.001f)

        assertEquals(1f, FeedbackBadgeDockMotion.scaleX(0f), 0.001f)
        assertTrue(FeedbackBadgeDockMotion.scaleX(0.5f) < 1f)
        assertEquals(1f, FeedbackBadgeDockMotion.scaleX(1f), 0.001f)

        assertEquals(1f, FeedbackBadgeDockMotion.scaleY(0f), 0.001f)
        assertTrue(FeedbackBadgeDockMotion.scaleY(0.5f) > 1f)
        assertEquals(1f, FeedbackBadgeDockMotion.scaleY(1f), 0.001f)
    }
}
