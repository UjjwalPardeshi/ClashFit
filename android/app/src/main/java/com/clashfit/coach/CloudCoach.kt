package com.clashfit.coach

import android.util.Log
import com.clashfit.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * The coach's voice when the on-device model is not installed and the player has said that is ok.
 *
 * Text goes to OpenRouter; text comes back. That is the whole surface. There is no method here that
 * takes a bitmap, a landmark or a rep timeline, so nothing in the app can send one by accident —
 * the referee's eyes are on-device only, and stay that way even with this switched on.
 *
 * Off by default (see Prefs.cloudCoach). One request per call, no retries, four seconds and then
 * the template speaks: a coach that is late is worse than a coach that is plain, and a retry loop is
 * the only way twenty-five dollars of credit disappears in a night.
 */
class CloudCoach(
    private val apiKey: String = BuildConfig.OPENROUTER_API_KEY,
    private val model: String = DEFAULT_MODEL,
    private val endpoint: String = ENDPOINT,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val tag = "ClashFit/cloud"

    /** True when a key was built in. Without one the rung is simply absent from the ladder. */
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /**
     * One exchange. [system] is the rules, [user] the question with its fact sheet.
     * Returns the model's text, trimmed, or null on any failure — the caller falls through.
     */
    suspend fun complete(system: String, user: String, maxTokens: Int = 96, timeoutMs: Long = 4_000L): String? {
        if (!isConfigured) return null
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", 0.4)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()

        return try {
            withTimeoutOrNull(timeoutMs) {
                withContext(Dispatchers.IO) { post(body, timeoutMs.toInt()) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "cloud coach failed: ${e.message}")
            null
        }
    }

    private fun post(body: String, timeoutMs: Int): String? {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            // OpenRouter asks for these so the app shows up by name on their dashboard.
            setRequestProperty("HTTP-Referer", "https://clashfit.app")
            setRequestProperty("X-Title", "ClashFit")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(tag, "cloud coach HTTP $code")
                return null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?.trim()?.takeIf { it.isNotEmpty() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        /** Fast, strict about instructions, and a fraction of a cent per set. */
        const val DEFAULT_MODEL = "anthropic/claude-haiku-4.5"
    }
}
