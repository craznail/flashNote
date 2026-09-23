package com.craznail.flashnote.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureSelectionDesignTest {

    @Test
    fun captureButtonBounds_matchTheFloatingBallAnchorExactly() {
        val anchor = CaptureSelectionAnchor(
            leftPx = -18,
            topPx = 420,
            diameterPx = 132
        )

        val bounds = CaptureSelectionDesign.captureButtonBounds(anchor)

        assertEquals(-18f, bounds.left, 0f)
        assertEquals(420f, bounds.top, 0f)
        assertEquals(114f, bounds.right, 0f)
        assertEquals(552f, bounds.bottom, 0f)
    }

    @Test
    fun adjustmentHandles_useTheCompactReferenceDimensionsAndStayCentered() {
        val bounds = CaptureSelectionDesign.handleBounds(
            screenWidthPx = 1080,
            edgeY = 640f,
            density = 3f
        )

        assertEquals(96f, bounds.width(), 0f)
        assertEquals(42f, bounds.height(), 0f)
        assertEquals(540f, bounds.centerX(), 0f)
        assertEquals(640f, bounds.centerY(), 0f)
    }

    @Test
    fun selectionExit_usesAVisibleButQuickCrossfade() {
        assertEquals(220L, CaptureSelectionMotion.EXIT_DURATION_MS)
    }
}
