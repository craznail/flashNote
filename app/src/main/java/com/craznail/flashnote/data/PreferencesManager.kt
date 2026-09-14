package com.craznail.flashnote.data

import android.content.Context
import android.content.SharedPreferences
import com.craznail.flashnote.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prefs: local summary (free), simulate premium + remote AI (paid path; real IAP later).
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _localSummaryEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LOCAL_SUMMARY, true) // free default: local summary on
    )
    val localSummaryEnabled: StateFlow<Boolean> = _localSummaryEnabled.asStateFlow()

    private val _simulatePremium = MutableStateFlow(
        prefs.getBoolean(KEY_SIMULATE_PREMIUM, false)
    )
    val simulatePremium: StateFlow<Boolean> = _simulatePremium.asStateFlow()

    private val _remoteAiEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_REMOTE_AI, false)
    )
    val remoteAiEnabled: StateFlow<Boolean> = _remoteAiEnabled.asStateFlow()

    /** Compile-time OR settings「模拟付费」 */
    val isPremium: Boolean
        get() = BuildConfig.IS_PREMIUM || _simulatePremium.value

    fun setLocalSummaryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_SUMMARY, enabled).apply()
        _localSummaryEnabled.value = enabled
    }

    fun setSimulatePremium(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SIMULATE_PREMIUM, enabled).apply()
        _simulatePremium.value = enabled
        if (!enabled) setRemoteAiEnabled(false)
    }

    fun setRemoteAiEnabled(enabled: Boolean) {
        val on = enabled && isPremium
        prefs.edit().putBoolean(KEY_REMOTE_AI, on).apply()
        _remoteAiEnabled.value = on
    }

    companion object {
        private const val PREFS = "flashnote_prefs"
        private const val KEY_LOCAL_SUMMARY = "local_summary_enabled"
        private const val KEY_SIMULATE_PREMIUM = "simulate_premium"
        private const val KEY_REMOTE_AI = "remote_ai_enabled"

        @Volatile private var instance: PreferencesManager? = null
        fun get(context: Context): PreferencesManager =
            instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
    }
}
