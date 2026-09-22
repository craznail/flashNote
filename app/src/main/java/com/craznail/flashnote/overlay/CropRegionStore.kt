package com.craznail.flashnote.overlay

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * In-memory + SharedPreferences crop selection in **screen pixels**
 * (left, top, right, bottom). Used by the selection overlay and CaptureService.
 *
 * Edge clamp keeps an **8dp** margin from screen edges (UI lock).
 */
object CropRegionStore {

    private const val PREFS = "flashnote_crop"
    private const val KEY_L = "crop_l"
    private const val KEY_T = "crop_t"
    private const val KEY_R = "crop_r"
    private const val KEY_B = "crop_b"
    private const val EDGE_MARGIN_DP = 8

    @Volatile
    private var rect: Rect? = null

    fun getRectOrNull(): Rect? = rect?.let { Rect(it) }

    /**
     * Returns the active crop, or a default 60%×40% region biased toward [ballCenterX]/
     * [ballCenterY] (or screen center if null). Always clamped to [screenW]×[screenH]
     * with an 8dp edge margin.
     */
    fun getOrDefault(
        screenW: Int,
        screenH: Int,
        ballCenterX: Int? = null,
        ballCenterY: Int? = null
    ): Rect {
        val existing = rect
        if (existing != null && existing.width() > 0 && existing.height() > 0) {
            return clamp(Rect(existing), screenW, screenH)
        }
        return defaultRect(screenW, screenH, ballCenterX, ballCenterY).also { rect = Rect(it) }
    }

    fun setRect(r: Rect, screenW: Int, screenH: Int) {
        rect = clamp(Rect(r), screenW, screenH)
    }

    fun translateBy(dx: Int, dy: Int, screenW: Int, screenH: Int) {
        val cur = rect ?: return
        setRect(
            Rect(cur.left + dx, cur.top + dy, cur.right + dx, cur.bottom + dy),
            screenW,
            screenH
        )
    }

    fun load(context: Context) {
        if (rect != null) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_L)) return
        val r = Rect(
            prefs.getInt(KEY_L, 0),
            prefs.getInt(KEY_T, 0),
            prefs.getInt(KEY_R, 0),
            prefs.getInt(KEY_B, 0)
        )
        if (r.width() > 0 && r.height() > 0) {
            val (w, h) = screenSize(context)
            rect = clamp(r, w, h)
        }
    }

    fun persist(context: Context) {
        val r = rect ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_L, r.left)
            .putInt(KEY_T, r.top)
            .putInt(KEY_R, r.right)
            .putInt(KEY_B, r.bottom)
            .apply()
    }

    fun defaultRect(
        screenW: Int,
        screenH: Int,
        ballCenterX: Int? = null,
        ballCenterY: Int? = null
    ): Rect {
        val m = edgeMarginPx()
        val availW = (screenW - 2 * m).coerceAtLeast(1)
        val availH = (screenH - 2 * m).coerceAtLeast(1)
        val w = (screenW * 0.60f).toInt().coerceIn(1, availW)
        val h = (screenH * 0.40f).toInt().coerceIn(1, availH)
        val bx = ballCenterX ?: (screenW / 2)
        val by = ballCenterY ?: (screenH / 2)
        // Bias horizontal center toward the ball's side (~35% / ~65%).
        val preferCx = if (bx < screenW / 2) {
            (screenW * 0.35f).toInt()
        } else {
            (screenW * 0.65f).toInt()
        }
        val preferCy = by
        val left = (preferCx - w / 2).coerceIn(m, (screenW - m - w).coerceAtLeast(m))
        val top = (preferCy - h / 2).coerceIn(m, (screenH - m - h).coerceAtLeast(m))
        return clamp(Rect(left, top, left + w, top + h), screenW, screenH)
    }

    /**
     * Clamp [r] inside the screen with an **8dp** inset from every edge.
     * Width/height are shrunk if needed so the rect still fits.
     */
    fun clamp(r: Rect, screenW: Int, screenH: Int): Rect {
        if (screenW <= 0 || screenH <= 0) return r
        val m = edgeMarginPx()
        val availW = (screenW - 2 * m).coerceAtLeast(1)
        val availH = (screenH - 2 * m).coerceAtLeast(1)
        val w = r.width().coerceIn(1, availW)
        val h = r.height().coerceIn(1, availH)
        var left = r.left
        var top = r.top
        if (left < m) left = m
        if (top < m) top = m
        if (left + w > screenW - m) left = screenW - m - w
        if (top + h > screenH - m) top = screenH - m - h
        // If screen is smaller than 2*margin, fall back to a non-negative fit.
        if (left < 0) left = 0
        if (top < 0) top = 0
        return Rect(left, top, left + w, top + h)
    }

    /** Map screen-space crop onto a captured bitmap (may differ slightly in size). */
    fun cropForBitmap(bitmapW: Int, bitmapH: Int, screenW: Int, screenH: Int): Rect {
        val src = getOrDefault(screenW, screenH)
        if (screenW <= 0 || screenH <= 0 || bitmapW <= 0 || bitmapH <= 0) {
            return Rect(0, 0, bitmapW.coerceAtLeast(1), bitmapH.coerceAtLeast(1))
        }
        val sx = bitmapW.toFloat() / screenW
        val sy = bitmapH.toFloat() / screenH
        val left = (src.left * sx).toInt().coerceIn(0, bitmapW - 1)
        val top = (src.top * sy).toInt().coerceIn(0, bitmapH - 1)
        val right = (src.right * sx).toInt().coerceIn(left + 1, bitmapW)
        val bottom = (src.bottom * sy).toInt().coerceIn(top + 1, bitmapH)
        return Rect(left, top, right, bottom)
    }

    private fun edgeMarginPx(): Int =
        (EDGE_MARGIN_DP * Resources.getSystem().displayMetrics.density).roundToInt()

    private fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }
}
