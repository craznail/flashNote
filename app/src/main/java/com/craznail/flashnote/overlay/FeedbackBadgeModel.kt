package com.craznail.flashnote.overlay

internal enum class FeedbackBadgeCorner {
    TOP_START,
    TOP_END
}

internal object FeedbackBadgePlacement {
    fun corner(dockLeft: Boolean): FeedbackBadgeCorner =
        if (dockLeft) FeedbackBadgeCorner.TOP_END else FeedbackBadgeCorner.TOP_START
}

internal enum class FeedbackBadgeVisual {
    HIDDEN,
    THUMBNAIL,
    SUCCESS,
    FAILURE
}

internal class FeedbackBadgeModel(hasThumbnail: Boolean = false) {
    var hasThumbnail: Boolean = hasThumbnail
        private set

    var visual: FeedbackBadgeVisual = defaultVisual()
        private set

    private var failureReasonConsumed: Boolean = false

    fun showSuccess(hasNewThumbnail: Boolean) {
        hasThumbnail = hasThumbnail || hasNewThumbnail
        visual = FeedbackBadgeVisual.SUCCESS
    }

    fun showFailure() {
        failureReasonConsumed = false
        visual = FeedbackBadgeVisual.FAILURE
    }

    fun consumeFailureReason(): Boolean {
        if (visual != FeedbackBadgeVisual.FAILURE || failureReasonConsumed) return false
        failureReasonConsumed = true
        return true
    }

    fun rememberThumbnail() {
        hasThumbnail = true
        if (visual == FeedbackBadgeVisual.HIDDEN) {
            visual = FeedbackBadgeVisual.THUMBNAIL
        }
    }

    fun restoreDefault() {
        visual = defaultVisual()
    }

    private fun defaultVisual(): FeedbackBadgeVisual =
        if (hasThumbnail) FeedbackBadgeVisual.THUMBNAIL else FeedbackBadgeVisual.HIDDEN
}
