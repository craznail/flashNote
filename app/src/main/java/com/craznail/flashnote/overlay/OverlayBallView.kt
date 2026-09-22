package com.craznail.flashnote.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.craznail.flashnote.R
import com.craznail.flashnote.data.OverlayBallSize
import com.craznail.flashnote.data.OverlayPreferences
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating overlay ball:
 * - Main ball is size-selectable (32–56dp) while the touch target stays 56dp
 * - Active dock exposes 75%; after idle it retracts to a low-profile 26% edge sliver
 * - Tap toggles a compact inward fan menu (capture only via menu items)
 * - Sub-buttons stay 40dp while menu visuals remain deliberately quiet
 * - A single 14dp inward-corner badge carries thumbnail / success / failure feedback
 */
class OverlayBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onCapture: (() -> Unit)? = null
    var onCaptureSelectionRequested: ((Int) -> Unit)? = null
    var onOpenLatestNote: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null
    var onExit: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val overlayPreferences = OverlayPreferences.get(context)
    private var currentBallSize = overlayPreferences.ballSize.value
    private var ballSizePx = (currentBallSize.diameterDp * density).roundToInt()
    private var visibleWhenDockedPx =
        (ArcMenuDesign.dockedVisibleDp(currentBallSize.diameterDp) * density).roundToInt()
    private val touchSlop = 8 * density
    private val ballContainer: FrameLayout
    private val iconView: ImageView
    private val feedbackBadge: FrameLayout
    private val thumbBadge: ImageView
    private val successBadge: View
    private val failureBadge: TextView
    private val badgeModel = FeedbackBadgeModel()
    private var feedbackBadgePersistent = overlayPreferences.feedbackBadgePersistent.value
    private var windowParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var overlayType: Int? = null
    private val menuState = ArcMenuState()
    private var arcMenuWindow: ArcMenuWindow? = null
    /** Side tip pill lives in its own window — the ball window never resizes. */
    private var sidePillWindow: SidePillWindow? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var startParamX = 0
    private var startParamY = 0
    /** WindowManager x is measured from this edge (START for left, END for right). */
    private var dockedLeft = false
    private var moved = false
    private val handler = Handler(Looper.getMainLooper())
    private var hideToastRunnable: Runnable? = null
    /** Sticky side pill (remote summarizing) — stays until clearSidePill / success / fail. */
    private var pillSticky = false
    private var lastFailReason: String? = null
    private var normalizeRunnable: Runnable? = null
    private var badgeExitRunnable: Runnable? = null
    private var feedbackTipRunnable: Runnable? = null
    private var badgeDockAnimator: ValueAnimator? = null
    private var idleCollapseRunnable: Runnable? = null
    private var pendingMenuOpenRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false
    private var idleCollapsed = false
    private var downStartedFromIdle = false

    private val touchHotspotPx = (ArcMenuDesign.ballTouchSizeDp * density).roundToInt()

    init {
        clipChildren = false
        clipToPadding = false

        ballContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx).apply {
                gravity = Gravity.CENTER
            }
            elevation = 2 * density // keep the ball close to the screen surface
            clipChildren = false
            clipToPadding = false
        }

        iconView = ImageView(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(R.drawable.ic_ball_normal)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = context.getString(R.string.app_name)
        }

        val badgeSizePx = (ArcMenuDesign.feedbackBadgeSizeDp * density).roundToInt()
        feedbackBadge = FrameLayout(context).apply {
            layoutParams = LayoutParams(badgeSizePx, badgeSizePx)
            clipChildren = false
            clipToPadding = false
            elevation = 3 * density
            visibility = View.GONE
        }

        thumbBadge = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.VISIBLE
            alpha = 0f
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        successBadge = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(SUCCESS_GREEN)
                setStroke((1 * density).roundToInt().coerceAtLeast(1), 0xE6FFFFFF.toInt())
            }
            visibility = View.VISIBLE
            alpha = 0f
        }

        failureBadge = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            text = "!"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFDFDFD.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(FAILURE_CORAL)
                setStroke((1 * density).roundToInt().coerceAtLeast(1), 0xE6FFFFFF.toInt())
            }
            visibility = View.VISIBLE
            alpha = 0f
        }

        feedbackBadge.addView(thumbBadge)
        feedbackBadge.addView(successBadge)
        feedbackBadge.addView(failureBadge)

        ballContainer.addView(iconView)
        ballContainer.addView(feedbackBadge)

        addView(ballContainer)

        isClickable = true
        isFocusable = true
    }

    fun attach(wm: WindowManager): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            touchHotspotPx,
            touchHotspotPx,
            type,
            OverlayCapturePolicy.captureSafeFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            ),
            PixelFormat.TRANSLUCENT
        ).apply {
            val dm = resources.displayMetrics
            gravity = Gravity.TOP or Gravity.END
            x = DockedBallLayout.windowPlacementFromStartX(
                dockLeft = false,
                startX = dm.widthPixels - visibleWhenDockedPx,
                screenWidth = dm.widthPixels,
                windowWidth = touchHotspotPx
            ).edgeOffsetPx
            y = (dm.heightPixels * 0.35f).toInt()
        }
        windowManager = wm
        overlayType = type
        windowParams = params
        pinBallToDockEdge(dockLeft = false)
        wm.addView(this, params)
        scheduleIdleCollapse()
        return params
    }

    override fun onDetachedFromWindow() {
        hideActionMenu(animate = false)
        hideToastRunnable?.let { handler.removeCallbacks(it) }
        normalizeRunnable?.let { handler.removeCallbacks(it) }
        badgeExitRunnable?.let { handler.removeCallbacks(it) }
        feedbackTipRunnable?.let { handler.removeCallbacks(it) }
        idleCollapseRunnable?.let { handler.removeCallbacks(it) }
        idleCollapseRunnable = null
        pendingMenuOpenRunnable?.let { handler.removeCallbacks(it) }
        pendingMenuOpenRunnable = null
        cancelFeedbackAnimations()
        badgeDockAnimator?.cancel()
        ballContainer.animate().cancel()
        sidePillWindow?.release()
        sidePillWindow = null
        cancelLongPressDetection()
        windowManager = null
        super.onDetachedFromWindow()
    }

    /**
     * Success is carried entirely by the 14dp badge; the primary ball never changes colour.
     * A new thumbnail is installed underneath the success layer before the crossfade starts.
     */
    fun showSuccessFeedback(tipText: String? = null, thumbnailPath: String? = null) {
        expandFromIdle()
        clearSidePill(immediate = true)
        cancelFeedbackState()
        lastFailReason = null

        val hasNewThumbnail = !thumbnailPath.isNullOrBlank() &&
            File(thumbnailPath).exists() &&
            updateThumbnailDrawable(thumbnailPath)
        badgeModel.showSuccess(hasNewThumbnail = hasNewThumbnail)

        val timeline = FeedbackMotion.successFeedback
        transitionBadgeTo(FeedbackBadgeVisual.SUCCESS, timeline.enterDurationMs)
        scheduleSuccessExit()
        scheduleIdleCollapse(timeline.totalDurationMs + ArcMenuDesign.idleCollapseDelayMs)

        val tip = tipText?.takeIf { it.isNotBlank() }
        if (tip != null) {
            val tipRunnable = Runnable {
                feedbackTipRunnable = null
                if (badgeModel.visual == FeedbackBadgeVisual.SUCCESS ||
                    badgeModel.visual == FeedbackBadgeVisual.THUMBNAIL ||
                    badgeModel.visual == FeedbackBadgeVisual.HIDDEN
                ) {
                    showSidePill(tip, 0xCC374151.toInt(), 1_600L, sticky = false)
                }
            }
            feedbackTipRunnable = tipRunnable
            handler.postDelayed(tipRunnable, timeline.exitDelayMs)
        }
    }

    fun showFailureUnauthorized() {
        showFailure(context.getString(R.string.unauthorized_capture))
    }

    fun showFailure(reason: String) {
        expandFromIdle()
        cancelIdleCollapse()
        clearSidePill(immediate = true)
        cancelFeedbackState()
        lastFailReason = reason
        badgeModel.showFailure()
        transitionBadgeTo(FeedbackBadgeVisual.FAILURE, FeedbackMotion.badgeEnterDurationMs)
    }

    fun showSystemTip(text: String, durationMs: Long = 1_200L) {
        showSidePill(text, 0xCC374151.toInt(), durationMs, sticky = false)
    }

    fun setThumbnailBadge(path: String) {
        if (!updateThumbnailDrawable(path)) return
        badgeModel.rememberThumbnail(showAsDefault = feedbackBadgePersistent)
        if (feedbackBadgePersistent && badgeModel.visual == FeedbackBadgeVisual.THUMBNAIL) {
            transitionBadgeTo(FeedbackBadgeVisual.THUMBNAIL, durationMs = 0L)
        }
    }

    fun setFeedbackBadgePersistent(enabled: Boolean) {
        feedbackBadgePersistent = enabled
        if (badgeModel.visual == FeedbackBadgeVisual.THUMBNAIL ||
            badgeModel.visual == FeedbackBadgeVisual.HIDDEN
        ) {
            badgeModel.restoreDefault(persistThumbnail = enabled)
            transitionBadgeTo(badgeModel.visual, durationMs = 160L)
        }
    }

    fun setCaptureHidden(hidden: Boolean) {
        if (hidden) {
            cancelIdleCollapse()
            hideActionMenu(animate = false)
            clearSidePill(immediate = true)
        } else {
            scheduleIdleCollapse()
        }
        visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    fun setBallSize(size: OverlayBallSize) {
        if (size == currentBallSize) return

        currentBallSize = size
        ballSizePx = (size.diameterDp * density).roundToInt()
        visibleWhenDockedPx =
            (ArcMenuDesign.dockedVisibleDp(size.diameterDp) * density).roundToInt()

        badgeDockAnimator?.cancel()
        feedbackBadge.translationX = 0f
        feedbackBadge.translationY = 0f
        feedbackBadge.scaleX = 1f
        feedbackBadge.scaleY = 1f

        (ballContainer.layoutParams as LayoutParams).also { lp ->
            lp.width = ballSizePx
            lp.height = ballSizePx
            ballContainer.layoutParams = lp
        }
        (iconView.layoutParams as LayoutParams).also { lp ->
            lp.width = ballSizePx
            lp.height = ballSizePx
            iconView.layoutParams = lp
        }

        val wm = windowManager
        val windowLp = windowParams
        if (wm != null && windowLp != null) {
            val dm = resources.displayMetrics
            val insetPx = (touchHotspotPx - ballSizePx) / 2
            val targetStartX = DockedBallLayout.dockedStartX(
                dockLeft = dockedLeft,
                screenWidth = dm.widthPixels,
                windowWidth = windowLp.width,
                ballWidth = ballSizePx,
                insetPx = insetPx,
                visibleBallWidth = visibleWhenDockedPx
            )
            windowLp.gravity = Gravity.TOP or if (dockedLeft) Gravity.START else Gravity.END
            windowLp.x = DockedBallLayout.windowPlacementFromStartX(
                dockLeft = dockedLeft,
                startX = targetStartX,
                screenWidth = dm.widthPixels,
                windowWidth = windowLp.width
            ).edgeOffsetPx
            pinBallToDockEdge(dockedLeft)
            runCatching { wm.updateViewLayout(this, windowLp) }
        } else {
            positionFeedbackBadge(dockedLeft)
        }
        expandFromIdle(animated = false)
        scheduleIdleCollapse()
    }

    private fun updateThumbnailDrawable(path: String): Boolean {
        return try {
            val bmp = android.graphics.BitmapFactory.decodeFile(path) ?: return false
            val size = (ArcMenuDesign.feedbackBadgeSizeDp * density).roundToInt()
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, size, size, true)
            if (scaled != bmp) bmp.recycle()
            val drawable = RoundedBitmapDrawableFactory.create(resources, scaled).apply {
                isCircular = true
            }
            thumbBadge.setImageDrawable(drawable)
            thumbBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((1.25f * density).roundToInt().coerceAtLeast(1), 0xFFFFFFFF.toInt())
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Timed tip pill (exit confirm, projection tips, etc.). */
    fun showPlainToast(text: String, durationMs: Long = 1_200L) {
        showSidePill(text, 0xCC374151.toInt(), durationMs, sticky = false)
    }

    /**
     * Light sticky loading pill while remote summary runs.
     * Cleared by [clearSidePill], [showSuccessFeedback], or [showFailureUnauthorized].
     * Safety auto-clear at 45s so a hung request never leaves the pill stuck.
     */
    fun showLoadingPill(text: String) {
        showSidePill(text, 0xCC374151.toInt(), durationMs = 45_000L, sticky = true)
    }

    fun clearSidePill(immediate: Boolean = true) {
        hideToastRunnable?.let { handler.removeCallbacks(it) }
        hideToastRunnable = null
        pillSticky = false
        sidePillWindow?.dismiss(animated = !immediate)
        scheduleIdleCollapse()
    }

    private fun showSidePill(
        text: CharSequence,
        bgColor: Int,
        durationMs: Long = 1_200L,
        sticky: Boolean = false
    ) {
        expandFromIdle()
        cancelIdleCollapse()
        hideToastRunnable?.let { handler.removeCallbacks(it) }
        hideToastRunnable = null
        pillSticky = sticky
        val wm = windowManager
        val type = overlayType
        if (wm != null && type != null) {
            // Loading stays compact; longer fallback tips get a bit more width, still one line.
            val maxWDp = if (sticky) 112f else 148f
            val location = IntArray(2)
            ballContainer.getLocationOnScreen(location)
            val pill = sidePillWindow ?: SidePillWindow(context, wm, type).also { sidePillWindow = it }
            pill.show(
                text = text,
                bgColor = bgColor,
                maxWidthDp = maxWDp,
                dockLeft = dockedLeft,
                ballCenterScreenX = location[0] + ballSizePx / 2,
                ballCenterScreenY = location[1] + ballSizePx / 2,
                ballRadiusPx = ballSizePx / 2
            )
        }
        val hide = Runnable {
            pillSticky = false
            hideToastRunnable = null
            sidePillWindow?.dismiss(animated = true)
            scheduleIdleCollapse()
        }
        hideToastRunnable = hide
        // Sticky: safety timeout only; normal tips auto-dismiss.
        handler.postDelayed(hide, durationMs)
    }

    private fun showActionMenu() {
        expandFromIdle()
        cancelIdleCollapse()
        if (menuState.isOpen || arcMenuWindow != null) return
        if (menuState.toggle() != ArcMenuTransition.OPEN) return

        val wm = windowManager
        val type = overlayType
        if (wm == null || type == null) {
            menuState.close()
            return
        }

        val location = IntArray(2)
        ballContainer.getLocationOnScreen(location)
        val menu = ArcMenuWindow(
            context = context,
            windowManager = wm,
            overlayType = type,
            dockLeft = dockedLeft,
            anchorScreenX = location[0] + ballSizePx / 2,
            anchorScreenY = location[1] + ballSizePx / 2,
            items = actionMenuItems(),
            onDismissed = {
                arcMenuWindow = null
                menuState.close()
                scheduleIdleCollapse()
            }
        )
        arcMenuWindow = menu
        runCatching { menu.show() }.onFailure {
            menu.close(animated = false)
        }
    }

    private fun hideActionMenu(animate: Boolean = true) {
        if (!menuState.isOpen && arcMenuWindow == null) return
        menuState.close()
        arcMenuWindow?.close(animated = animate)
    }

    private fun actionMenuItems(): List<ArcMenuItem> {
        return listOf(
            ArcMenuItem(R.drawable.ic_menu_capture, R.string.action_capture) {
                onCapture?.invoke()
            },
            ArcMenuItem(R.drawable.ic_menu_note_detail, R.string.action_latest_note) {
                onOpenLatestNote?.invoke()
            },
            ArcMenuItem(R.drawable.ic_menu_settings, R.string.action_settings) {
                onOpenSettings?.invoke()
            },
            ArcMenuItem(
                iconRes = R.drawable.ic_menu_exit,
                labelRes = R.string.action_exit,
                tintColor = 0xFFEF4444.toInt(),
                onClick = { onExit?.invoke() }
            )
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val lp = windowParams ?: return super.onTouchEvent(event)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pendingMenuOpenRunnable?.let { handler.removeCallbacks(it) }
                pendingMenuOpenRunnable = null
                downStartedFromIdle = idleCollapsed
                expandFromIdle()
                cancelIdleCollapse()
                // This fixed window owns only the ball; menu buttons live in another window.
                downRawX = event.rawX
                downRawY = event.rawY
                startParamX = lp.x
                startParamY = lp.y
                moved = false
                longPressTriggered = false
                scheduleLongPressDetection()
                ballContainer.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    pendingMenuOpenRunnable?.let { handler.removeCallbacks(it) }
                    pendingMenuOpenRunnable = null
                    cancelLongPressDetection()
                    // Close the independent menu before moving.
                    if (!moved && menuState.isOpen) hideActionMenu(animate = false)
                    moved = true
                }
                if (moved && !longPressTriggered) {
                    val gravityAdjustedDx = if (dockedLeft) dx else -dx
                    lp.x = (startParamX + gravityAdjustedDx).toInt()
                    lp.y = (startParamY + dy).toInt()
                    wm.updateViewLayout(this, lp)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPressDetection()
                ballContainer.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.4f))
                    .start()

                when {
                    longPressTriggered -> {
                        longPressTriggered = false
                        downStartedFromIdle = false
                    }
                    !moved && event.actionMasked == MotionEvent.ACTION_UP -> {
                        when {
                            badgeModel.visual == FeedbackBadgeVisual.FAILURE && !menuState.isOpen -> showFailReason()
                            menuState.isOpen -> hideActionMenu()
                            downStartedFromIdle -> {
                                val elapsedMs = event.eventTime - event.downTime
                                val remainingMs = ArcMenuDesign.remainingIdleExpandMs(elapsedMs)
                                scheduleMenuOpenAfterIdleExpansion(remainingMs)
                            }
                            else -> showActionMenu()
                        }
                    }
                    moved -> {
                        snapToEdge(wm, lp)
                        scheduleIdleCollapse(ArcMenuDesign.idleCollapseDelayMs + 220L)
                    }
                }
                downStartedFromIdle = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scheduleLongPressDetection() {
        cancelLongPressDetection()
        val runnable = Runnable {
            if (moved || longPressTriggered) return@Runnable
            longPressTriggered = true
            hideActionMenu(animate = false)
            cancelIdleCollapse()
            ballContainer.animate().cancel()
            ballContainer.scaleX = 1f
            ballContainer.scaleY = 1f
            val location = IntArray(2)
            ballContainer.getLocationOnScreen(location)
            onCaptureSelectionRequested?.invoke(location[1] + ballSizePx / 2)
        }
        longPressRunnable = runnable
        handler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun cancelLongPressDetection() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun snapToEdge(wm: WindowManager, lp: WindowManager.LayoutParams) {
        val dm = resources.displayMetrics
        val marginY = (8 * density).roundToInt()
        val currentStartX = DockedBallLayout.windowPlacement(
            dockLeft = dockedLeft,
            edgeOffsetPx = lp.x
        ).screenLeft(
            screenWidth = dm.widthPixels,
            windowWidth = lp.width
        )
        val currentBallLeft = DockedBallLayout.placement(
            dockLeft = dockedLeft,
            insetPx = (touchHotspotPx - ballSizePx) / 2
        ).screenLeft(
            windowX = currentStartX,
            windowWidth = lp.width,
            ballWidth = ballSizePx
        )
        val nextDockLeft = currentBallLeft + ballSizePx / 2 < dm.widthPixels / 2
        val startPlacement = DockedBallLayout.windowPlacementFromStartX(
            dockLeft = nextDockLeft,
            startX = currentStartX,
            screenWidth = dm.widthPixels,
            windowWidth = lp.width
        )
        val insetPx = (touchHotspotPx - ballSizePx) / 2
        val targetStartX = DockedBallLayout.dockedStartX(
            dockLeft = nextDockLeft,
            screenWidth = dm.widthPixels,
            windowWidth = lp.width,
            ballWidth = ballSizePx,
            insetPx = insetPx,
            visibleBallWidth = visibleWhenDockedPx
        )
        val targetX = DockedBallLayout.windowPlacementFromStartX(
            dockLeft = nextDockLeft,
            startX = targetStartX,
            screenWidth = dm.widthPixels,
            windowWidth = lp.width
        ).edgeOffsetPx
        val previousDockLeft = dockedLeft
        dockedLeft = nextDockLeft
        lp.gravity = Gravity.TOP or if (dockedLeft) Gravity.START else Gravity.END
        lp.x = startPlacement.edgeOffsetPx
        pinBallToDockEdge(dockedLeft)
        if (previousDockLeft != dockedLeft) {
            animateFeedbackBadgeDockChange(toDockLeft = dockedLeft)
        }
        val maxY = dm.heightPixels - ballSizePx - marginY
        val targetY = lp.y.coerceIn(marginY, maxY)
        val startXAnim = lp.x
        val startYAnim = lp.y
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                lp.x = (startXAnim + (targetX - startXAnim) * t).toInt()
                lp.y = (startYAnim + (targetY - startYAnim) * t).toInt()
                runCatching { wm.updateViewLayout(this@OverlayBallView, lp) }
            }
            start()
        }
    }

    private fun pinBallToDockEdge(dockLeft: Boolean) {
        val placement = DockedBallLayout.placement(
            dockLeft = dockLeft,
            insetPx = (touchHotspotPx - ballSizePx) / 2
        )
        val lp = ballContainer.layoutParams as LayoutParams
        lp.gravity = Gravity.CENTER_VERTICAL or
            if (placement.dockLeft) Gravity.START else Gravity.END
        lp.marginStart = if (placement.dockLeft) placement.insetPx else 0
        lp.marginEnd = if (placement.dockLeft) 0 else placement.insetPx
        ballContainer.layoutParams = lp
        positionFeedbackBadge(dockLeft)
    }

    private fun cancelIdleCollapse() {
        idleCollapseRunnable?.let(handler::removeCallbacks)
        idleCollapseRunnable = null
    }

    private fun scheduleIdleCollapse(delayMs: Long = ArcMenuDesign.idleCollapseDelayMs) {
        cancelIdleCollapse()
        val runnable = Runnable {
            idleCollapseRunnable = null
            if (canCollapseIdle()) collapseToIdle()
        }
        idleCollapseRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun canCollapseIdle(): Boolean =
        visibility == View.VISIBLE &&
            !menuState.isOpen &&
            hideToastRunnable == null &&
            !pillSticky &&
            badgeModel.visual != FeedbackBadgeVisual.SUCCESS &&
            badgeModel.visual != FeedbackBadgeVisual.FAILURE

    private fun collapseToIdle() {
        if (idleCollapsed || !canCollapseIdle()) return
        idleCollapsed = true

        // Idle keeps the same ball silhouette and center mark, but switches to the
        // high-blue side artwork so the small edge sliver remains visually obvious.
        iconView.setImageResource(R.drawable.ic_ball_idle)

        val idleVisiblePx =
            (ArcMenuDesign.idleDockedVisibleDp(currentBallSize.diameterDp) * density).roundToInt()
        val shift = (visibleWhenDockedPx - idleVisiblePx).coerceAtLeast(0).toFloat()
        val targetTranslation = if (dockedLeft) -shift else shift

        ballContainer.animate().cancel()
        ballContainer.animate()
            .translationX(targetTranslation)
            .alpha(ArcMenuDesign.idleBallAlpha)
            .scaleX(ArcMenuDesign.idleBallScale)
            .scaleY(ArcMenuDesign.idleBallScale)
            .setDuration(ArcMenuDesign.idleCollapseDurationMs)
            .setInterpolator(EXIT_EASING)
            .start()
    }

    private fun expandFromIdle(animated: Boolean = true) {
        cancelIdleCollapse()
        val needsExpansion = idleCollapsed ||
            ballContainer.translationX != 0f ||
            ballContainer.alpha != 1f ||
            ballContainer.scaleX != 1f
        idleCollapsed = false
        iconView.setImageResource(R.drawable.ic_ball_normal)
        if (!needsExpansion) return

        ballContainer.animate().cancel()
        if (!animated) {
            ballContainer.translationX = 0f
            ballContainer.alpha = 1f
            ballContainer.scaleX = 1f
            ballContainer.scaleY = 1f
            return
        }

        ballContainer.animate()
            .translationX(0f)
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ArcMenuDesign.idleExpandDurationMs)
            .setInterpolator(ENTER_EASING)
            .start()
    }

    private fun scheduleMenuOpenAfterIdleExpansion(delayMs: Long) {
        pendingMenuOpenRunnable?.let { handler.removeCallbacks(it) }
        val open = Runnable {
            pendingMenuOpenRunnable = null
            if (
                visibility == View.VISIBLE &&
                !moved &&
                !menuState.isOpen &&
                arcMenuWindow == null
            ) {
                showActionMenu()
            }
        }
        pendingMenuOpenRunnable = open
        if (delayMs <= 0L) {
            handler.post(open)
        } else {
            handler.postDelayed(open, delayMs)
        }
    }

    /** Tapping a failed ball reveals the reason, then restores the previous thumbnail state. */
    private fun showFailReason() {
        if (!badgeModel.consumeFailureReason()) return
        val reason = lastFailReason ?: context.getString(R.string.unauthorized_capture)
        val text = SpannableString("●  $reason").apply {
            setSpan(ForegroundColorSpan(FAILURE_CORAL), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val timeline = FeedbackMotion.failureRecovery
        showSidePill(text, FAILURE_PILL, timeline.pillHoldMs, sticky = false)

        normalizeRunnable?.let { handler.removeCallbacks(it) }
        badgeExitRunnable?.let { handler.removeCallbacks(it) }

        val badgeExit = Runnable {
            if (badgeModel.visual != FeedbackBadgeVisual.FAILURE) return@Runnable
            badgeModel.restoreDefault(persistThumbnail = feedbackBadgePersistent)
            transitionBadgeTo(
                target = badgeModel.visual,
                durationMs = timeline.badgeExitDurationMs
            )
        }
        badgeExitRunnable = badgeExit
        handler.postDelayed(badgeExit, timeline.badgeExitDelayMs)

        val reset = Runnable {
            // Visual recovery already finished through a crossfade above. Only clear
            // the consumed failure payload here so the final frame is never re-applied.
            lastFailReason = null
            scheduleIdleCollapse()
        }
        normalizeRunnable = reset
        handler.postDelayed(reset, timeline.resetDelayMs)
    }

    private fun scheduleSuccessExit() {
        normalizeRunnable?.let { handler.removeCallbacks(it) }
        val timeline = FeedbackMotion.successFeedback
        val r = Runnable {
            if (badgeModel.visual != FeedbackBadgeVisual.SUCCESS) return@Runnable
            badgeModel.restoreDefault(persistThumbnail = feedbackBadgePersistent)
            transitionBadgeTo(badgeModel.visual, timeline.exitDurationMs)
        }
        normalizeRunnable = r
        handler.postDelayed(r, timeline.exitDelayMs)
    }

    private fun cancelFeedbackState() {
        normalizeRunnable?.let { handler.removeCallbacks(it) }
        badgeExitRunnable?.let { handler.removeCallbacks(it) }
        feedbackTipRunnable?.let { handler.removeCallbacks(it) }
        normalizeRunnable = null
        badgeExitRunnable = null
        feedbackTipRunnable = null
        cancelFeedbackAnimations()
    }

    private fun cancelFeedbackAnimations() {
        feedbackBadge.animate().cancel()
        thumbBadge.animate().cancel()
        successBadge.animate().cancel()
        failureBadge.animate().cancel()
    }

    private fun transitionBadgeTo(target: FeedbackBadgeVisual, durationMs: Long) {
        positionFeedbackBadge(dockedLeft)
        cancelFeedbackAnimations()
        if (durationMs <= 0L) {
            applyBadgeVisualImmediately(target)
            return
        }

        if (target == FeedbackBadgeVisual.HIDDEN) {
            if (feedbackBadge.visibility != View.VISIBLE) {
                applyBadgeVisualImmediately(target)
                return
            }
            feedbackBadge.animate()
                .alpha(0f)
                .setDuration(durationMs)
                .setInterpolator(EXIT_EASING)
                .withEndAction {
                    if (badgeModel.visual == FeedbackBadgeVisual.HIDDEN) {
                        applyBadgeVisualImmediately(FeedbackBadgeVisual.HIDDEN)
                    }
                }
                .start()
            return
        }

        feedbackBadge.visibility = View.VISIBLE
        feedbackBadge.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(ENTER_EASING)
            .start()

        animateBadgeLayer(thumbBadge, target == FeedbackBadgeVisual.THUMBNAIL, durationMs)
        animateBadgeLayer(successBadge, target == FeedbackBadgeVisual.SUCCESS, durationMs)
        animateBadgeLayer(failureBadge, target == FeedbackBadgeVisual.FAILURE, durationMs)
    }

    private fun animateBadgeLayer(view: View, selected: Boolean, durationMs: Long) {
        view.visibility = View.VISIBLE
        view.animate().cancel()
        view.animate()
            .alpha(if (selected) 1f else 0f)
            .setDuration(durationMs)
            .setInterpolator(if (selected) ENTER_EASING else EXIT_EASING)
            .start()
    }

    private fun applyBadgeVisualImmediately(target: FeedbackBadgeVisual) {
        feedbackBadge.animate().cancel()
        val hidden = target == FeedbackBadgeVisual.HIDDEN
        feedbackBadge.visibility = if (hidden) View.GONE else View.VISIBLE
        feedbackBadge.alpha = if (hidden) 0f else 1f
        thumbBadge.alpha = if (target == FeedbackBadgeVisual.THUMBNAIL) 1f else 0f
        successBadge.alpha = if (target == FeedbackBadgeVisual.SUCCESS) 1f else 0f
        failureBadge.alpha = if (target == FeedbackBadgeVisual.FAILURE) 1f else 0f
    }

    private fun positionFeedbackBadge(dockLeft: Boolean) {
        val lp = feedbackBadge.layoutParams as LayoutParams
        val inwardCorner = FeedbackBadgePlacement.corner(dockLeft)
        lp.gravity = Gravity.TOP or when (inwardCorner) {
            FeedbackBadgeCorner.TOP_START -> Gravity.START
            FeedbackBadgeCorner.TOP_END -> Gravity.END
        }
        val overlap = (-1 * density).roundToInt()
        lp.marginStart = if (inwardCorner == FeedbackBadgeCorner.TOP_START) overlap else 0
        lp.marginEnd = if (inwardCorner == FeedbackBadgeCorner.TOP_END) overlap else 0
        lp.topMargin = overlap
        feedbackBadge.layoutParams = lp
    }


    private fun animateFeedbackBadgeDockChange(toDockLeft: Boolean) {
        badgeDockAnimator?.cancel()
        feedbackBadge.translationX = 0f
        feedbackBadge.translationY = 0f
        feedbackBadge.scaleX = 1f
        feedbackBadge.scaleY = 1f

        if (feedbackBadge.visibility != View.VISIBLE || feedbackBadge.alpha <= 0f) return

        val startTranslationX =
            FeedbackBadgeDockMotion.startTranslationDp(
                toDockLeft = toDockLeft,
                ballSizeDp = currentBallSize.diameterDp,
                badgeSizeDp = ArcMenuDesign.feedbackBadgeSizeDp
            ) * density
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FeedbackBadgeDockMotion.durationMs
            interpolator = ENTER_EASING
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                feedbackBadge.translationX = startTranslationX * (1f - progress)
                feedbackBadge.translationY =
                    FeedbackBadgeDockMotion.arcTranslationYDp(progress) * density
                feedbackBadge.scaleX = FeedbackBadgeDockMotion.scaleX(progress)
                feedbackBadge.scaleY = FeedbackBadgeDockMotion.scaleY(progress)
            }
        }
        badgeDockAnimator = animator
        animator.start()
    }

    companion object {
        const val PRIMARY_BLUE = 0xFF3B82F6.toInt()
        private const val SUCCESS_GREEN = 0xFF32E875.toInt()
        private const val FAILURE_CORAL = 0xFFF05A5A.toInt()
        private const val FAILURE_PILL = 0xEA262A33.toInt()
        private val ENTER_EASING = PathInterpolator(0.22f, 1f, 0.36f, 1f)
        private val EXIT_EASING = PathInterpolator(0.4f, 0f, 1f, 1f)
    }
}
