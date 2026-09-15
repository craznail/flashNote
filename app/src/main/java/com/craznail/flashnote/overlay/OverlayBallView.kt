package com.craznail.flashnote.overlay

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.craznail.flashnote.R
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Floating overlay ball — UI locked (0.1.11):
 * - Main ball 40dp; docked visible 28–30dp; frost #FFFFFF α=0.32 + stroke α=0.45 1dp
 * - Blue note mark ~55% (~22dp); soft low-contrast shadow; press scale 0.92
 * - Tap toggles arc glass menu (capture only via menu items)
 * - Sub-buttons 36dp, spacing ~8–10dp, expand 220ms ease-out toward screen center
 * - Sub fill α≈0.30 white; icons #1E293B; 退出 #EF4444
 * - Success: green check 420ms; failure: red flash 350ms
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
    private val ballSizePx = (40 * density).roundToInt()
    private val visibleWhenDockedPx = (29 * density).roundToInt() // mid of 28–30
    private val overhangPx = ballSizePx - visibleWhenDockedPx
    private val touchSlop = 8 * density
    private val subSizePx = (36 * density).roundToInt()
    private val iconMarkPx = (22 * density).roundToInt() // ~55% of 40dp
    private val arcRadiusPx = (64 * density).roundToInt()
    private val gapAlongArcDp = 9f // ~8–10dp chord spacing target via angle span

    // UI lock: normal #E8F2FF α0.55 + stroke white α0.5; success/fail #22C55E/#EF4444 α0.72; menu α0.45
    private val fillNormal = 0x8CE8F2FF.toInt()
    private val strokeNormal = 0x80FFFFFF.toInt()
    private val fillSuccess = 0xB822C55E.toInt()
    private val strokeSuccess = 0xE622C55E.toInt()
    private val fillFailure = 0xB8EF4444.toInt()
    private val strokeFailure = 0xE6EF4444.toInt()
    private val fillMain = fillNormal
    private val strokeMain = strokeNormal
    private val fillSub = 0x73E8F2FF.toInt() // α0.45
    private val strokeSub = 0x80FFFFFF.toInt()

    private val ballContainer: FrameLayout
    private val ballBg: View
    private val iconView: ImageView
    private val checkView: ImageView
    private val redDot: View
    private val thumbBadge: ImageView
    private val toastBar: TextView
    private val arcLayer: FrameLayout
    private val arcPathView: View
    private val menuButtons: List<ImageView>

    private var windowParams: WindowManager.LayoutParams? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var startParamX = 0
    private var startParamY = 0
    private var moved = false
    private var menuVisible = false
    private var exitArmed = false
    private val handler = Handler(Looper.getMainLooper())
    private var hideToastRunnable: Runnable? = null
    private var exitArmRunnable: Runnable? = null
    private var capturingHidden = false
    /** Window x/y while collapsed (hotspot-sized); restored on menu dismiss so ball does not jump. */
    /** True while collapse animation is in progress (blocks opportunistic shrink). */
    private var menuCollapsing = false
    /** Sticky side pill (remote summarizing) — stays until clearSidePill / success / fail. */
    private var pillSticky = false
    private var ballMood = BallMood.NORMAL
    private var lastFailReason: String? = null
    private var failMenuMode = false
    private var colorAnimator: ValueAnimator? = null
    private var normalizeRunnable: Runnable? = null
    private var currentFill: Int = 0x8CE8F2FF.toInt()
    private var currentStroke: Int = 0x80FFFFFF.toInt()

    private enum class BallMood { NORMAL, SUCCESS, FAILURE }

    private val touchHotspotPx = (48 * density).roundToInt()

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
        }

        iconView = ImageView(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(R.drawable.ic_ball_normal)
            scaleType = ImageView.ScaleType.FIT_XY
            contentDescription = context.getString(R.string.app_name)
        }

        checkView = ImageView(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx)
            setImageResource(R.drawable.ic_check_circle)
            visibility = View.GONE
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (6 * density).roundToInt()
            setPadding(pad, pad, pad, pad)
        }

        redDot = View(context).apply {
            val d = (8 * density).roundToInt()
            layoutParams = LayoutParams(d, d).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, (2 * density).roundToInt(), (2 * density).roundToInt(), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED)
            }
            visibility = View.GONE
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
        ballContainer.addView(checkView)
        ballContainer.addView(redDot)
        ballContainer.addView(thumbBadge)

        toastBar = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                (28 * density).roundToInt()
            ).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                marginEnd = ballSizePx + (8 * density).roundToInt()
            }
            maxWidth = (104 * density).roundToInt()
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(
                (10 * density).roundToInt(), 0,
                (10 * density).roundToInt(), 0
            )
            background = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(0xCC374151.toInt())
            }
            visibility = View.GONE
            alpha = 0f
            elevation = 4 * density
        }

        arcLayer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.GONE
            clipChildren = false
            clipToPadding = false
        }

        arcPathView = View(context).apply {
            // Thin optional arc — drawn as a transparent placeholder; positions use polar math
            layoutParams = LayoutParams(1, 1)
            visibility = View.GONE
        }
        arcLayer.addView(arcPathView)

        val specs = listOf(
            MenuSpec(R.drawable.ic_menu_image, R.string.action_image_only, false) {
                hideActionMenu()
                onSaveImageOnly?.invoke()
            },
            MenuSpec(R.drawable.ic_menu_summary, R.string.action_image_summary, false) {
                hideActionMenu()
                onSaveImageAndSummary?.invoke()
            },
            MenuSpec(R.drawable.ic_menu_settings, R.string.action_settings, false) {
                hideActionMenu()
                onOpenSettings?.invoke()
            },
            MenuSpec(R.drawable.ic_menu_exit, R.string.action_exit, true) {
                handleExitTap()
            }
        )

        menuButtons = specs.map { spec ->
            ImageView(context).apply {
                layoutParams = LayoutParams(subSizePx, subSizePx)
                background = glassOval(fillSub, strokeSub)
                val pad = (8 * density).roundToInt()
                setPadding(pad, pad, pad, pad)
                setImageResource(spec.iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = context.getString(spec.labelRes)
                elevation = 2 * density
                visibility = View.INVISIBLE
                scaleX = 0.4f
                scaleY = 0.4f
                alpha = 0f
                setOnClickListener { spec.onClick() }
                isClickable = true
                isFocusable = true
                arcLayer.addView(this)
            }
        }

        addView(arcLayer)
        addView(ballContainer)
        addView(toastBar)

        isClickable = true
        isFocusable = true
    }

    private data class MenuSpec(
        val iconRes: Int,
        val labelRes: Int,
        val isExit: Boolean,
        val onClick: () -> Unit
    )

    private fun glassOval(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke((1 * density).roundToInt().coerceAtLeast(1), stroke)
        }
    }

    private fun handleExitTap() {
        if (!exitArmed) {
            exitArmed = true
            showSystemTip(context.getString(R.string.exit_confirm_tip), 1_600L)
            exitArmRunnable?.let { handler.removeCallbacks(it) }
            val clear = Runnable { exitArmed = false }
            exitArmRunnable = clear
            handler.postDelayed(clear, 1_800L)
            return
        }
        exitArmed = false
        exitArmRunnable?.let { handler.removeCallbacks(it) }
        hideActionMenu()
        onExit?.invoke()
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            x = dm.widthPixels - visibleWhenDockedPx
            y = (dm.heightPixels * 0.35f).toInt()
        }
        windowParams = params
        wm.addView(this, params)
        return params
    }

    /** Hide ball + arc before MediaProjection grab so the ball is not in the shot. */
    fun hideForCapture() {
        capturingHidden = true
        if (menuVisible) {
            // Instant collapse without animation so capture path is fast
            menuVisible = false
            arcLayer.visibility = View.GONE
            menuButtons.forEach {
                it.visibility = View.INVISIBLE
                it.alpha = 0f
                it.scaleX = 0.85f
                it.scaleY = 0.85f
                it.translationX = 0f
                it.translationY = 0f
            }
            shrinkWindowIfIdle(force = true)
        }
        alpha = 0f
        visibility = View.INVISIBLE
    }

    fun showAfterCapture() {
        if (!capturingHidden) return
        capturingHidden = false
        visibility = View.VISIBLE
        alpha = 1f
    }

    /**
     * Success: green check 420ms at ball center only (no center toast).
     * Clears any loading pill. Optional [tipText] shows as a light side pill after the check
     * (fallback / not-configured tips only — never the default 「已保存」 copy).
     */
    fun showSuccessFeedback(tipText: String? = null, thumbnailPath: String? = null) {
        clearSidePill(immediate = true)
        failMenuMode = false
        lastFailReason = null
        ballMood = BallMood.SUCCESS
        checkView.visibility = View.GONE
        redDot.visibility = View.GONE
        iconView.visibility = View.VISIBLE
        applyBallArt()
        animateBallColors(fillSuccess, strokeSuccess, 160L)
        if (!thumbnailPath.isNullOrBlank() && File(thumbnailPath).exists()) {
            setThumbnailBadge(thumbnailPath)
        }
        scheduleNormalize(700L)
        val tip = tipText?.takeIf { it.isNotBlank() }
        if (tip != null) {
            handler.postDelayed({
                showSidePill(tip, 0xCC374151.toInt(), 1_600L, sticky = false)
            }, 650L)
        }
    }

    fun showFailureUnauthorized() {
        showFailure(context.getString(R.string.unauthorized_capture))
    }

    fun showFailure(reason: String) {
        clearSidePill(immediate = true)
        normalizeRunnable?.let { handler.removeCallbacks(it) }
        lastFailReason = reason
        ballMood = BallMood.FAILURE
        failMenuMode = false
        checkView.visibility = View.GONE
        redDot.visibility = View.GONE
        thumbBadge.visibility = View.GONE
        iconView.visibility = View.VISIBLE
        applyBallArt()
        animateBallColors(fillFailure, strokeFailure, 160L)
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

    private fun flashBallRed(durationMs: Long = 350L) {
        ballBg.background = glassOval(0xE6EF4444.toInt(), strokeMain)
        redDot.visibility = View.GONE
        handler.postDelayed({
            ballBg.background = glassOval(fillMain, strokeMain)
        }, durationMs)
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
        toastBar.animate().cancel()
        if (immediate || toastBar.visibility != View.VISIBLE) {
            toastBar.visibility = View.GONE
            toastBar.alpha = 0f
            shrinkWindowIfIdle()
        } else {
            toastBar.animate().alpha(0f).setDuration(120).withEndAction {
                toastBar.visibility = View.GONE
                shrinkWindowIfIdle()
            }.start()
        }
    }

    private fun showSidePill(
        text: String,
        bgColor: Int,
        durationMs: Long = 1_200L,
        sticky: Boolean = false
    ) {
        hideToastRunnable?.let { handler.removeCallbacks(it) }
        hideToastRunnable = null
        pillSticky = sticky
        // Loading stays compact; longer fallback tips get a bit more width, still one line.
        val maxWDp = if (sticky) 112f else 148f
        toastBar.maxWidth = (maxWDp * density).roundToInt()
        toastBar.maxLines = 1
        toastBar.ellipsize = TextUtils.TruncateAt.END
        val lp = windowParams
        val dm = resources.displayMetrics
        val onLeft = if (lp != null) {
            lp.x + (if (lp.width <= touchHotspotPx) ballSizePx else lp.width) / 2 < dm.widthPixels / 2
        } else {
            false
        }
        expandWindowForExtras(forMenu = false, dockLeft = onLeft)
        if (lp != null) {
            pinBallInWindow(onLeft, lp.width, lp.height)

            val tipLp = toastBar.layoutParams as LayoutParams
            if (onLeft) {
                tipLp.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                tipLp.marginStart = ballSizePx + (6 * density).roundToInt()
                tipLp.marginEnd = 0
            } else {
                tipLp.gravity = Gravity.CENTER_VERTICAL or Gravity.END
                tipLp.marginEnd = ballSizePx + (6 * density).roundToInt()
                tipLp.marginStart = 0
            }
            toastBar.layoutParams = tipLp
        }
        toastBar.text = text
        (toastBar.background as? GradientDrawable)?.setColor(bgColor)
        toastBar.visibility = View.VISIBLE
        toastBar.alpha = 0f
        toastBar.animate().alpha(1f).setDuration(120).start()
        val hide = Runnable {
            pillSticky = false
            toastBar.animate().alpha(0f).setDuration(160).withEndAction {
                toastBar.visibility = View.GONE
                shrinkWindowIfIdle()
            }.start()
        }
        hideToastRunnable = hide
        // Sticky: safety timeout only; normal tips auto-dismiss.
        handler.postDelayed(hide, durationMs)
    }

    private fun isDockedLeft(): Boolean {
        val lp = windowParams ?: return false
        val dm = resources.displayMetrics
        return lp.x + (if (lp.width <= touchHotspotPx) ballSizePx else lp.width) / 2 < dm.widthPixels / 2
    }

    /**
     * Angles in degrees, 0 = right, clockwise positive (Android y+ down).
     * Right-docked → open left (toward center), top→bottom.
     * Left-docked → open right, top→bottom.
     * Chord spacing ~8–10dp between 36dp buttons → angular step ≈ gap/R in rad.
     */
    private fun arcAnglesDegrees(dockLeft: Boolean): FloatArray {
        val r = arcRadiusPx.toFloat()
        // chord ≈ 2 R sin(Δ/2) ≈ gap + subSize → aim Δ so chord ≈ sub + 9dp
        val desiredChord = subSizePx + gapAlongArcDp * density
        val stepRad = 2.0 * Math.asin((desiredChord / (2.0 * r)).coerceIn(0.05, 0.95))
        val step = Math.toDegrees(stepRad).toFloat()
        val span = step * 3f
        return if (dockLeft) {
            // open right: center 0°, top = -span/2
            val start = -span / 2f
            FloatArray(4) { i -> start + step * i }
        } else {
            // open left: center 180°, top = 180 - span/2
            val start = 180f - span / 2f
            FloatArray(4) { i -> start + step * i }
        }
    }


    /**
     * On-screen top-left of the ball graphic (not the overlay window).
     * Used as the stable anchor for expand/shrink so gravity/size changes cannot jump the ball.
     */
    private fun ballScreenLeftTop(): Pair<Int, Int> {
        val wlp = windowParams ?: return 0 to 0
        val blp = ballContainer.layoutParams as LayoutParams
        val g = blp.gravity
        val left = when {
            (g and Gravity.END) == Gravity.END -> wlp.x + wlp.width - ballSizePx
            (g and Gravity.START) == Gravity.START &&
                (g and Gravity.CENTER_HORIZONTAL) != Gravity.CENTER_HORIZONTAL ->
                wlp.x + blp.leftMargin
            else -> wlp.x + (wlp.width - ballSizePx) / 2
        }
        val top = when {
            (g and Gravity.TOP) == Gravity.TOP &&
                (g and Gravity.CENTER_VERTICAL) != Gravity.CENTER_VERTICAL ->
                wlp.y + blp.topMargin
            else -> wlp.y + (wlp.height - ballSizePx) / 2
        }
        return left to top
    }

    /**
     * Pin [ballContainer] with TOP|START margins so position is independent of gravity switches.
     * @param dockLeft true/false = docked edge in an expanded window; null = centered (collapsed).
     */
    private fun pinBallInWindow(dockLeft: Boolean?, winW: Int, winH: Int) {
        val blp = ballContainer.layoutParams as LayoutParams
        blp.width = ballSizePx
        blp.height = ballSizePx
        blp.gravity = Gravity.TOP or Gravity.START
        blp.leftMargin = when (dockLeft) {
            true -> 0
            false -> (winW - ballSizePx).coerceAtLeast(0)
            null -> (winW - ballSizePx) / 2
        }
        blp.topMargin = (winH - ballSizePx) / 2
        ballContainer.layoutParams = blp
    }

    /**
     * Resize/move the overlay window so the ball's on-screen top-left stays at [ballLeft],[ballTop].
     * @param dockLeft null → collapsed hotspot centered on the ball; true/false → expanded, ball on that edge.
     */
    private fun placeWindowAnchoredToBall(
        ballLeft: Int,
        ballTop: Int,
        winW: Int,
        winH: Int,
        dockLeft: Boolean?
    ) {
        val lp = windowParams ?: return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lp.width = winW
        lp.height = winH
        when (dockLeft) {
            true -> {
                lp.x = ballLeft
                lp.y = ballTop - (winH - ballSizePx) / 2
            }
            false -> {
                lp.x = ballLeft + ballSizePx - winW
                lp.y = ballTop - (winH - ballSizePx) / 2
            }
            null -> {
                lp.x = ballLeft - (winW - ballSizePx) / 2
                lp.y = ballTop - (winH - ballSizePx) / 2
            }
        }
        runCatching { wm.updateViewLayout(this, lp) }
        pinBallInWindow(dockLeft, winW, winH)
    }

    private fun showActionMenu() {
        menuVisible = true
        exitArmed = false
        val dockLeft = isDockedLeft()
        expandWindowForExtras(forMenu = true, dockLeft = dockLeft)

        // Ball center inside expanded window (ball pinned to dock edge via margins)
        val winW = windowParams?.width ?: width
        val winH = windowParams?.height ?: height
        val cx = if (dockLeft) ballSizePx / 2f else winW - ballSizePx / 2f
        val cy = winH / 2f

        val angles = arcAnglesDegrees(dockLeft)
        arcLayer.visibility = View.VISIBLE

        menuButtons.forEachIndexed { i, btn ->
            val rad = Math.toRadians(angles[i].toDouble())
            val bx = cx + (arcRadiusPx * cos(rad)).toFloat() - subSizePx / 2f
            val by = cy + (arcRadiusPx * sin(rad)).toFloat() - subSizePx / 2f
            val lp = btn.layoutParams as LayoutParams
            lp.gravity = Gravity.TOP or Gravity.START
            lp.leftMargin = bx.roundToInt()
            lp.topMargin = by.roundToInt()
            lp.width = subSizePx
            lp.height = subSizePx
            btn.layoutParams = lp
            // Start near ball center, glide out along arc with soft scale/alpha (no bounce)
            val fromTx = (cx - subSizePx / 2f) - bx
            val fromTy = (cy - subSizePx / 2f) - by
            btn.animate().cancel()
            btn.visibility = View.VISIBLE
            btn.alpha = 0f
            btn.scaleX = 0.85f
            btn.scaleY = 0.85f
            btn.translationX = fromTx
            btn.translationY = fromTy
            btn.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(260)
                .setStartDelay((i * 28).toLong())
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }

    private fun hideActionMenu(animate: Boolean = true) {
        if (!menuVisible && arcLayer.visibility != View.VISIBLE) return
        menuVisible = false
        menuCollapsing = true
        exitArmed = false
        if (!animate) {
            menuButtons.forEach {
                it.animate().cancel()
                it.visibility = View.INVISIBLE
                it.alpha = 0f
                it.scaleX = 0.85f
                it.scaleY = 0.85f
                it.translationX = 0f
                it.translationY = 0f
            }
            arcLayer.visibility = View.GONE
            shrinkWindowIfIdle(force = true)
            return
        }
        val dockLeft = isDockedLeft()
        val winW = windowParams?.width ?: width
        val winH = windowParams?.height ?: height
        val cx = if (dockLeft) ballSizePx / 2f else winW - ballSizePx / 2f
        val cy = winH / 2f
        var pending = menuButtons.count { it.visibility == View.VISIBLE }
        if (pending == 0) {
            arcLayer.visibility = View.GONE
            shrinkWindowIfIdle(force = true)
            return
        }
        menuButtons.forEachIndexed { i, btn ->
            if (btn.visibility != View.VISIBLE) return@forEachIndexed
            val lp = btn.layoutParams as LayoutParams
            val toTx = (cx - subSizePx / 2f) - lp.leftMargin
            val toTy = (cy - subSizePx / 2f) - lp.topMargin
            btn.animate().cancel()
            btn.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .translationX(toTx)
                .translationY(toTy)
                .setDuration(220)
                .setStartDelay(((menuButtons.size - 1 - i) * 20).toLong().coerceAtLeast(0))
                .setInterpolator(FastOutSlowInInterpolator())
                .withEndAction {
                    btn.visibility = View.INVISIBLE
                    btn.translationX = 0f
                    btn.translationY = 0f
                    pending--
                    if (pending <= 0) {
                        arcLayer.visibility = View.GONE
                        shrinkWindowIfIdle(force = true)
                    }
                }
                .start()
        }
    }

    private fun expandWindowForExtras(forMenu: Boolean = true, dockLeft: Boolean = false) {
        val lp = windowParams ?: return
        val needW: Int
        val needH: Int
        if (forMenu) {
            val half = (arcRadiusPx + subSizePx / 2 + (8 * density).roundToInt())
            needW = ballSizePx + half + (4 * density).roundToInt()
            needH = (2 * (arcRadiusPx + subSizePx / 2) + ballSizePx / 2)
                .coerceAtLeast(touchHotspotPx)
        } else {
            val sideExtra = 156
            needW = ballSizePx + (8 * density).roundToInt() + (sideExtra * density).roundToInt()
            needH = touchHotspotPx.coerceAtLeast(ballSizePx)
        }
        if (lp.width < needW || lp.height < needH) {
            // Anchor on the ball's current screen position — never on stale window x/y.
            val (ballLeft, ballTop) = ballScreenLeftTop()
            placeWindowAnchoredToBall(ballLeft, ballTop, needW, needH, dockLeft)
        }
    }

    private fun shrinkWindowIfIdle(force: Boolean = false) {
        if (!force && (menuVisible || menuCollapsing || toastBar.visibility == View.VISIBLE)) return
        val lp = windowParams ?: return
        if (lp.width == touchHotspotPx && lp.height == touchHotspotPx) {
            // Already collapsed — still normalize pin, clear collapsing flag.
            pinBallInWindow(null, touchHotspotPx, touchHotspotPx)
            menuCollapsing = false
            return
        }
        // Critical: measure ball where it is NOW (after drag / mid-collapse), then shrink
        // the window around that screen point. Restoring expand-time window x/y was the
        // jump: gravity CENTER vs START/END + stale Y from height growth.
        val (ballLeft, ballTop) = ballScreenLeftTop()
        placeWindowAnchoredToBall(ballLeft, ballTop, touchHotspotPx, touchHotspotPx, dockLeft = null)
        menuCollapsing = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val lp = windowParams ?: return super.onTouchEvent(event)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // If menu open and touch is outside sub-buttons / on blank → dismiss.
                // Touches on sub-buttons are handled by their click listeners (they get events first
                // only if within their bounds inside this view). For the ball itself we toggle.
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
                    if (!moved && menuVisible) {
                        // Collapse onto the ball's current screen position first, then drag
                        // from the resulting collapsed window coords (no inward jump).
                        hideActionMenu(animate = false)
                        startParamX = lp.x
                        startParamY = lp.y
                    }
                    moved = true
                }
                if (moved) {
                    if (lp.width != touchHotspotPx) {
                        lp.width = touchHotspotPx
                        lp.height = touchHotspotPx
                    }
                    lp.x = (startParamX + dx).toInt()
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
                            ballMood == BallMood.FAILURE && !menuVisible -> openFailFlow()
                            menuVisible && failMenuMode -> dismissFailFlow()
                            menuVisible -> hideActionMenu()
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
        val targetX = if (lp.x + ballSizePx / 2 < dm.widthPixels / 2) {
            -overhangPx
        } else {
            dm.widthPixels - visibleWhenDockedPx
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

    private fun openFailFlow() {
        iconView.setColorFilter(0xFFFFFFFF.toInt())
        iconView.setImageResource(android.R.drawable.ic_dialog_alert)
        handler.postDelayed({
            if (ballMood != BallMood.FAILURE) return@postDelayed
            failMenuMode = true
            menuButtons.forEachIndexed { i, btn ->
                if (i == 0) {
                    btn.setImageResource(android.R.drawable.ic_menu_info_details)
                    btn.clearColorFilter()
                    btn.setColorFilter(0xFF1E293B.toInt())
                    btn.contentDescription = context.getString(R.string.view_fail_reason)
                    btn.setOnClickListener {
                        val r = lastFailReason ?: context.getString(R.string.unauthorized_capture)
                        showSidePill(r, 0xCC374151.toInt(), 2_000L, sticky = false)
                    }
                }
            }
            showActionMenu()
            handler.post {
                menuButtons.drop(1).forEach {
                    it.visibility = View.GONE
                    it.alpha = 0f
                }
            }
        }, 220L)
    }

    private fun dismissFailFlow() {
        hideActionMenu()
        failMenuMode = false
        restoreNormalMenuButtons()
        iconView.setImageResource(R.drawable.ic_flash_note)
        iconView.clearColorFilter()
        lastFailReason = null
        ballMood = BallMood.NORMAL
        applyBallArt()
        animateBallColors(fillNormal, strokeNormal, 420L)
    }

    private fun restoreNormalMenuButtons() {
        val icons = listOf(
            R.drawable.ic_menu_image to R.string.action_image_only,
            R.drawable.ic_menu_summary to R.string.action_image_summary,
            R.drawable.ic_menu_settings to R.string.action_settings,
            R.drawable.ic_menu_exit to R.string.action_exit
        )
        menuButtons.forEachIndexed { i, btn ->
            val (icon, label) = icons[i]
            btn.setImageResource(icon)
            btn.contentDescription = context.getString(label)
            if (i == 3) btn.setColorFilter(0xFFEF4444.toInt()) else btn.clearColorFilter()
            btn.setOnClickListener {
                when (i) {
                    0 -> { hideActionMenu(); onSaveImageOnly?.invoke() }
                    1 -> { hideActionMenu(); onSaveImageAndSummary?.invoke() }
                    2 -> { hideActionMenu(); onOpenSettings?.invoke() }
                    3 -> handleExitTap()
                }
            }
            btn.visibility = View.INVISIBLE
        }
    }

    private fun scheduleNormalize(delayMs: Long) {
        normalizeRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (ballMood == BallMood.FAILURE) return@Runnable
            ballMood = BallMood.NORMAL
            applyBallArt()
            animateBallColors(fillNormal, strokeNormal, 400L)
        }
        normalizeRunnable = r
        handler.postDelayed(r, delayMs)
    }


    /** Normal = full circular logo; success/fail = colored plate + small mark. */
    private fun applyBallArt() {
        when (ballMood) {
            BallMood.NORMAL -> {
                ballBg.visibility = View.GONE
                val lp = iconView.layoutParams as LayoutParams
                lp.width = ballSizePx
                lp.height = ballSizePx
                iconView.layoutParams = lp
                iconView.setImageResource(R.drawable.ic_ball_normal)
                iconView.clearColorFilter()
                iconView.scaleType = ImageView.ScaleType.FIT_XY
            }
            BallMood.SUCCESS, BallMood.FAILURE -> {
                ballBg.visibility = View.VISIBLE
                val lp = iconView.layoutParams as LayoutParams
                lp.width = iconMarkPx
                lp.height = iconMarkPx
                iconView.layoutParams = lp
                iconView.setImageResource(R.drawable.ic_flash_note)
                iconView.clearColorFilter()
                iconView.scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
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
    }
}
