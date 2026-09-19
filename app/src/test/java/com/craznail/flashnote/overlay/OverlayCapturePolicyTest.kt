package com.craznail.flashnote.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayCapturePolicyTest {

    @Test
    fun secureFlags_preserveWindowBehaviorAndAddCaptureExclusion() {
        val baseFlags = 0b0101
        val secureFlag = 0b1000

        val result = OverlayCapturePolicy.secureFlags(baseFlags, secureFlag)

        assertEquals(0b1101, result)
    }
}
