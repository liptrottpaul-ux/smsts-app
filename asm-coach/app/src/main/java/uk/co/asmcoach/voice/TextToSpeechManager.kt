package uk.co.asmcoach.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Pitch, rate and preferred voice slot for one speaker. */
data class VoiceStyle(val pitch: Float = 1f, val rate: Float = 1f, val voiceSlot: Int = 0) {
    companion object {
        val Narrator = VoiceStyle()
    }
}

/**
 * Wraps Android's on-device TextToSpeech engine with British English voices, long-text chunking
 * and suspend-until-finished playback.
 */
class TextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** The id passed to [speak] that is currently playing, or null. */
    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    private val waiters = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private var voices: List<Voice> = emptyList()

    @Volatile
    var baseRate: Float = 1f

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.UK)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        voices = runCatching {
            tts.voices.orEmpty()
                .filter { it.locale.language == "en" && it.locale.country == "GB" }
                .filter { !it.isNetworkConnectionRequired && it.features?.contains("notInstalled") != true }
                .sortedByDescending { it.quality }
        }.getOrDefault(emptyList())
        tts.setOnUtteranceProgressListener(listener)
        _ready.value = true
    }

    /** Fire-and-forget playback. Replaces anything currently speaking. */
    fun speak(text: String, id: String, style: VoiceStyle = VoiceStyle.Narrator) {
        if (!_ready.value || text.isBlank()) return
        stop()
        voices.getOrNull(style.voiceSlot % voices.size.coerceAtLeast(1))?.let { tts.setVoice(it) }
        tts.setPitch(style.pitch)
        tts.setSpeechRate(style.rate * baseRate)
        val chunks = chunk(text)
        chunks.forEachIndexed { index, part ->
            val utteranceId = if (index == chunks.lastIndex) "$id$LAST" else "$id#$index"
            tts.speak(part, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
    }

    /** Speaks and suspends until playback finishes, is stopped, or the engine is unavailable. */
    suspend fun speakAndAwait(text: String, style: VoiceStyle = VoiceStyle.Narrator) {
        if (text.isBlank()) return
        withTimeoutOrNull(4_000) { _ready.first { it } } ?: return
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine<Unit> { cont ->
            speak(text, id, style)
            waiters[id] = cont
            cont.invokeOnCancellation {
                waiters.remove(id)
                if (_speakingId.value == id) tts.stop()
            }
        }
    }

    fun stop() {
        tts.stop()
        _speakingId.value = null
        val pending = waiters.keys.toList()
        pending.forEach { resolve(it) }
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun resolve(groupId: String) {
        waiters.remove(groupId)?.let { if (it.isActive) it.resume(Unit) }
    }

    private fun groupOf(utteranceId: String?): String? = utteranceId?.substringBefore('#')

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _speakingId.value = groupOf(utteranceId)
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId?.endsWith(LAST) == true) finish(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)

        private fun finish(utteranceId: String?) {
            val group = groupOf(utteranceId) ?: return
            if (_speakingId.value == group) _speakingId.value = null
            resolve(group)
        }
    }

    /** TextToSpeech rejects input longer than getMaxSpeechInputLength(); split on sentences. */
    private fun chunk(text: String): List<String> {
        val max = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(500)
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>()
        val current = StringBuilder()
        text.split(Regex("(?<=[.!?])\\s+")).forEach { sentence ->
            if (current.length + sentence.length + 1 > max && current.isNotEmpty()) {
                out += current.toString()
                current.clear()
            }
            sentence.chunked(max).forEach { piece ->
                if (current.length + piece.length + 1 > max && current.isNotEmpty()) {
                    out += current.toString()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private companion object {
        const val LAST = "#last"
    }
}
