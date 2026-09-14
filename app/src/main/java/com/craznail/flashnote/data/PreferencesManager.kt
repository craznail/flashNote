package com.craznail.flashnote.data

import android.content.Context
import android.content.SharedPreferences
import com.craznail.flashnote.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prefs: local summary (free), simulate premium + remote AI (paid path; real IAP later).
 * Remote AI credentials live only in SharedPreferences (never commit secrets).
 * BuildConfig REMOTE_AI_* are optional build-time defaults when prefs are empty.
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _localSummaryEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LOCAL_SUMMARY, true)
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

    private val _remoteAiBaseUrl = MutableStateFlow(readRemoteAiBaseUrl())
    val remoteAiBaseUrlFlow: StateFlow<String> = _remoteAiBaseUrl.asStateFlow()

    private val _remoteAiApiKey = MutableStateFlow(readRemoteAiApiKey())
    val remoteAiApiKeyFlow: StateFlow<String> = _remoteAiApiKey.asStateFlow()

    private val _remoteAiModel = MutableStateFlow(readRemoteAiModel())
    val remoteAiModelFlow: StateFlow<String> = _remoteAiModel.asStateFlow()

    /** Compile-time OR settings「模拟付费」 */
    val isPremium: Boolean
        get() = BuildConfig.IS_PREMIUM || _simulatePremium.value

    /** Effective values (prefs, else BuildConfig defaults). */
    val remoteAiBaseUrl: String
        get() = _remoteAiBaseUrl.value

    val remoteAiApiKey: String
        get() = _remoteAiApiKey.value

    val remoteAiModel: String
        get() = _remoteAiModel.value

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

    fun setRemoteAiConfig(baseUrl: String, apiKey: String, model: String) {
        val url = baseUrl.trim()
        val key = apiKey.trim()
        val mdl = model.trim().ifBlank { DEFAULT_MODEL }
        prefs.edit()
            .putString(KEY_REMOTE_AI_BASE_URL, url)
            .putString(KEY_REMOTE_AI_API_KEY, key)
            .putString(KEY_REMOTE_AI_MODEL, mdl)
            .apply()
        _remoteAiBaseUrl.value = url.ifBlank { BuildConfig.REMOTE_AI_ENDPOINT.trim() }
        _remoteAiApiKey.value = key.ifBlank { BuildConfig.REMOTE_AI_API_KEY.trim() }
        _remoteAiModel.value = mdl.ifBlank {
            BuildConfig.REMOTE_AI_MODEL.trim().ifBlank { DEFAULT_MODEL }
        }
    }

    private fun readRemoteAiBaseUrl(): String {
        val stored = prefs.getString(KEY_REMOTE_AI_BASE_URL, null)
        return if (!stored.isNullOrBlank()) stored.trim()
        else BuildConfig.REMOTE_AI_ENDPOINT.trim()
    }

    private fun readRemoteAiApiKey(): String {
        val stored = prefs.getString(KEY_REMOTE_AI_API_KEY, null)
        return if (!stored.isNullOrBlank()) stored.trim()
        else BuildConfig.REMOTE_AI_API_KEY.trim()
    }

    private fun readRemoteAiModel(): String {
        val stored = prefs.getString(KEY_REMOTE_AI_MODEL, null)
        if (!stored.isNullOrBlank()) return stored.trim()
        return BuildConfig.REMOTE_AI_MODEL.trim().ifBlank { DEFAULT_MODEL }
    }

    companion object {
        private const val PREFS = "flashnote_prefs"
        private const val KEY_LOCAL_SUMMARY = "local_summary_enabled"
        private const val KEY_SIMULATE_PREMIUM = "simulate_premium"
        private const val KEY_REMOTE_AI = "remote_ai_enabled"
        private const val KEY_REMOTE_AI_BASE_URL = "remote_ai_base_url"
        private const val KEY_REMOTE_AI_API_KEY = "remote_ai_api_key"
        private const val KEY_REMOTE_AI_MODEL = "remote_ai_model"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        @Volatile private var instance: PreferencesManager? = null
        fun get(context: Context): PreferencesManager =
            instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
    }
}
