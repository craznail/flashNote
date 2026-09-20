package com.craznail.flashnote.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackBadgeModelTest {

    @Test
    fun badgeSitsOnTheScreenInwardCornerForEachDockSide() {
        assertEquals(
            FeedbackBadgeCorner.TOP_START,
            FeedbackBadgePlacement.corner(dockLeft = false)
        )
        assertEquals(
            FeedbackBadgeCorner.TOP_END,
            FeedbackBadgePlacement.corner(dockLeft = true)
        )
    }

    @Test
    fun successStoresNewThumbnailAndRestoresItAfterFeedback() {
        val model = FeedbackBadgeModel()

        model.showSuccess(hasNewThumbnail = true)
        assertEquals(FeedbackBadgeVisual.SUCCESS, model.visual)

        model.restoreDefault()
        assertEquals(FeedbackBadgeVisual.THUMBNAIL, model.visual)
    }

    @Test
    fun failureRestoresExistingThumbnailAfterReasonIsShown() {
        val model = FeedbackBadgeModel(hasThumbnail = true)

        model.showFailure()
        assertEquals(FeedbackBadgeVisual.FAILURE, model.visual)

        model.restoreDefault()
        assertEquals(FeedbackBadgeVisual.THUMBNAIL, model.visual)
    }

    @Test
    fun failureWithoutThumbnailReturnsToHiddenBadge() {
        val model = FeedbackBadgeModel()

        model.showFailure()
        assertEquals(FeedbackBadgeVisual.FAILURE, model.visual)

        model.restoreDefault()
        assertEquals(FeedbackBadgeVisual.HIDDEN, model.visual)
    }

    @Test
    fun thumbnailLoadedOutsideFeedbackBecomesTheDefaultVisual() {
        val model = FeedbackBadgeModel()

        model.rememberThumbnail()

        assertEquals(FeedbackBadgeVisual.THUMBNAIL, model.visual)
    }

    @Test
    fun latestSuccessReplacesFailureAndKeepsItsThumbnail() {
        val model = FeedbackBadgeModel(hasThumbnail = true)

        model.showFailure()
        model.showSuccess(hasNewThumbnail = true)
        assertEquals(FeedbackBadgeVisual.SUCCESS, model.visual)

        model.restoreDefault()
        assertEquals(FeedbackBadgeVisual.THUMBNAIL, model.visual)
    }

    @Test
    fun repeatedSuccessWithoutAnyThumbnailReturnsToHiddenBadge() {
        val model = FeedbackBadgeModel()

        model.showSuccess(hasNewThumbnail = false)
        model.showSuccess(hasNewThumbnail = false)
        assertEquals(FeedbackBadgeVisual.SUCCESS, model.visual)

        model.restoreDefault()
        assertEquals(FeedbackBadgeVisual.HIDDEN, model.visual)
    }

    @Test
    fun nonPersistentModeKeepsThumbnailButHidesItsDefaultVisual() {
        val model = FeedbackBadgeModel()

        model.rememberThumbnail(showAsDefault = false)
        assertEquals(FeedbackBadgeVisual.HIDDEN, model.visual)

        model.showSuccess(hasNewThumbnail = false)
        model.restoreDefault(persistThumbnail = false)
        assertEquals(FeedbackBadgeVisual.HIDDEN, model.visual)

        model.restoreDefault(persistThumbnail = true)
        assertEquals(FeedbackBadgeVisual.THUMBNAIL, model.visual)
    }

    @Test
    fun failureReasonCanOnlyBeConsumedOncePerFailure() {
        val model = FeedbackBadgeModel()

        model.showFailure()
        assertEquals(true, model.consumeFailureReason())
        assertEquals(false, model.consumeFailureReason())

        model.showFailure()
        assertEquals(true, model.consumeFailureReason())
    }
}
