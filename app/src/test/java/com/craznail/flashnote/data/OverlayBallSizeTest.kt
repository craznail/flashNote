package com.craznail.flashnote.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayBallSizeTest {

    @Test
    fun fiveSizeSteps_areStableAndMediumIsTheFallback() {
        assertEquals(
            listOf(32f, 38f, 44f, 50f, 56f),
            OverlayBallSize.entries.map { it.diameterDp }
        )
        assertEquals(OverlayBallSize.MEDIUM, OverlayBallSize.fromStored(null))
        assertEquals(OverlayBallSize.MEDIUM, OverlayBallSize.fromStored("UNKNOWN"))
        assertEquals(OverlayBallSize.EXTRA_LARGE, OverlayBallSize.fromStored("EXTRA_LARGE"))
    }
}
