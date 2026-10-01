package com.craznail.flashnote.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureSelectionDesignTest {

    @Test
    fun captureButton_followsTheRightMiddleOfTheSelection() {
        val bounds = CaptureSelectionDesign.captureButtonBounds(
            screenWidthPx = 1080,
            selectionTop = 600f,
            selectionBottom = 1200f,
            diameterPx = 132,
            density = 3f
        )

        assertEquals(918f, bounds.left, 0f)
        assertEquals(834f, bounds.top, 0f)
        assertEquals(1050f, bounds.right, 0f)
        assertEquals(966f, bounds.bottom, 0f)

        val moved = CaptureSelectionDesign.captureButtonBounds(1080, 300f, 700f, 132, 3f)
        assertEquals(1050f, moved.right, 0f)
        assertEquals(500f, moved.centerY(), 0f)
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
