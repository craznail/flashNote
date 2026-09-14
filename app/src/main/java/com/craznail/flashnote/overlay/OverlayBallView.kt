package com.craznail.flashnote.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.craznail.flashnote.R
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating overlay ball — UI specs:
 * - 56dp diameter; when docked, 40dp remains visible (16dp overhang)
 * - Semi-transparent white bg + primary icon #3B82F6, elevation 6
 * - Drag follow finger; release snap L/R 200ms ease-out
 * - Short tap → open app inbox; press scale 0.92, release spring back
 * - Long-press 400ms → screenshot (follows summary settings); no menu
 * - Success: green check flash 420ms; optional toast bar
 * - Failure: red-dot flash ×2 + toast「未授权截屏」
 */
class OverlayBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** Short tap: open app. */
    var onTap: (() -> Unit)? = null
    /** Long press: capture screenshot. */
    var onLongPressCapture: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val ballSizePx = (56 * density).roundToInt()
    private val visibleWhenDockedPx = (40 * density).roundToInt()
    private val overhangPx = ballSizePx - visibleWhenDockedPx // 16dp
    private val touchSlop = 8 * density
    private val longPressMs = 400L

    private val rootContainer: FrameLayout
    private lateinit var ballContainer: FrameLayout
    private val ballBg: View
    private val iconView: ImageView
    private val checkView: ImageView
    private val redDot: View
    private val thumbBadge: ImageView
    private val toastBar: TextView
    private val actionMenu: LinearLayout

    private var windowParams: WindowManager.LayoutParams? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var startParamX = 0
    private var startParamY = 0
    private var moved = false
    private var longPressFired = false
    private var menuVisible = false
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!moved) {
            longPressFired = true
            onLongPressCapture?.invoke()
        }
    }

    init {
        // Expandable root so menu/toast can show without clipping
        rootContainer = this
        clipChildren = false
        clipToPadding = false

        ballContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx)
            elevation = 6 * density
        }

        ballBg = View(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6FFFFFF.toInt()) // semi-transparent white
            }
        }

        iconView = ImageView(context).apply {
            val pad = (14 * density).roundToInt()
            layoutParams = LayoutParams(ballSizePx, ballSizePx)
            setPadding(pad, pad, pad, pad)
            setImageResource(R.drawable.ic_flash_note)
            setColorFilter(PRIMARY_BLUE)
            contentDescription = context.getString(R.string.app_name)
        }

        checkView = ImageView(context).apply {
            layoutParams = LayoutParams(ballSizePx, ballSizePx)
            setImageResource(R.drawable.ic_check_circle)
            visibility = View.GONE
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (8 * density).roundToInt()
            setPadding(pad, pad, pad, pad)
        }

        redDot = View(context).apply {
            val d = (10 * density).roundToInt()
            layoutParams = LayoutParams(d, d).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, (4 * density).roundToInt(), (4 * density).roundToInt(), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED)
            }
            visibility = View.GONE
        }

        thumbBadge = ImageView(context).apply {
            val d = (24 * density).roundToInt()
            layoutParams = LayoutParams(d, d).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            visibility = View.GONE
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        ballContainer.addView(ballBg)
        ballContainer.addView(iconView)
        ballContainer.addView(checkView)
        ballContainer.addView(redDot)
        ballContainer.addView(thumbBadge)

        toastBar = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                (40 * density).roundToInt()
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                topMargin = ballSizePx + (8 * density).roundToInt()
            }
            maxWidth = (280 * density).roundToInt()
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(
                (16 * density).roundToInt(), 0,
                (16 * density).roundToInt(), 0
            )
            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(0xDD2E7D32.toInt())
            }
            visibility = View.GONE
            alpha = 0f
        }

        // Long-press menu removed: long-press captures directly (settings control summary).
        actionMenu = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }

        addView(ballContainer)
        addView(toastBar)
        addView(actionMenu)

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
            ballSizePx,
            ballSizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            // Default dock right: 40dp visible
            x = dm.widthPixels - visibleWhenDockedPx
            y = (dm.heightPixels * 0.35f).toInt()
        }
        windowParams = params
        wm.addView(this, params)
        return params
    }

    /** Success: green check flash 420ms at ball center, then restore. */
    fun showSuccessFeedback(toastText: String? = null, thumbnailPath: String? = null) {
        checkView.visibility = View.VISIBLE
        checkView.alpha = 1f
        iconView.visibility = View.INVISIBLE
        handler.postDelayed({
            checkView.visibility = View.GONE
            iconView.visibility = View.VISIBLE
        }, 420)

        if (!toastText.isNullOrBlank()) {
            showToastBar(toastText, 0xDD2E7D32.toInt())
        } else {
            showToastBar(context.getString(R.string.saved_to_notes), 0xDD2E7D32.toInt())
        }

        if (!thumbnailPath.isNullOrBlank() && File(thumbnailPath).exists()) {
            setThumbnailBadge(thumbnailPath)
        }
    }

    /** Failure: red-dot flash twice + toast「未授权截屏」. */
    fun showFailureUnauthorized() {
        flashRedDotTwice()
        showToastBar(context.getString(R.string.unauthorized_capture), 0xDDC62828.toInt())
    }

    fun setThumbnailBadge(path: String) {
        try {
            val bmp = android.graphics.BitmapFactory.decodeFile(path) ?: return
            val size = (24 * density).roundToInt()
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, size, size, true)
            if (scaled != bmp) bmp.recycle()
            val drawable = RoundedBitmapDrawableFactory.create(resources, scaled).apply {
                isCircular = true
            }
            thumbBadge.setImageDrawable(drawable)
            thumbBadge.visibility = View.VISIBLE
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun flashRedDotTwice() {
        redDot.visibility = View.VISIBLE
        redDot.alpha = 1f
        val blink = ObjectAnimator.ofFloat(redDot, View.ALPHA, 1f, 0f, 1f, 0f, 1f, 0f).apply {
            duration = 600
        }
        blink.start()
        handler.postDelayed({ redDot.visibility = View.GONE }, 650)
    }

    private fun showToastBar(text: String, bgColor: Int) {
        expandWindowForExtras()
        toastBar.text = text
        (toastBar.background as? GradientDrawable)?.setColor(bgColor)
        toastBar.visibility = View.VISIBLE
        toastBar.animate().alpha(1f).setDuration(150).start()
        handler.postDelayed({
            toastBar.animate().alpha(0f).setDuration(200).withEndAction {
                toastBar.visibility = View.GONE
                shrinkWindowIfIdle()
            }.start()
        }, 1200)
    }

    private fun showActionMenu() {
        menuVisible = true
        expandWindowForExtras()
        // Position menu 12dp from ball toward screen center
        val lp = windowParams ?: return
        val dm = resources.displayMetrics
        val onLeft = lp.x + ballSizePx / 2 < dm.widthPixels / 2
        val menuLp = actionMenu.layoutParams as LayoutParams
        if (onLeft) {
            menuLp.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            menuLp.marginStart = ballSizePx + (12 * density).roundToInt()
            menuLp.marginEnd = 0
        } else {
            menuLp.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            menuLp.marginEnd = ballSizePx + (12 * density).roundToInt()
            menuLp.marginStart = 0
        }
        actionMenu.layoutParams = menuLp
        actionMenu.visibility = View.VISIBLE
        actionMenu.alpha = 0f
        actionMenu.animate().alpha(1f).setDuration(120).start()
    }

    private fun hideActionMenu() {
        menuVisible = false
        actionMenu.visibility = View.GONE
        shrinkWindowIfIdle()
    }

    private fun expandWindowForExtras() {
        val lp = windowParams ?: return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Wide enough for ball + 12dp + buttons (~96dp) or toast ≤280dp
        val needW = (280 * density).roundToInt().coerceAtLeast(ballSizePx + (12 * density).roundToInt() + (96 * density).roundToInt())
        val needH = ballSizePx + (8 * density).roundToInt() + (40 * density).roundToInt()
        if (lp.width < needW || lp.height < needH) {
            // Keep ball visual position: when expanding leftward for right-docked ball
            val dm = resources.displayMetrics
            val dockedRight = lp.x + visibleWhenDockedPx >= dm.widthPixels - 2
            if (dockedRight && lp.width == ballSizePx) {
                lp.x = lp.x - (needW - ballSizePx)
            }
            lp.width = needW
            lp.height = needH.coerceAtLeast(ballSizePx + (44 * 2 + 8 + 12).let { (it * density).roundToInt() })
            runCatching { wm.updateViewLayout(this, lp) }
        }
    }

    private fun shrinkWindowIfIdle() {
        if (menuVisible || toastBar.visibility == View.VISIBLE) return
        val lp = windowParams ?: return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        // Re-dock: restore 56dp window with 40dp visible
        val centerX = lp.x + lp.width / 2
        val onRight = centerX >= dm.widthPixels / 2
        lp.width = ballSizePx
        lp.height = ballSizePx
        lp.x = if (onRight) dm.widthPixels - visibleWhenDockedPx else -overhangPx
        runCatching { wm.updateViewLayout(this, lp) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val lp = windowParams ?: return super.onTouchEvent(event)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (menuVisible) {
                    // Outside release / tap dismisses menu
                    hideActionMenu()
                    return true
                }
                downRawX = event.rawX
                downRawY = event.rawY
                startParamX = lp.x
                startParamY = lp.y
                moved = false
                longPressFired = false
                // Press scale 0.92
                ballContainer.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                handler.postDelayed(longPressRunnable, longPressMs)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    moved = true
                    handler.removeCallbacks(longPressRunnable)
                    if (menuVisible) hideActionMenu()
                }
                if (!longPressFired) {
                    // Ensure window is ball-sized while dragging
                    if (lp.width != ballSizePx) {
                        lp.width = ballSizePx
                        lp.height = ballSizePx
                    }
                    lp.x = (startParamX + dx).toInt()
                    lp.y = (startParamY + dy).toInt()
                    wm.updateViewLayout(this, lp)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                // Spring back scale
                ballContainer.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.4f))
                    .start()

                when {
                    longPressFired -> {
                        // Capture already fired on long-press
                    }
                    !moved && event.actionMasked == MotionEvent.ACTION_UP -> {
                        onTap?.invoke()
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
        // Docked: only 40dp visible → x = -overhang (left) or width-40dp (right)
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
            interpolator = DecelerateInterpolator() // ease-out
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                lp.x = (startXAnim + (targetX - startXAnim) * t).toInt()
                lp.y = (startYAnim + (targetY - startYAnim) * t).toInt()
                runCatching { wm.updateViewLayout(this@OverlayBallView, lp) }
            }
            start()
        }
    }

    companion object {
        const val PRIMARY_BLUE = 0xFF3B82F6.toInt()
    }
}
