package com.craznail.flashnote.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OverlayBallSize(val diameterDp: Float) {
    EXTRA_SMALL(32f),
    SMALL(38f),
    MEDIUM(44f),
    LARGE(50f),
    EXTRA_LARGE(56f);

    companion object {
        fun fromStored(value: String?): OverlayBallSize =
            entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

/** Preferences that only affect floating-overlay presentation and interaction. */
class OverlayPreferences private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _feedbackBadgePersistent = MutableStateFlow(
        prefs.getBoolean(KEY_FEEDBACK_BADGE_PERSISTENT, true)
    )
    val feedbackBadgePersistent: StateFlow<Boolean> =
        _feedbackBadgePersistent.asStateFlow()

    private val _ballSize = MutableStateFlow(
        OverlayBallSize.fromStored(prefs.getString(KEY_BALL_SIZE, null))
    )
    val ballSize: StateFlow<OverlayBallSize> = _ballSize.asStateFlow()

    fun setFeedbackBadgePersistent(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FEEDBACK_BADGE_PERSISTENT, enabled).apply()
        _feedbackBadgePersistent.value = enabled
    }

    fun setBallSize(size: OverlayBallSize) {
        prefs.edit().putString(KEY_BALL_SIZE, size.name).apply()
        _ballSize.value = size
    }

    companion object {
        private const val PREFS = "flashnote_overlay_prefs"
        private const val KEY_FEEDBACK_BADGE_PERSISTENT = "feedback_badge_persistent"
        private const val KEY_BALL_SIZE = "ball_size"

        @Volatile
        private var instance: OverlayPreferences? = null

        fun get(context: Context): OverlayPreferences =
            instance ?: synchronized(this) {
                instance ?: OverlayPreferences(context.applicationContext).also { instance = it }
            }
    }
}
