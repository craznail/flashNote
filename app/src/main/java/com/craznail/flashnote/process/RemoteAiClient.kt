package com.craznail.flashnote.process

import com.craznail.flashnote.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Remote summary. Payload is minimal: only OCR text (truncated). No device ids / images.
 * If REMOTE_AI_ENDPOINT is blank, uses a local stand-in labeled 【远端】 for paid-path QA.
 */
object RemoteAiClient {

    suspend fun summarize(ocrText: String?): Result<String> = withContext(Dispatchers.IO) {
        if (ocrText.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("empty ocr"))
        }
        val text = ocrText.take(4000)
        val endpoint = BuildConfig.REMOTE_AI_ENDPOINT.trim()
        if (endpoint.isEmpty()) {
            delay(500)
            val local = LocalSummary.fromOcr(text)
                ?: return@withContext Result.failure(IllegalStateException("no local fallback"))
            return@withContext Result.success("【远端】$local")
        }
        try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val key = BuildConfig.REMOTE_AI_API_KEY.trim()
                if (key.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $key")
                }
            }
            val body = JSONObject().put("text", text).toString()
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
            conn.disconnect()
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("http $code"))
            }
            val summary = JSONObject(resp).optString("summary").ifBlank {
                JSONObject(resp).optString("text")
            }
            if (summary.isBlank()) Result.failure(IllegalStateException("empty summary"))
            else Result.success(summary.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
