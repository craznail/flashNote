package com.craznail.flashnote.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class DockedBallLayoutTest {

    @Test
    fun rightDockedWindow_keepsOneEdgeOffsetWhenWidthChanges() {
        val screenWidth = 1_080
        val collapsedWidth = 144
        val collapsedStartX = 993
        val placement = DockedBallLayout.windowPlacementFromStartX(
            dockLeft = false,
            startX = collapsedStartX,
            screenWidth = screenWidth,
            windowWidth = collapsedWidth
        )

        assertEquals(-57, placement.edgeOffsetPx)
        assertEquals(
            collapsedStartX,
            placement.screenLeft(screenWidth = screenWidth, windowWidth = collapsedWidth)
        )
        assertEquals(
            735,
            placement.screenLeft(screenWidth = screenWidth, windowWidth = 402)
        )
    }

    @Test
    fun rightDockedBall_keepsScreenPositionWhenOverlayWidthChanges() {
        val screenWidth = 1_080
        val collapsedWindowX = 993
        val collapsedWindowWidth = 48
        val expandedWindowWidth = 132
        val windowPlacement = DockedBallLayout.windowPlacementFromStartX(
            dockLeft = false,
            startX = collapsedWindowX,
            screenWidth = screenWidth,
            windowWidth = collapsedWindowWidth
        )
        val ballPlacement = DockedBallLayout.placement(
            dockLeft = false,
            insetPx = 4
        )

        val collapsedBallLeft = ballPlacement.screenLeft(
            windowX = windowPlacement.screenLeft(screenWidth, collapsedWindowWidth),
            windowWidth = collapsedWindowWidth,
            ballWidth = 40
        )
        val expandedBallLeft = ballPlacement.screenLeft(
            windowX = windowPlacement.screenLeft(screenWidth, expandedWindowWidth),
            windowWidth = expandedWindowWidth,
            ballWidth = 40
        )

        assertEquals(997, collapsedBallLeft)
        assertEquals(collapsedBallLeft, expandedBallLeft)
    }

    @Test
    fun leftAndRightDock_hideTheSameAmountOfTheBall() {
        val screenWidth = 1_080
        val windowWidth = 56
        val ballWidth = 44
        val inset = 6
        val rightDockStartX = screenWidth - 33
        val placementLeft = DockedBallLayout.placement(dockLeft = true, insetPx = inset)
        val placementRight = DockedBallLayout.placement(dockLeft = false, insetPx = inset)

        val leftStartX = DockedBallLayout.dockedStartX(
            dockLeft = true,
            screenWidth = screenWidth,
            windowWidth = windowWidth,
            rightDockStartX = rightDockStartX
        )
        val rightStartX = DockedBallLayout.dockedStartX(
            dockLeft = false,
            screenWidth = screenWidth,
            windowWidth = windowWidth,
            rightDockStartX = rightDockStartX
        )
        val leftVisible = ballWidth + placementLeft.screenLeft(
            windowX = leftStartX,
            windowWidth = windowWidth,
            ballWidth = ballWidth
        )
        val rightBallLeft = placementRight.screenLeft(
            windowX = rightStartX,
            windowWidth = windowWidth,
            ballWidth = ballWidth
        )
        val rightVisible = screenWidth - rightBallLeft

        assertEquals(rightVisible, leftVisible)
    }

    @Test
    fun leftDockedBall_keepsScreenPositionWhenOverlayWidthChanges() {
        val windowX = -11
        val placement = DockedBallLayout.placement(
            dockLeft = true,
            insetPx = 4
        )

        val collapsedBallLeft = placement.screenLeft(
            windowX = windowX,
            windowWidth = 48,
            ballWidth = 40
        )
        val expandedBallLeft = placement.screenLeft(
            windowX = windowX,
            windowWidth = 132,
            ballWidth = 40
        )

        assertEquals(-7, collapsedBallLeft)
        assertEquals(collapsedBallLeft, expandedBallLeft)
    }
}
