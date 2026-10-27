package uk.co.asmcoach.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps Android's on-device SpeechRecognizer for long interview answers.
 *
 * The platform recogniser stops after a short pause, so this runs in "continuous" mode: each
 * recognised segment is appended and listening restarts until [finishAnswer] is called. Must be
 * used from the main thread.
 */
class SpeechRecognizerManager(context: Context) {

    sealed interface State {
        data object Idle : State
        data class Listening(val committed: String, val partial: String, val level: Float) : State {
            val text: String get() = listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" ")
        }
        data class Error(val message: String) : State
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private val committed = StringBuilder()
    private var partial = ""
    private var level = 0f
    private var active = false
    private var finishing = false
    private var retries = 0
    private var onComplete: ((String) -> Unit)? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    /** Starts capturing an answer; [onComplete] receives the full transcript (may be blank). */
    fun startAnswer(onComplete: (String) -> Unit) {
        cancel()
        committed.clear()
        partial = ""
        retries = 0
        active = true
        finishing = false
        this.onComplete = onComplete
        publish()
        listen()
    }

    /** Stops listening and delivers what has been heard so far. */
    fun finishAnswer() {
        if (!active || finishing) return
        finishing = true
        recognizer?.stopListening()
        // Some engines never send a final callback after stopListening(); don't hang.
        main.postDelayed(finishTimeout, 2_500)
    }

    fun cancel() {
        active = false
        finishing = false
        onComplete = null
        main.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        _state.value = State.Idle
    }

    fun release() {
        cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private val finishTimeout = Runnable { if (active) deliver() }

    private fun listen() {
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
        }
        r.startListening(intent)
    }

    private fun restartSoon(delayMs: Long = 120) {
        main.postDelayed({ if (active && !finishing) listen() }, delayMs)
    }

    private fun deliver() {
        if (!active) return
        if (partial.isNotBlank()) append(partial)
        val text = committed.toString().trim()
        val callback = onComplete
        active = false
        finishing = false
        onComplete = null
        main.removeCallbacksAndMessages(null)
        _state.value = State.Idle
        callback?.invoke(text)
    }

    private fun append(segment: String) {
        if (segment.isBlank()) return
        if (committed.isNotEmpty()) committed.append(' ')
        committed.append(segment.trim())
        partial = ""
    }

    private fun publish() {
        if (active) _state.value = State.Listening(committed.toString(), partial, level)
    }

    private fun Bundle?.bestResult(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            publish()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.bestResult()
            if (text.isNotBlank()) {
                partial = text
                publish()
            }
        }

        override fun onResults(results: Bundle?) {
            if (!active) return
            val text = results.bestResult()
            partial = ""
            append(text)
            retries = 0
            if (finishing) deliver() else {
                publish()
                restartSoon()
            }
        }

        override fun onError(error: Int) {
            if (!active) return
            if (finishing) {
                deliver()
                return
            }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Silence or an unrecognised segment: keep the floor open.
                    if (partial.isNotBlank()) append(partial)
                    publish()
                    restartSoon()
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Microphone permission is needed for voice answers.")
                else -> {
                    if (retries++ < MAX_RETRIES) {
                        recognizer?.cancel()
                        restartSoon(400)
                    } else if (committed.isNotEmpty() || partial.isNotBlank()) {
                        deliver()
                    } else {
                        fail(describe(error))
                    }
                }
            }
        }
    }

    private fun fail(message: String) {
        active = false
        finishing = false
        onComplete = null
        main.removeCallbacksAndMessages(null)
        _state.value = State.Error(message)
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs a connection on this device. Try again, or type your answer."
        SpeechRecognizer.ERROR_AUDIO -> "The microphone couldn't be opened."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech service is busy. Try again."
        else -> "Speech recognition stopped (code $error). Try again, or type your answer."
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
