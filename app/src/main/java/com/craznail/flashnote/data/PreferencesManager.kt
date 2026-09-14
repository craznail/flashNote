package com.craznail.flashnote.data

import android.content.Context
import android.content.SharedPreferences
import com.craznail.flashnote.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight prefs. isPremium is a compile-time stub (false).
 * 「本地摘要」toggle is persisted; when on, CaptureService builds a local heuristic summary.
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _localSummaryEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LOCAL_SUMMARY, false)
    )
    val localSummaryEnabled: StateFlow<Boolean> = _localSummaryEnabled.asStateFlow()

    val isPremium: Boolean get() = BuildConfig.IS_PREMIUM

    fun setLocalSummaryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_SUMMARY, enabled).apply()
        _localSummaryEnabled.value = enabled
    }

    companion object {
        private const val PREFS = "flashnote_prefs"
        private const val KEY_LOCAL_SUMMARY = "local_summary_enabled"

        @Volatile private var instance: PreferencesManager? = null
        fun get(context: Context): PreferencesManager =
            instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
    }
}
