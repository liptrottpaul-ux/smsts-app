package uk.co.asmcoach.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream

/**
 * Downloadable British English neural voices: open-source Piper models run on the phone by
 * sherpa-onnx. Multi-speaker packs give several distinct people from one download.
 */
enum class VoicePack(
    val title: String,
    val detail: String,
    val model: String,
    val sizeMb: Int,
    val gender: VoiceGender,
    val speakers: Int,
    val recommended: Boolean = false,
) {
    NorthernMan(
        "Northern English man", "1 voice · suits North West sites",
        "vits-piper-en_GB-northern_english_male-medium", 67, VoiceGender.Male, 1, recommended = true,
    ),
    BritishWomen(
        "British women", "6 different voices",
        "vits-piper-en_GB-southern_english_female-medium", 80, VoiceGender.Female, 6, recommended = true,
    ),
    BritishMen(
        "British men", "8 different voices",
        "vits-piper-en_GB-southern_english_male-medium", 80, VoiceGender.Male, 8, recommended = true,
    ),
    Cori(
        "Cori (woman)", "1 clear, warm voice",
        "vits-piper-en_GB-cori-medium", 67, VoiceGender.Female, 1,
    );

    val url: String get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$model.tar.bz2"
}

/** One person: a speaker inside a pack. */
data class NeuralVoice(val pack: VoicePack, val speaker: Int) {
    val gender: VoiceGender get() = pack.gender
}

sealed interface PackState {
    data object Missing : PackState
    data class Downloading(val fraction: Float) : PackState
    data object Installed : PackState
    data class Failed(val message: String) : PackState
}

/** Where packs live on the device, and which people are available, in the order panels use them. */
object NeuralCatalog {
    /** Single-speaker packs first: they are the cleanest recordings. */
    private val order = listOf(VoicePack.NorthernMan, VoicePack.Cori, VoicePack.BritishMen, VoicePack.BritishWomen)

    fun people(installed: Set<VoicePack>, gender: VoiceGender): List<NeuralVoice> =
        order.filter { it in installed && (gender == VoiceGender.Unknown || it.gender == gender) }
            .flatMap { pack -> (0 until pack.speakers).map { NeuralVoice(pack, it) } }

    /** The voice for a panel seat: a distinct person per [slot] of the same gender, or null if none. */
    fun choose(installed: Set<VoicePack>, gender: VoiceGender, slot: Int): NeuralVoice? {
        val pool = people(installed, gender)
        return pool.getOrNull(slot % pool.size.coerceAtLeast(1))
    }
}

/** Downloads and unpacks voice packs into app storage. Each pack is about 70–80 MB. */
class NeuralVoiceStore(context: Context, http: OkHttpClient) {
    private val root = File(context.filesDir, "voices")
    val espeakDir: File get() = File(root, "espeak-ng-data")
    private val client = http.newBuilder()
        .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val lock = Mutex()
    // Downloads outlive the Settings screen.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow(VoicePack.entries.associateWith { state(it) })
    val states: StateFlow<Map<VoicePack, PackState>> = _states.asStateFlow()

    val installed: Set<VoicePack> get() = _states.value.filterValues { it == PackState.Installed }.keys

    fun dir(pack: VoicePack) = File(root, pack.model)
    fun modelFile(pack: VoicePack) = File(dir(pack), "model.onnx")
    fun tokensFile(pack: VoicePack) = File(dir(pack), "tokens.txt")

    private fun state(pack: VoicePack): PackState =
        if (File(dir(pack), OK).exists() && File(espeakDir, OK).exists()) PackState.Installed else PackState.Missing

    private fun set(pack: VoicePack, s: PackState) = _states.update { it + (pack to s) }

    /** Starts downloading [packs] in the background, one after another. */
    fun download(packs: List<VoicePack>) {
        packs.filter { _states.value[it] !is PackState.Downloading && _states.value[it] != PackState.Installed }
            .onEach { set(it, PackState.Downloading(0f)) }
            .let { queue -> if (queue.isNotEmpty()) scope.launch { queue.forEach { install(it) } } }
    }

    /** Downloads one pack (queued behind any other download). Safe to call when already installed. */
    suspend fun install(pack: VoicePack) {
        if (state(pack) == PackState.Installed) return set(pack, PackState.Installed)
        set(pack, PackState.Downloading(0f))
        lock.withLock {
            runCatching { download(pack) }
                .onSuccess { set(pack, PackState.Installed) }
                .onFailure { e ->
                    dir(pack).deleteRecursively()
                    if (e is kotlinx.coroutines.CancellationException) { set(pack, PackState.Missing); throw e }
                    set(pack, PackState.Failed(e.message ?: "Download failed"))
                }
        }
    }

    fun delete(pack: VoicePack) {
        dir(pack).deleteRecursively()
        set(pack, PackState.Missing)
        if (VoicePack.entries.none { File(dir(it), OK).exists() }) espeakDir.deleteRecursively()
    }

    private suspend fun download(pack: VoicePack) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(pack.url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Voice download failed (${response.code}). Try again on Wi-Fi.")
            val body = response.body ?: throw java.io.IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: (pack.sizeMb * 1_000_000L)
            val target = dir(pack).apply { deleteRecursively(); mkdirs() }
            val needEspeak = !File(espeakDir, OK).exists()
            val espeakTmp = File(root, "espeak-ng-data.part").apply { if (needEspeak) { deleteRecursively(); mkdirs() } }
            var read = 0L
            var lastReport = 0f
            val counting = object : FilterInputStream(body.byteStream()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { n ->
                        if (n > 0) {
                            read += n
                            val f = (read.toFloat() / total).coerceAtMost(0.99f)
                            if (f - lastReport > 0.01f) { lastReport = f; set(pack, PackState.Downloading(f)) }
                        }
                    }
            }
            TarArchiveInputStream(BZip2CompressorInputStream(counting.buffered(1 shl 16))).use { tar ->
                while (true) {
                    ensureActive()
                    val entry = tar.nextEntry ?: break
                    // Strip the archive's top folder: "vits-piper-…/espeak-ng-data/en_dict" → "espeak-ng-data/en_dict".
                    val path = entry.name.substringAfter('/', "")
                    if (entry.isDirectory || path.isEmpty() || ".." in path) continue
                    val out = when {
                        path.startsWith("espeak-ng-data/") -> if (needEspeak) File(espeakTmp, path.removePrefix("espeak-ng-data/")) else null
                        path.endsWith(".onnx") -> File(target, "model.onnx")
                        path == "tokens.txt" -> File(target, "tokens.txt")
                        else -> null
                    } ?: continue
                    out.parentFile?.mkdirs()
                    out.outputStream().use { copy(tar, it) }
                }
            }
            if (!modelFile(pack).exists() || !tokensFile(pack).exists()) throw java.io.IOException("The voice download was incomplete. Try again.")
            if (needEspeak) {
                File(espeakTmp, OK).createNewFile()
                espeakDir.deleteRecursively()
                if (!espeakTmp.renameTo(espeakDir)) throw java.io.IOException("Couldn't save the voice data.")
            }
            File(target, OK).createNewFile()
        }
    }

    private fun copy(input: InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
        }
    }

    private companion object {
        const val OK = ".ok"
    }
}
