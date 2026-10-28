package uk.co.asmcoach.data.remote

import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import uk.co.asmcoach.data.settings.SettingsRepository

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
data class GeminiContent(val role: String? = null, val parts: List<GeminiPart> = emptyList())

@Serializable
data class GeminiPart(val text: String? = null, val thought: Boolean? = null)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val responseMimeType: String? = null,
    val maxOutputTokens: Int? = null,
    val thinkingConfig: ThinkingConfig? = null,
)

@Serializable
data class ThinkingConfig(val thinkingBudget: Int)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null, val finishReason: String? = null)

@Serializable
data class PromptFeedback(val blockReason: String? = null)

@Serializable
data class GeminiModelList(val models: List<GeminiModelInfo> = emptyList())

@Serializable
data class GeminiModelInfo(val name: String = "", val supportedGenerationMethods: List<String> = emptyList())

@Serializable
data class GeminiErrorEnvelope(val error: GeminiError? = null)

@Serializable
data class GeminiError(val code: Int = 0, val message: String = "", val status: String = "")

/**
 * Minimal REST client for the Gemini API free tier (Google AI Studio key).
 * https://ai.google.dev/api/generate-content
 */
class GeminiClient(
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val json = AppJson

    suspend fun generateText(
        system: String,
        prompt: String,
        temperature: Float = 0.7f,
        fast: Boolean = false,
        quick: Boolean = false,
    ): String = call(system, prompt, temperature, jsonMode = false, fast = fast, quick = quick)

    suspend fun <T> generateStructured(
        serializer: KSerializer<T>,
        system: String,
        prompt: String,
        temperature: Float = 0.6f,
        fast: Boolean = false,
        quick: Boolean = false,
        lite: Boolean = false,
    ): T {
        val raw = call(system, prompt, temperature, jsonMode = true, fast = fast, quick = quick, lite = lite)
        decode(serializer, raw)?.let { return it }
        // Flash-Lite sometimes returns malformed or cut-off JSON for long outputs: redo it on the main model.
        if (lite) {
            decode(serializer, call(system, prompt, temperature, jsonMode = true, fast = true))?.let { return it }
        }
        throw RemoteException("The AI returned an unexpected format. Please try again.")
    }

    private fun <T> decode(serializer: KSerializer<T>, raw: String): T? = try {
        json.decodeFromString(serializer, JsonText.extract(raw))
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private suspend fun call(
        system: String,
        prompt: String,
        temperature: Float,
        jsonMode: Boolean,
        fast: Boolean,
        quick: Boolean = false,
        lite: Boolean = false,
    ): String {
        val s = settings.current()
        if (!s.hasGemini) {
            throw RemoteException("Add your free Gemini API key in Settings to use the AI coach.")
        }
        if (quick) {
            // Live interview reactions: one fast attempt on the lightest model; callers fall back locally.
            val (code, body) = send(s.geminiKey, LITE_MODEL, system, prompt, temperature, jsonMode, fast = true)
            if (code !in 200..299) throw RemoteException(describeError(code, body, LITE_MODEL))
            return parse(body)
        }
        if (lite) {
            // Bulk generation (role packs) on the faster Flash-Lite model; any failure falls back to the main model.
            val (code, body) = sendWithRetry(s.geminiKey, LITE_MODEL, system, prompt, temperature, jsonMode, fast = true)
            if (code in 200..299) return parse(body)
        }
        val primary = s.geminiModel
        val (code, body, model) = sendWithRetry(s.geminiKey, primary, system, prompt, temperature, jsonMode, fast)
            .let { first ->
                if (first.first != 404) return@let Triple(first.first, first.second, primary)
                // Google retires model versions; find one this key can use, remember it, retry once.
                val replacement = discoverModel(s.geminiKey)
                    ?: return@let Triple(first.first, first.second, primary)
                settings.setModel(replacement)
                val retry = sendWithRetry(s.geminiKey, replacement, system, prompt, temperature, jsonMode, fast)
                Triple(retry.first, retry.second, replacement)
            }
            .let { result ->
                if (result.first !in BUSY || result.third == LITE_MODEL) return@let result
                // Flash is overloaded: the lighter model usually has spare capacity.
                val liteResult = send(s.geminiKey, LITE_MODEL, system, prompt, temperature, jsonMode, fast)
                if (liteResult.first in 200..299) Triple(liteResult.first, liteResult.second, LITE_MODEL) else result
            }
        if (code !in 200..299) throw RemoteException(describeError(code, body, model))
        return parse(body)
    }

    /** Retries overloaded (5xx) and briefly rate-limited (429) responses with backoff. */
    private suspend fun sendWithRetry(
        key: String,
        model: String,
        system: String,
        prompt: String,
        temperature: Float,
        jsonMode: Boolean,
        fast: Boolean,
    ): Pair<Int, String> {
        var result = send(key, model, system, prompt, temperature, jsonMode, fast)
        for (wait in RETRY_DELAYS_MS) {
            if (result.first !in BUSY) break
            delay(wait)
            result = send(key, model, system, prompt, temperature, jsonMode, fast)
        }
        return result
    }

    private suspend fun send(
        key: String,
        model: String,
        system: String,
        prompt: String,
        temperature: Float,
        jsonMode: Boolean,
        fast: Boolean,
    ): Pair<Int, String> {
        val request = GeminiRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = system))),
            contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = temperature,
                responseMimeType = if (jsonMode) "application/json" else null,
                // 2.5 Flash "thinks" by default; switch it off for latency-sensitive voice turns.
                thinkingConfig = if (fast && model.contains("2.5-flash")) ThinkingConfig(0) else null,
            ),
        )
        return Http.postJson(
            client = http,
            url = "$BASE_URL/models/$model:generateContent",
            body = json.encodeToString(GeminiRequest.serializer(), request),
            headers = mapOf("x-goog-api-key" to key),
            serviceName = "Gemini",
        )
    }

    /** Lists the models this key can call and picks the best general-purpose Flash model. */
    private suspend fun discoverModel(key: String): String? {
        val (code, body) = Http.get(
            client = http,
            url = "$BASE_URL/models?pageSize=200",
            headers = mapOf("x-goog-api-key" to key),
            serviceName = "Gemini",
        )
        if (code !in 200..299) return null
        val names = runCatching { json.decodeFromString(GeminiModelList.serializer(), body) }.getOrNull()
            ?.models.orEmpty()
            .filter { "generateContent" in it.supportedGenerationMethods }
            .map { it.name.removePrefix("models/") }
        return pickModel(names)
    }

    private fun parse(body: String): String {
        val response = try {
            json.decodeFromString(GeminiResponse.serializer(), body)
        } catch (e: SerializationException) {
            throw RemoteException("Gemini sent an unreadable response.", e)
        }
        response.promptFeedback?.blockReason?.let {
            throw RemoteException("Gemini declined this request ($it). Try rephrasing.")
        }
        val candidate = response.candidates.firstOrNull()
            ?: throw RemoteException("Gemini returned no answer. Please try again.")
        val text = candidate.content?.parts.orEmpty()
            .filter { it.thought != true }
            .mapNotNull { it.text }
            .joinToString("")
            .trim()
        if (text.isEmpty()) {
            throw RemoteException("Gemini returned an empty answer (${candidate.finishReason ?: "unknown"}).")
        }
        return text
    }

    private fun describeError(code: Int, body: String, model: String): String {
        val apiMessage = runCatching {
            json.decodeFromString(GeminiErrorEnvelope.serializer(), body).error?.message
        }.getOrNull().orEmpty()
        return when (code) {
            400 -> if (apiMessage.contains("API key", ignoreCase = true)) {
                "Your Gemini API key is not valid. Check it in Settings."
            } else {
                "Gemini rejected the request: ${apiMessage.ifBlank { "bad request" }}"
            }
            401, 403 -> "Your Gemini API key was rejected. Check it in Settings."
            404 -> "Model \"$model\" isn't available. Pick another model in Settings."
            429 -> "Free-tier rate limit reached. Wait a minute and try again."
            in 500..599 -> "Google's Gemini servers are overloaded ($code). The app retried for you; wait a minute, then tap Try again."
            else -> "Gemini error $code: ${apiMessage.ifBlank { "unknown" }}"
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val LITE_MODEL = "gemini-flash-lite-latest"
        private val BUSY = setOf(429, 500, 502, 503, 504)
        private val RETRY_DELAYS_MS = listOf(1_500L, 3_000L, 6_000L)
        private val EXCLUDE = listOf("lite", "image", "tts", "audio", "live", "embedding", "exp", "preview", "thinking", "8b")

        /** Prefers the rolling Flash alias, then the newest stable Flash version, then any Flash. */
        fun pickModel(names: List<String>): String? {
            if ("gemini-flash-latest" in names) return "gemini-flash-latest"
            val flash = names.filter { it.startsWith("gemini") && "flash" in it }
            val stable = flash.filter { n -> EXCLUDE.none { it in n } }
            val version = { n: String -> Regex("gemini-(\\d+(?:\\.\\d+)?)").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0 }
            return stable.maxByOrNull(version) ?: flash.maxByOrNull(version)
        }
    }
}
