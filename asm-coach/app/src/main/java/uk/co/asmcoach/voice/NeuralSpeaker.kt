package uk.co.asmcoach.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * Speaks with the downloaded neural voices. Each sentence is generated while the previous one
 * plays, so speech starts after the first sentence is ready rather than the whole answer.
 */
class NeuralSpeaker(private val store: NeuralVoiceStore) {
    // The native engine is not thread-safe: every load, generate and release runs on this thread.
    private val native = Executors.newSingleThreadExecutor { r -> Thread(r, "neural-tts") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + native)
    private val engines = LinkedHashMap<VoicePack, OfflineTts>(4, 0.75f, true)

    /** Loads the engines for these voices in the background so the first line isn't delayed. */
    fun warmUp(voices: Collection<NeuralVoice>) {
        voices.map { it.pack }.distinct().forEach { pack -> scope.launch { runCatching { engine(pack) } } }
    }

    /** Speaks [sentences] in [voice], with a short natural pause between sentences. Cancellable. */
    suspend fun speak(sentences: List<String>, voice: NeuralVoice, speed: Float) = coroutineScope {
        if (sentences.isEmpty()) return@coroutineScope
        val sampleRate = withContext(native) { engine(voice.pack).sampleRate() }
        val audio = Channel<FloatArray>(capacity = 2)
        launch(native) {
            try {
                for (sentence in sentences) {
                    val samples = engine(voice.pack).generate(sentence, voice.speaker, speed.coerceIn(0.6f, 1.6f)).samples
                    if (samples.isNotEmpty()) audio.send(samples)
                }
            } finally {
                audio.close()
            }
        }
        withContext(Dispatchers.IO) { play(audio, sampleRate) }
    }

    private fun engine(pack: VoicePack): OfflineTts {
        engines[pack]?.let { return it }
        if (engines.size >= MAX_LOADED) {
            val eldest = engines.keys.first()
            engines.remove(eldest)?.release()
        }
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = store.modelFile(pack).absolutePath,
                    tokens = store.tokensFile(pack).absolutePath,
                    dataDir = store.espeakDir.absolutePath,
                ),
                numThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4),
                debug = false,
                provider = "cpu",
            ),
        )
        return OfflineTts(assetManager = null, config = config).also { engines[pack] = it }
    }

    private suspend fun play(chunks: ReceiveChannel<FloatArray>, sampleRate: Int) {
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, sampleRate * 4 / 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val pause = FloatArray((sampleRate * SENTENCE_PAUSE_S).toInt())
        var written = 0L
        try {
            track.play()
            var first = true
            for (samples in chunks) {
                if (!first) written += write(track, pause)
                first = false
                written += write(track, samples)
            }
            // A short tail so the last word isn't clipped, then wait for it to finish playing.
            written += write(track, FloatArray(sampleRate / 8))
            val deadline = System.currentTimeMillis() + written * 1000 / sampleRate + 1_500
            while ((track.playbackHeadPosition.toLong() and 0xFFFFFFFFL) < written && System.currentTimeMillis() < deadline) {
                delay(20)
            }
        } finally {
            runCatching { track.pause(); track.flush() }
            track.release()
        }
    }

    /** Writes in small blocks so stopping takes effect within ~50 ms. */
    private suspend fun write(track: AudioTrack, data: FloatArray): Int {
        var offset = 0
        while (offset < data.size) {
            coroutineContext.ensureActive()
            val n = track.write(data, offset, minOf(WRITE_BLOCK, data.size - offset), AudioTrack.WRITE_BLOCKING)
            if (n <= 0) break
            offset += n
        }
        return offset
    }

    private companion object {
        /** A panel uses at most three packs; each loaded model takes roughly 100 MB of memory. */
        const val MAX_LOADED = 3
        const val SENTENCE_PAUSE_S = 0.22f
        const val WRITE_BLOCK = 1_024
    }
}
