package com.craznail.flashnote.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import com.craznail.flashnote.data.OverlayBallSize
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

private val sin75 = kotlin.math.sin(Math.toRadians(75.0)).toFloat()
private val cos75 = kotlin.math.cos(Math.toRadians(75.0)).toFloat()

class ArcMenuModelTest {

    @Test
    fun tappingBall_togglesMenuOpenThenClosed() {
        val state = ArcMenuState()

        assertEquals(ArcMenuTransition.OPEN, state.toggle())
        assertTrue(state.isOpen)

        assertEquals(ArcMenuTransition.CLOSE, state.toggle())
        assertFalse(state.isOpen)
    }

    @Test
    fun idleMenu_closesOnlyAfterTheLatestInteractionDeadline() {
        val idle = ArcMenuIdleDeadline(timeoutMs = 5_000L)

        idle.restart(nowMs = 1_000L)
        assertFalse(idle.shouldClose(nowMs = 5_999L))

        idle.restart(nowMs = 4_000L)
        assertFalse(idle.shouldClose(nowMs = 6_000L))
        assertTrue(idle.shouldClose(nowMs = 9_000L))
    }

    @Test
    fun closingMenu_cancelsItsPendingIdleDeadline() {
        val idle = ArcMenuIdleDeadline(timeoutMs = 5_000L)

        idle.restart(nowMs = 1_000L)
        idle.cancel()

        assertFalse(idle.shouldClose(nowMs = 10_000L))
    }

    @Test
    fun rightDockedMenu_ringsEvenlyWithEndsPulledInward() {
        val centers = ArcMenuGeometry.itemCenters(
            dockLeft = false,
            anchorX = 100f,
            anchorY = 100f,
            radius = 60f
        )

        assertEquals(4, centers.size)
        // Even 50° spacing, opening toward screen center (left side)
        assertTrue(centers.all { it.x <= 100f })
        assertTrue(centers.zipWithNext().all { (first, second) -> first.y < second.y })
        assertEquals(centers[0].x, centers[3].x, 0.01f)
        assertEquals(centers[1].x, centers[2].x, 0.01f)
        // First/last pulled inward from straight above/below (75°, not 90°)
        assertEquals(100f - 60f * cos75, centers.first().x, 0.01f)
        assertEquals(100f - 60f * sin75, centers.first().y, 0.01f)
        assertEquals(centers.first().x, centers.last().x, 0.01f)
        assertEquals(100f + 60f * sin75, centers.last().y, 0.01f)
    }

    @Test
    fun leftDockedMenu_ringsEvenlyWithEndsPulledInward() {
        val centers = ArcMenuGeometry.itemCenters(
            dockLeft = true,
            anchorX = 100f,
            anchorY = 100f,
            radius = 60f
        )

        assertEquals(4, centers.size)
        assertTrue(centers.all { it.x >= 100f })
        assertTrue(centers.zipWithNext().all { (first, second) -> first.y < second.y })
        assertEquals(centers[0].x, centers[3].x, 0.01f)
        assertEquals(centers[1].x, centers[2].x, 0.01f)
        assertEquals(100f + 60f * cos75, centers.first().x, 0.01f)
        assertEquals(100f - 60f * sin75, centers.first().y, 0.01f)
        assertEquals(100f + 60f * sin75, centers.last().y, 0.01f)
    }

    @Test
    fun menuWindow_coversBallTouchAreaAndFullReachOnBothSides() {
        val right = ArcMenuGeometry.windowLayout(
            dockLeft = false,
            anchorScreenX = 1_000,
            reach = 270,
            ballTouchRadius = 72
        )
        assertEquals(730, right.screenX)
        assertEquals(342, right.width)
        assertEquals(270f, right.anchorX, 0f)
        assertEquals(1_072, right.screenX + right.width)

        val left = ArcMenuGeometry.windowLayout(
            dockLeft = true,
            anchorScreenX = 1_000,
            reach = 270,
            ballTouchRadius = 72
        )
        assertEquals(928, left.screenX)
        assertEquals(342, left.width)
        assertEquals(72f, left.anchorX, 0f)
    }

