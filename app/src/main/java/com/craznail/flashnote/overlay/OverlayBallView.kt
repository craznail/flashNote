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
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.craznail.flashnote.R
import com.craznail.flashnote.data.OverlayPreferences
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating overlay ball:
 * - Main ball is always the 44dp ice-blue note + lightning identity; touch target stays 56dp
 * - Docked visible diameter is 33dp; press scale 0.92
 * - Tap toggles arc glass menu (capture only via menu items)
 * - Sub-buttons stay 40dp on a half-ring around the ball
 * - A single 14dp inward-corner badge carries thumbnail / success / failure feedback
 */
class OverlayBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onSaveImageOnly: (() -> Unit)? = null
    var onSaveImageAndSummary: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null
    var onExit: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val ballSizePx = (ArcMenuDesign.ballSizeDp * density).roundToInt()
    private val visibleWhenDockedPx = (ArcMenuDesign.dockedVisibleDp * density).roundToInt()
    private val touchSlop = 8 * density
    private val ballContainer: FrameLayout
    private val iconView: ImageView
    private val feedbackBadge: FrameLayout
    private val thumbBadge: ImageView
    private val successBadge: View
    private val failureBadge: TextView
    private val badgeModel = FeedbackBadgeModel()
    private var feedbackBadgePersistent =
        OverlayPreferences.get(context).feedbackBadgePersistent.value
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
            OverlayCapturePolicy.secureFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_SECURE
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
        return params
    }

    override fun onDetachedFromWindow() {
        hideActionMenu(animate = false)
        hideToastRunnable?.let { handler.removeCallbacks(it) }
        normalizeRunnable?.let { handler.removeCallbacks(it) }
        badgeExitRunnable?.let { handler.removeCallbacks(it) }
        feedbackTipRunnable?.let { handler.removeCallbacks(it) }
        cancelFeedbackAnimations()
        sidePillWindow?.dismiss(animated = false)
        sidePillWindow = null
        windowManager = null
        super.onDetachedFromWindow()
    }

    /**
     * Success is carried entirely by the 14dp badge; the primary ball never changes colour.
     * A new thumbnail is installed underneath the success layer before the crossfade starts.
     */
    fun showSuccessFeedback(tipText: String? = null, thumbnailPath: String? = null) {
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
        clearSidePill(immediate = true)
        cancelFeedbackState()
        lastFailReason = reason
        badgeModel.showFailure()
        transitionBadgeTo(FeedbackBadgeVisual.FAILURE, FeedbackMotion.badgeEnterDurationMs)
        showSidePill(
            context.getString(R.string.failure_tap_for_reason),
            FAILURE_PILL,
            durationMs = 1_200L,
            sticky = false
        )
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
    }

    private fun showSidePill(
        text: CharSequence,
        bgColor: Int,
        durationMs: Long = 1_200L,
        sticky: Boolean = false
    ) {
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
            sidePillWindow?.dismiss(animated = true)
        }
        hideToastRunnable = hide
        // Sticky: safety timeout only; normal tips auto-dismiss.
        handler.postDelayed(hide, durationMs)
    }

    private fun showActionMenu() {
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
            ArcMenuItem(R.drawable.ic_menu_image, R.string.action_image_only) {
                onSaveImageOnly?.invoke()
            },
            ArcMenuItem(R.drawable.ic_menu_summary, R.string.action_image_summary) {
                onSaveImageAndSummary?.invoke()
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
                // This fixed window owns only the ball; menu buttons live in another window.
                downRawX = event.rawX
                downRawY = event.rawY
                startParamX = lp.x
                startParamY = lp.y
                moved = false
                ballContainer.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    // Close the independent menu before moving.
                    if (!moved && menuState.isOpen) hideActionMenu(animate = false)
                    moved = true
                }
                if (moved) {
                    val gravityAdjustedDx = if (dockedLeft) dx else -dx
                    lp.x = (startParamX + gravityAdjustedDx).toInt()
                    lp.y = (startParamY + dy).toInt()
                    wm.updateViewLayout(this, lp)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ballContainer.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.4f))
                    .start()

                when {
                    !moved && event.actionMasked == MotionEvent.ACTION_UP -> {
                        when {
                            badgeModel.visual == FeedbackBadgeVisual.FAILURE && !menuState.isOpen -> showFailReason()
                            menuState.isOpen -> hideActionMenu()
                            else -> showActionMenu()
                        }
                    }
                    moved -> snapToEdge(wm, lp)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
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
        val rightDockStartX = dm.widthPixels - visibleWhenDockedPx
        val targetStartX = DockedBallLayout.dockedStartX(
            dockLeft = nextDockLeft,
            screenWidth = dm.widthPixels,
            windowWidth = lp.width,
            rightDockStartX = rightDockStartX
        )
        val targetX = DockedBallLayout.windowPlacementFromStartX(
            dockLeft = nextDockLeft,
            startX = targetStartX,
            screenWidth = dm.widthPixels,
            windowWidth = lp.width
        ).edgeOffsetPx
        dockedLeft = nextDockLeft
        lp.gravity = Gravity.TOP or if (dockedLeft) Gravity.START else Gravity.END
        lp.x = startPlacement.edgeOffsetPx
        pinBallToDockEdge(dockedLeft)
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
            feedbackBadge.animate().cancel()
            feedbackBadge.animate()
                .alpha(0f)
                .setDuration(timeline.badgeExitDurationMs)
                .setInterpolator(EXIT_EASING)
                .start()
        }
        badgeExitRunnable = badgeExit
        handler.postDelayed(badgeExit, timeline.badgeExitDelayMs)

        val reset = Runnable {
            if (badgeModel.visual != FeedbackBadgeVisual.FAILURE) return@Runnable
            lastFailReason = null
            badgeModel.restoreDefault(persistThumbnail = feedbackBadgePersistent)
            applyBadgeVisualImmediately(badgeModel.visual)
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


    companion object {
        const val PRIMARY_BLUE = 0xFF3B82F6.toInt()
        private const val SUCCESS_GREEN = 0xFF32E875.toInt()
        private const val FAILURE_CORAL = 0xFFF05A5A.toInt()
        private const val FAILURE_PILL = 0xEA262A33.toInt()
        private val ENTER_EASING = PathInterpolator(0.22f, 1f, 0.36f, 1f)
        private val EXIT_EASING = PathInterpolator(0.4f, 0f, 1f, 1f)
    }
}
