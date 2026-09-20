package com.craznail.flashnote.overlay

/**
 * Overlay windows stay ordinary transparent overlays.
 *
 * FLAG_SECURE caused MediaProjection to black out the overlay surface instead of
 * revealing the app underneath on HyperOS/API 36. FlashNote now excludes its UI by
 * temporarily making the overlay content invisible for the capture frame.
 */
internal object OverlayCapturePolicy {
    fun captureSafeFlags(baseFlags: Int): Int = baseFlags
}
