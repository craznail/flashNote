package com.craznail.flashnote.overlay

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * In-memory + SharedPreferences crop selection in **screen pixels**
 * (left, top, right, bottom). Used by the selection overlay and CaptureService.
 */
object CropRegionStore {

    private const val PREFS = "flashnote_crop"
    private const val KEY_L = "crop_l"
    private const val KEY_T = "crop_t"
    private const val KEY_R = "crop_r"
    private const val KEY_B = "crop_b"

    @Volatile
    private var rect: Rect? = null

    fun getRectOrNull(): Rect? = rect?.let { Rect(it) }

    /**
     * Returns the active crop, or a default 60%×40% region biased toward [ballCenterX]/
     * [ballCenterY] (or screen center if null). Always clamped to [screenW]×[screenH].
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
        val w = (screenW * 0.60f).toInt().coerceAtLeast(1)
        val h = (screenH * 0.40f).toInt().coerceAtLeast(1)
        val bx = ballCenterX ?: (screenW / 2)
        val by = ballCenterY ?: (screenH / 2)
        // Bias horizontal center toward the ball's side (~35% / ~65%).
        val preferCx = if (bx < screenW / 2) {
            (screenW * 0.35f).toInt()
        } else {
            (screenW * 0.65f).toInt()
        }
        val preferCy = by
        val left = (preferCx - w / 2).coerceIn(0, (screenW - w).coerceAtLeast(0))
        val top = (preferCy - h / 2).coerceIn(0, (screenH - h).coerceAtLeast(0))
        return Rect(left, top, left + w, top + h)
    }

    fun clamp(r: Rect, screenW: Int, screenH: Int): Rect {
        if (screenW <= 0 || screenH <= 0) return r
        var w = r.width().coerceIn(1, screenW)
        var h = r.height().coerceIn(1, screenH)
        var left = r.left
        var top = r.top
        if (left < 0) left = 0
        if (top < 0) top = 0
        if (left + w > screenW) left = screenW - w
        if (top + h > screenH) top = screenH - h
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

    private fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }
}
