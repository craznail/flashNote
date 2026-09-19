package com.craznail.flashnote.overlay

import android.animation.ArgbEvaluator
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
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating overlay ball — UI locked (0.1.11):
 * - Main ball 48dp; docked visible ~32dp; note + lightning identity
 * - Normal and success layers share one fixed footprint; press scale 0.92
 * - Tap toggles arc glass menu (capture only via menu items)
 * - Sub-buttons 40dp on a half-ring around the ball; dark smoked glass with light icons
 * - Success: held green plate with a soft crossfade; failure: neutral ball + compact coral badge
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
    private val visibleWhenDockedPx = (36 * density).roundToInt()
    private val overhangPx = ballSizePx - visibleWhenDockedPx
    private val touchSlop = 8 * density
    // UI lock: normal #E8F2FF α0.55 + stroke white α0.5; success #22C55E α0.72
    private val fillNormal = 0x8CE8F2FF.toInt()
    private val strokeNormal = 0x80FFFFFF.toInt()
    private val fillSuccess = 0xB822C55E.toInt()
    private val strokeSuccess = 0xE622C55E.toInt()
    private val ballContainer: FrameLayout
    private val ballBg: View
    private val iconView: ImageView
    private val successIconView: ImageView
    private val redDot: TextView
    private val thumbBadge: ImageView
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
    private var ballMood = BallMood.NORMAL
    private var lastFailReason: String? = null
    private var colorAnimator: ValueAnimator? = null
    private var normalizeRunnable: Runnable? = null
    private var badgeExitRunnable: Runnable? = null
    private var currentFill: Int = 0x8CE8F2FF.toInt()
    private var currentStroke: Int = 0x80FFFFFF.toInt()

    private enum class BallMood { NORMAL, SUCCESS, FAILURE }

    private val touchHotspotPx = (ArcMenuDesign.ballTouchSizeDp * density).roundToInt()

    init {
        clipChildren = false
        clipToPadding = false

        ballContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx).apply {
                gravity = Gravity.CENTER
            }
            elevation = 3 * density // soft low-contrast shadow
        }

        currentFill = fillNormal
        currentStroke = strokeNormal
        ballBg = View(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx)
            background = glassOval(currentFill, currentStroke)
            // Normal uses full circular logo asset; plate only for green/red states
            visibility = View.GONE
            alpha = 0f
        }

        iconView = ImageView(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(R.drawable.ic_ball_normal)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = context.getString(R.string.app_name)
        }

        successIconView = ImageView(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx)
            setImageResource(R.drawable.ic_ball_lightning)
            setColorFilter(0xFFFFFFFF.toInt())
            visibility = View.GONE
            alpha = 0f
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = (11 * density).roundToInt()
            setPadding(pad, pad, pad, pad)
        }

        redDot = TextView(context).apply {
            val d = (13 * density).roundToInt()
            layoutParams = LayoutParams(d, d).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins((1 * density).roundToInt(), (1 * density).roundToInt(), 0, 0)
            }
            text = "!"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFDFDFD.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(FAILURE_CORAL)
                setStroke((1 * density).roundToInt().coerceAtLeast(1), 0xB3FFFFFF.toInt())
            }
            elevation = 3 * density
            visibility = View.GONE
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
        }

        thumbBadge = ImageView(context).apply {
            val d = (14 * density).roundToInt()
            layoutParams = LayoutParams(d, d).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, (-1 * density).roundToInt(), (-1 * density).roundToInt(), 0)
            }
            visibility = View.GONE
            scaleType = ImageView.ScaleType.CENTER_CROP
            elevation = 2 * density
        }

        ballContainer.addView(ballBg)
        ballContainer.addView(iconView)
        ballContainer.addView(successIconView)
        ballContainer.addView(redDot)
        ballContainer.addView(thumbBadge)

        addView(ballContainer)

        isClickable = true
        isFocusable = true
    }

    private fun glassOval(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke((1 * density).roundToInt().coerceAtLeast(1), stroke)
        }
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
        badgeExitRunnable?.let { handler.removeCallbacks(it) }
        sidePillWindow?.dismiss(animated = false)
        sidePillWindow = null
        windowManager = null
        super.onDetachedFromWindow()
    }

    /**
     * Success: full-size green state held long enough to read, then crossfaded back.
     * Clears any loading pill. Optional [tipText] shows as a light side pill after the check
     * (fallback / not-configured tips only — never the default 「已保存」 copy).
     */
    fun showSuccessFeedback(tipText: String? = null, thumbnailPath: String? = null) {
        clearSidePill(immediate = true)
        cancelBallStateAnimations()
        normalizeRunnable?.let { handler.removeCallbacks(it) }
        lastFailReason = null
        ballMood = BallMood.SUCCESS
        hideFailureBadge(immediate = true)
        iconView.visibility = View.VISIBLE
        applyBallArt()
        val timeline = FeedbackMotion.successFeedback
        animateBallColors(fillSuccess, strokeSuccess, timeline.enterDurationMs)
        iconView.animate()
            .alpha(0f)
            .setDuration(timeline.enterDurationMs)
            .setInterpolator(ENTER_EASING)
            .start()
        ballBg.animate()
            .alpha(1f)
            .setDuration(timeline.enterDurationMs)
            .setInterpolator(ENTER_EASING)
            .start()
        successIconView.animate()
            .alpha(1f)
            .setDuration(timeline.enterDurationMs)
            .setInterpolator(ENTER_EASING)
            .start()
        if (!thumbnailPath.isNullOrBlank() && File(thumbnailPath).exists()) {
            setThumbnailBadge(thumbnailPath)
        }
        scheduleSuccessExit()
        val tip = tipText?.takeIf { it.isNotBlank() }
        if (tip != null) {
            handler.postDelayed({
                showSidePill(tip, 0xCC374151.toInt(), 1_600L, sticky = false)
            }, timeline.exitDelayMs)
        }
    }

    fun showFailureUnauthorized() {
        showFailure(context.getString(R.string.unauthorized_capture))
    }

    fun showFailure(reason: String) {
        clearSidePill(immediate = true)
        normalizeRunnable?.let { handler.removeCallbacks(it) }
        badgeExitRunnable?.let { handler.removeCallbacks(it) }
        cancelBallStateAnimations()
        lastFailReason = reason
        ballMood = BallMood.FAILURE
        thumbBadge.visibility = View.GONE
        iconView.visibility = View.VISIBLE
        applyBallArt()
        showFailureBadge()
    }

    fun showSystemTip(text: String, durationMs: Long = 1_200L) {
        showSidePill(text, 0xCC374151.toInt(), durationMs, sticky = false)
    }

    fun setThumbnailBadge(path: String) {
        try {
            val bmp = android.graphics.BitmapFactory.decodeFile(path) ?: return
            val size = (14 * density).roundToInt()
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, size, size, true)
            if (scaled != bmp) bmp.recycle()
            val drawable = RoundedBitmapDrawableFactory.create(resources, scaled).apply {
                isCircular = true
            }
            thumbBadge.setImageDrawable(drawable)
            thumbBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((1.5f * density).roundToInt().coerceAtLeast(1), 0xFFFFFFFF.toInt())
            }
            thumbBadge.visibility = View.VISIBLE
            thumbBadge.alpha = 1f
        } catch (_: Exception) {
            // ignore
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
                            ballMood == BallMood.FAILURE && !menuState.isOpen -> showFailReason()
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
        val targetStartX = if (nextDockLeft) {
            -overhangPx
        } else {
            dm.widthPixels - visibleWhenDockedPx
        }
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
        positionFailureBadge(dockLeft)
    }

    /** Tapping a failed ball reveals the reason, then gently returns to normal. */
    private fun showFailReason() {
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
            if (ballMood == BallMood.FAILURE) hideFailureBadge(immediate = false)
        }
        badgeExitRunnable = badgeExit
        handler.postDelayed(badgeExit, timeline.badgeExitDelayMs)
        val reset = Runnable {
            if (ballMood != BallMood.FAILURE) return@Runnable
            lastFailReason = null
            ballMood = BallMood.NORMAL
            applyBallArt()
            hideFailureBadge(immediate = true)
        }
        normalizeRunnable = reset
        handler.postDelayed(reset, timeline.resetDelayMs)
    }

    private fun scheduleSuccessExit() {
        normalizeRunnable?.let { handler.removeCallbacks(it) }
        val timeline = FeedbackMotion.successFeedback
        val r = Runnable {
            if (ballMood != BallMood.SUCCESS) return@Runnable
            iconView.animate()
                .alpha(1f)
                .setDuration(timeline.exitDurationMs)
                .setInterpolator(EXIT_EASING)
                .start()
            successIconView.animate()
                .alpha(0f)
                .setDuration(timeline.exitDurationMs)
                .setInterpolator(EXIT_EASING)
                .start()
            animateBallColors(fillNormal, strokeNormal, timeline.exitDurationMs)
            ballBg.animate()
                .alpha(0f)
                .setDuration(timeline.exitDurationMs)
                .setInterpolator(EXIT_EASING)
                .withEndAction {
                    if (ballMood != BallMood.SUCCESS) return@withEndAction
                    ballMood = BallMood.NORMAL
                    applyBallArt()
                }
                .start()
        }
        normalizeRunnable = r
        handler.postDelayed(r, timeline.exitDelayMs)
    }


    /** The glass ball remains visually continuous; failure is carried by its badge. */
    private fun applyBallArt() {
        when (ballMood) {
            BallMood.NORMAL, BallMood.FAILURE -> {
                ballBg.visibility = View.GONE
                ballBg.alpha = 0f
                successIconView.visibility = View.GONE
                successIconView.alpha = 0f
                val lp = iconView.layoutParams as LayoutParams
                lp.width = ballSizePx
                lp.height = ballSizePx
                iconView.layoutParams = lp
                iconView.setImageResource(R.drawable.ic_ball_normal)
                iconView.clearColorFilter()
                iconView.scaleType = ImageView.ScaleType.FIT_CENTER
                iconView.alpha = 1f
            }
            BallMood.SUCCESS -> {
                ballBg.visibility = View.VISIBLE
                ballBg.alpha = 0f
                successIconView.visibility = View.VISIBLE
                successIconView.alpha = 0f
                val lp = iconView.layoutParams as LayoutParams
                lp.width = ballSizePx
                lp.height = ballSizePx
                iconView.layoutParams = lp
                iconView.setImageResource(R.drawable.ic_ball_normal)
                iconView.clearColorFilter()
                iconView.scaleType = ImageView.ScaleType.FIT_CENTER
                iconView.alpha = 1f
            }
        }
    }

    private fun cancelBallStateAnimations() {
        iconView.animate().cancel()
        ballBg.animate().cancel()
        successIconView.animate().cancel()
        colorAnimator?.cancel()
    }

    private fun showFailureBadge() {
        positionFailureBadge(dockedLeft)
        redDot.animate().cancel()
        redDot.visibility = View.VISIBLE
        redDot.alpha = 0f
        redDot.scaleX = 0.72f
        redDot.scaleY = 0.72f
        redDot.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(FeedbackMotion.badgeEnterDurationMs)
            .setInterpolator(ENTER_EASING)
            .start()
    }

    private fun hideFailureBadge(immediate: Boolean) {
        redDot.animate().cancel()
        if (immediate || redDot.visibility != View.VISIBLE) {
            redDot.visibility = View.GONE
            redDot.alpha = 0f
            redDot.scaleX = 0.72f
            redDot.scaleY = 0.72f
            return
        }
        redDot.animate()
            .alpha(0f)
            .scaleX(0.82f)
            .scaleY(0.82f)
            .setDuration(FeedbackMotion.failureRecovery.badgeExitDurationMs)
            .setInterpolator(EXIT_EASING)
            .withEndAction { redDot.visibility = View.GONE }
            .start()
    }

    private fun positionFailureBadge(dockLeft: Boolean) {
        val lp = redDot.layoutParams as LayoutParams
        lp.gravity = Gravity.TOP or if (dockLeft) Gravity.END else Gravity.START
        lp.marginStart = if (dockLeft) 0 else (1 * density).roundToInt()
        lp.marginEnd = if (dockLeft) (1 * density).roundToInt() else 0
        lp.topMargin = (1 * density).roundToInt()
        redDot.layoutParams = lp
    }

    private fun animateBallColors(toFill: Int, toStroke: Int, durationMs: Long) {
        colorAnimator?.cancel()
        val fromFill = currentFill
        val fromStroke = currentStroke
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                val eval = ArgbEvaluator()
                currentFill = eval.evaluate(f, fromFill, toFill) as Int
                currentStroke = eval.evaluate(f, fromStroke, toStroke) as Int
                ballBg.background = glassOval(currentFill, currentStroke)
            }
        }
        colorAnimator = anim
        anim.start()
    }


    companion object {
        const val PRIMARY_BLUE = 0xFF3B82F6.toInt()
        private const val FAILURE_CORAL = 0xFFF05A5A.toInt()
        private const val FAILURE_PILL = 0xEA262A33.toInt()
        private val ENTER_EASING = PathInterpolator(0.22f, 1f, 0.36f, 1f)
        private val EXIT_EASING = PathInterpolator(0.4f, 0f, 1f, 1f)
    }
}
