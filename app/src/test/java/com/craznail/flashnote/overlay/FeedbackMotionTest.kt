package com.craznail.flashnote.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackMotionTest {

    @Test
    fun rightDockedPill_entersAndExitsTowardTheBall() {
        val offsets = FeedbackMotion.pillOffsets(dockLeft = false)

        assertEquals(6f, offsets.enterFromDp, 0f)
        assertEquals(4f, offsets.exitToDp, 0f)
    }

    @Test
    fun leftDockedPill_entersAndExitsTowardTheBall() {
        val offsets = FeedbackMotion.pillOffsets(dockLeft = true)

        assertEquals(-6f, offsets.enterFromDp, 0f)
        assertEquals(-4f, offsets.exitToDp, 0f)
    }

    @Test
    fun failureRecovery_overlapsPillAndBadgeExitBeforeResetting() {
        val timeline = FeedbackMotion.failureRecovery

        assertTrue(timeline.badgeExitDelayMs < timeline.pillHoldMs)
        assertEquals(2_180L, timeline.resetDelayMs)
        assertTrue(
            timeline.resetDelayMs >=
                timeline.badgeExitDelayMs + timeline.badgeExitDurationMs
        )
        assertTrue(
            timeline.resetDelayMs >=
                timeline.pillHoldMs + timeline.pillExitDurationMs
        )
    }

    @Test
    fun successFeedback_holdsLongEnoughAndCrossfadesBothDirections() {
        val timeline = FeedbackMotion.successFeedback

        assertTrue(timeline.enterDurationMs >= 200L)
        assertTrue(timeline.holdDurationMs >= 1_200L)
        assertTrue(timeline.exitDurationMs >= 300L)
        assertEquals(1_760L, timeline.exitDelayMs)
        assertEquals(2_180L, timeline.totalDurationMs)
    }
}