    @Test
    fun menuItems_neverOverlapAfterArcIsTightened() {
        val centers = ArcMenuGeometry.itemCenters(
            dockLeft = false,
            anchorX = 0f,
            anchorY = 0f,
            radius = ArcMenuDesign.radiusDp
        )

        val nearestDistance = centers.zipWithNext().minOf { (first, second) ->
            hypot(second.x - first.x, second.y - first.y)
        }

        assertTrue(nearestDistance >= ArcMenuDesign.buttonSizeDp + 2f)
    }

    @Test
    fun openingMotion_isLongEnoughAndStartsNearTheBall() {
        assertTrue(ArcMenuDesign.startScale <= 0.5f)
        assertTrue(ArcMenuDesign.openTotalDuration(itemCount = 4) >= 500L)
        assertTrue(ArcMenuDesign.closeTotalDuration(itemCount = 4) >= 320L)
    }

    @Test
    fun primaryBall_isCompactButKeepsAComfortableTouchTarget() {
        assertEquals(44f, ArcMenuDesign.ballSizeDp, 0f)
        assertEquals(56f, ArcMenuDesign.ballTouchSizeDp, 0f)
        assertEquals(40f, ArcMenuDesign.buttonSizeDp, 0f)
        assertEquals(33f, ArcMenuDesign.dockedVisibleDp(ArcMenuDesign.ballSizeDp), 0f)
        assertEquals(14f, ArcMenuDesign.feedbackBadgeSizeDp, 0f)
        assertEquals(4f, ArcMenuDesign.ballSizeDp - ArcMenuDesign.buttonSizeDp, 0f)
        assertEquals(12f, ArcMenuDesign.ballTouchSizeDp - ArcMenuDesign.ballSizeDp, 0f)

        assertEquals(
            listOf(32f, 38f, 44f, 50f, 56f),
            OverlayBallSize.entries.map { it.diameterDp }
        )
        OverlayBallSize.entries.forEach { size ->
            assertTrue(size.diameterDp <= ArcMenuDesign.ballTouchSizeDp)
            assertEquals(
                size.diameterDp * 0.75f,
                ArcMenuDesign.dockedVisibleDp(size.diameterDp),
                0f
            )
        }

        // These stay fixed regardless of the selected main-ball size.
        assertEquals(40f, ArcMenuDesign.buttonSizeDp, 0f)
        assertEquals(14f, ArcMenuDesign.feedbackBadgeSizeDp, 0f)
    }

    @Test
    fun menuArc_staysCloseAndRingsTheBallWithoutOverlap() {
        val centers = ArcMenuGeometry.itemCenters(
            dockLeft = true,
            anchorX = 0f,
            anchorY = 0f,
            radius = ArcMenuDesign.radiusDp
        )
        val nearestDistance = centers.zipWithNext().minOf { (first, second) ->
            hypot(second.x - first.x, second.y - first.y)
        }

        assertTrue(ArcMenuDesign.radiusDp <= 58f)
        // Ends pulled 15° inward: vertical extreme is radius * sin(75°), not the full radius
        assertEquals(ArcMenuDesign.radiusDp * sin75, centers.maxOf { kotlin.math.abs(it.y) }, 0.01f)
        assertTrue(nearestDistance >= ArcMenuDesign.buttonSizeDp + 2f)
    }

    @Test
    fun menuPalette_usesDarkSmokedGlassWithWhiteIcons() {
        val fill = ArcMenuPalette.fillArgb
        val red = fill ushr 16 and 0xFF
        val green = fill ushr 8 and 0xFF
        val blue = fill and 0xFF

        assertTrue(fill ushr 24 >= 0xB0)
        assertTrue(maxOf(red, green, blue) <= 80)
        assertEquals(0xFFFFFFFF.toInt(), ArcMenuPalette.iconArgb)
    }
}
