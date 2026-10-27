package uk.co.asmcoach.data.remote

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
    ): String = call(system, prompt, temperature, jsonMode = false, fast = fast)

    suspend fun <T> generateStructured(
        serializer: KSerializer<T>,
        system: String,
        prompt: String,
        temperature: Float = 0.6f,
        fast: Boolean = false,
    ): T {
        val raw = call(system, prompt, temperature, jsonMode = true, fast = fast)
        return try {
            json.decodeFromString(serializer, JsonText.extract(raw))
        } catch (e: SerializationException) {
            throw RemoteException("The AI returned an unexpected format. Please try again.", e)
        } catch (e: IllegalArgumentException) {
            throw RemoteException("The AI returned an unexpected format. Please try again.", e)
        }
    }

    private suspend fun call(
        system: String,
        prompt: String,
        temperature: Float,
        jsonMode: Boolean,
        fast: Boolean,
    ): String {
        val s = settings.current()
        if (!s.hasGemini) {
            throw RemoteException("Add your free Gemini API key in Settings to use the AI coach.")
        }
        val model = s.geminiModel
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
        val (code, body) = Http.postJson(
            client = http,
            url = "$BASE_URL/models/$model:generateContent",
            body = json.encodeToString(GeminiRequest.serializer(), request),
            headers = mapOf("x-goog-api-key" to s.geminiKey),
            serviceName = "Gemini",
        )
        if (code !in 200..299) throw RemoteException(describeError(code, body, model))

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
            in 500..599 -> "Gemini is busy right now ($code). Try again shortly."
            else -> "Gemini error $code: ${apiMessage.ifBlank { "unknown" }}"
        }
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
}
