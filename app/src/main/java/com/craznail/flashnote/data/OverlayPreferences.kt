package com.craznail.flashnote.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferences that only affect floating-overlay presentation and interaction. */
class OverlayPreferences private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _feedbackBadgePersistent = MutableStateFlow(
        prefs.getBoolean(KEY_FEEDBACK_BADGE_PERSISTENT, true)
    )
    val feedbackBadgePersistent: StateFlow<Boolean> =
        _feedbackBadgePersistent.asStateFlow()

    fun setFeedbackBadgePersistent(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FEEDBACK_BADGE_PERSISTENT, enabled).apply()
        _feedbackBadgePersistent.value = enabled
    }

    companion object {
        private const val PREFS = "flashnote_overlay_prefs"
        private const val KEY_FEEDBACK_BADGE_PERSISTENT = "feedback_badge_persistent"

        @Volatile
        private var instance: OverlayPreferences? = null

        fun get(context: Context): OverlayPreferences =
            instance ?: synchronized(this) {
                instance ?: OverlayPreferences(context.applicationContext).also { instance = it }
            }
    }
}
