package com.craznail.flashnote.overlay

internal data class DockedBallPlacement(
    val dockLeft: Boolean,
    val insetPx: Int
) {
    fun screenLeft(windowX: Int, windowWidth: Int, ballWidth: Int): Int =
        if (dockLeft) {
            windowX + insetPx
        } else {
            windowX + windowWidth - ballWidth - insetPx
        }
}

/**
 * Horizontal WindowManager coordinates relative to the docked edge.
 *
 * START gravity measures [edgeOffsetPx] from the left edge. END gravity measures it
 * from the right edge. Keeping this value unchanged while resizing lets the window
 * grow toward the screen centre without also submitting a competing x-position move.
 */
internal data class DockedWindowPlacement(
    val dockLeft: Boolean,
    val edgeOffsetPx: Int
) {
    fun screenLeft(screenWidth: Int, windowWidth: Int): Int =
        if (dockLeft) edgeOffsetPx else screenWidth - windowWidth - edgeOffsetPx
}

internal object DockedBallLayout {
    fun placement(dockLeft: Boolean, insetPx: Int): DockedBallPlacement =
        DockedBallPlacement(dockLeft, insetPx)

    fun dockedStartX(
        dockLeft: Boolean,
        screenWidth: Int,
        windowWidth: Int,
        rightDockStartX: Int
    ): Int = if (dockLeft) {
        -(windowWidth - (screenWidth - rightDockStartX))
    } else {
        rightDockStartX
    }

    fun windowPlacement(dockLeft: Boolean, edgeOffsetPx: Int): DockedWindowPlacement =
        DockedWindowPlacement(dockLeft, edgeOffsetPx)

    fun windowPlacementFromStartX(
        dockLeft: Boolean,
        startX: Int,
        screenWidth: Int,
        windowWidth: Int
    ): DockedWindowPlacement = DockedWindowPlacement(
        dockLeft = dockLeft,
        edgeOffsetPx = if (dockLeft) startX else screenWidth - windowWidth - startX
    )
}
