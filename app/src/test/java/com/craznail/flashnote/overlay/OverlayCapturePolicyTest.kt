package com.craznail.flashnote.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayCapturePolicyTest {

    @Test
    fun captureSafeFlags_doNotAddSecureSurfaceMasking() {
        val baseFlags = 0b0101

        val result = OverlayCapturePolicy.captureSafeFlags(baseFlags)

        assertEquals(baseFlags, result)
    }
}
