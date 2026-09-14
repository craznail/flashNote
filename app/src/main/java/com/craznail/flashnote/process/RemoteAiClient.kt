package com.craznail.flashnote.process

import com.craznail.flashnote.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible Chat Completions remote summary.
 * Payload is minimal: OCR text only (in messages). No images / device ids / extras.
 * Missing base URL or API key → not configured (caller must tip + local fallback).
 */
object RemoteAiClient {

    private const val SYSTEM_PROMPT =
        "你是简洁的中文摘要助手。根据用户给出的屏幕 OCR 文本，用一两句中文概括要点，不要复述无关噪声，不要添加前缀标签。"

    fun isConfigured(prefs: PreferencesManager): Boolean {
        return prefs.remoteAiBaseUrl.isNotBlank() && prefs.remoteAiApiKey.isNotBlank()
    }

    /** Normalize user base URL to .../chat/completions. */
    fun normalizeChatCompletionsUrl(baseUrl: String): String {
        val u = baseUrl.trim().trimEnd('/')
        return when {
            u.endsWith("/chat/completions", ignoreCase = true) -> u
            u.endsWith("/v1", ignoreCase = true) -> "$u/chat/completions"
            else -> "$u/chat/completions"
        }
    }

    suspend fun summarize(prefs: PreferencesManager, ocrText: String?): Result<String> =
        withContext(Dispatchers.IO) {
            if (ocrText.isNullOrBlank()) {
                return@withContext Result.failure(IllegalArgumentException("empty ocr"))
            }
            if (!isConfigured(prefs)) {
                return@withContext Result.failure(IllegalStateException("not configured"))
            }
            val text = ocrText.take(4000)
            val endpoint = normalizeChatCompletionsUrl(prefs.remoteAiBaseUrl)
            val model = prefs.remoteAiModel.ifBlank { "gpt-4o-mini" }
            val apiKey = prefs.remoteAiApiKey.trim()
            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 25_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", text))
                val body = JSONObject()
                    .put("model", model)
                    .put("messages", messages)
                    .toString()
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
                }.orEmpty()
                conn.disconnect()
                if (code !in 200..299) {
                    return@withContext Result.failure(IllegalStateException("http $code"))
                }
                val root = JSONObject(resp)
                val choices = root.optJSONArray("choices")
                val content = choices
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    .orEmpty()
                if (content.isBlank()) {
                    Result.failure(IllegalStateException("empty summary"))
                } else {
                    Result.success(content)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
