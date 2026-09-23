package com.craznail.flashnote.overlay

internal data class CaptureSelectionAnchor(
    val leftPx: Int,
    val topPx: Int,
    val diameterPx: Int
) {
    val centerY: Int get() = topPx + diameterPx / 2
}

internal data class CaptureSelectionRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
}

internal object CaptureSelectionDesign {
    const val HANDLE_WIDTH_DP = 32f
    const val HANDLE_HEIGHT_DP = 14f

    fun captureButtonBounds(anchor: CaptureSelectionAnchor): CaptureSelectionRect =
        CaptureSelectionRect(
            left = anchor.leftPx.toFloat(),
            top = anchor.topPx.toFloat(),
            right = (anchor.leftPx + anchor.diameterPx).toFloat(),
            bottom = (anchor.topPx + anchor.diameterPx).toFloat()
        )

    fun handleBounds(
        screenWidthPx: Int,
        edgeY: Float,
        density: Float
    ): CaptureSelectionRect {
        val width = HANDLE_WIDTH_DP * density
        val height = HANDLE_HEIGHT_DP * density
        val centerX = screenWidthPx / 2f
        return CaptureSelectionRect(
            left = centerX - width / 2f,
            top = edgeY - height / 2f,
            right = centerX + width / 2f,
            bottom = edgeY + height / 2f
        )
    }
}

internal object CaptureSelectionMotion {
    const val EXIT_DURATION_MS = 220L
}
