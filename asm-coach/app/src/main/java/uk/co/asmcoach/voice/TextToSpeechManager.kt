package uk.co.asmcoach.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

enum class VoiceGender { Male, Female, Unknown }

/**
 * Which voice a speaker uses. [slot] picks a different voice of the same gender for each panel
 * member. Pitch stays at 1.0 unless no voice of the right gender exists: shifting pitch is what
 * makes engine voices sound robotic.
 */
data class VoiceStyle(
    val rate: Float = 1f,
    val slot: Int = 0,
    val gender: VoiceGender = VoiceGender.Unknown,
) {
    companion object {
        val Narrator = VoiceStyle()
    }
}

/** How many usable British English voices of each gender the device has. */
data class VoiceSummary(val male: Int, val female: Int, val other: Int, val online: Int) {
    val hasBoth: Boolean get() = male > 0 && female > 0
}

/** Recognises voice gender from engine naming schemes (Android exposes no gender API). */
object VoiceCatalog {
    // Google Speech Services en-GB voices, e.g. "en-gb-x-gbb-network".
    private val googleMale = setOf("gbb", "gbd", "rjs")
    private val googleFemale = setOf("gba", "gbc", "gbg", "fis")

    fun genderOf(name: String, features: Set<String>): VoiceGender {
        val n = name.lowercase()
        val f = features.joinToString(" ").lowercase()
        return when {
            "female" in n || "female" in f -> VoiceGender.Female
            Regex("\\bmale\\b|[-_]male").containsMatchIn(n) || Regex("\\bmale\\b").containsMatchIn(f) -> VoiceGender.Male
            googleMale.any { "-x-$it" in n } -> VoiceGender.Male
            googleFemale.any { "-x-$it" in n } -> VoiceGender.Female
            // Samsung: "en-GB-SMTm00" / "en-GB-SMTf00".
            Regex("smtm\\d").containsMatchIn(n) -> VoiceGender.Male
            Regex("smtf\\d").containsMatchIn(n) -> VoiceGender.Female
            else -> VoiceGender.Unknown
        }
    }
}

/**
 * Wraps Android's TextToSpeech engine: British English voices matched to each speaker's gender,
 * natural (network) voices preferred when online, long-text chunking and suspend-until-finished.
 */
class TextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** The id passed to [speak] that is currently playing, or null. */
    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    private val _summary = MutableStateFlow(VoiceSummary(0, 0, 0, 0))
    val summary: StateFlow<VoiceSummary> = _summary.asStateFlow()

    private val waiters = ConcurrentHashMap<String, CancellableContinuation<Unit>>()

    private data class Entry(val voice: Voice, val gender: VoiceGender) {
        val online: Boolean get() = voice.isNetworkConnectionRequired
    }

    private var voices: List<Entry> = emptyList()

    @Volatile
    var baseRate: Float = 1f

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.UK)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        refreshVoices()
        tts.setOnUtteranceProgressListener(listener)
        _ready.value = true
    }

    /** Re-reads installed voices (e.g. after the user downloads voice data). */
    fun refreshVoices() {
        voices = runCatching {
            tts.voices.orEmpty()
                .filter { it.locale.language == "en" && it.locale.country == "GB" }
                .filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
                .map { Entry(it, VoiceCatalog.genderOf(it.name, it.features.orEmpty())) }
                .sortedWith(compareByDescending<Entry> { it.voice.quality }.thenBy { it.voice.name })
        }.getOrDefault(emptyList())
        _summary.value = VoiceSummary(
            male = distinctPeople(VoiceGender.Male),
            female = distinctPeople(VoiceGender.Female),
            other = distinctPeople(VoiceGender.Unknown),
            online = voices.count { it.online },
        )
    }

    /** Local and network variants of one Google voice ("gbb-local", "gbb-network") are the same person. */
    private fun distinctPeople(gender: VoiceGender): Int =
        voices.filter { it.gender == gender }.map { personKey(it.voice.name) }.distinct().size

    private fun personKey(name: String): String = name.lowercase().removeSuffix("-local").removeSuffix("-network")

    private fun isOnline(): Boolean = runCatching {
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(false)

    /**
     * Picks the voice for [style]: same-gender voices first, one distinct person per slot, the
     * natural network variant when online. Returns the voice and the pitch to use.
     */
    private fun choose(style: VoiceStyle): Pair<Voice?, Float> {
        if (voices.isEmpty()) return null to fallbackPitch(style.gender)
        val online = isOnline()
        val usable = voices.filter { online || !it.online }
        val matching = if (style.gender == VoiceGender.Unknown) usable else usable.filter { it.gender == style.gender }
        val pool = matching.ifEmpty { usable.filter { it.gender == VoiceGender.Unknown }.ifEmpty { usable } }
        // One entry per person, preferring the network (more natural) variant when available.
        val people = pool.groupBy { personKey(it.voice.name) }.values.map { variants ->
            variants.firstOrNull { it.online && online } ?: variants.first()
        }
        val pick = people.getOrNull(style.slot % people.size.coerceAtLeast(1))
        val genderMatched = pick != null && (style.gender == VoiceGender.Unknown || pick.gender == style.gender)
        return pick?.voice to if (genderMatched) 1f else fallbackPitch(style.gender)
    }

    /** Only used when the device has no voice of the needed gender: a gentle shift, not a robotic one. */
    private fun fallbackPitch(gender: VoiceGender): Float = when (gender) {
        VoiceGender.Male -> 0.9f
        VoiceGender.Female -> 1.1f
        VoiceGender.Unknown -> 1f
    }

    /** Fire-and-forget playback. Replaces anything currently speaking. */
    fun speak(text: String, id: String, style: VoiceStyle = VoiceStyle.Narrator) {
        if (!_ready.value || text.isBlank()) return
        stop()
        val (voice, pitch) = choose(style)
        voice?.let { tts.setVoice(it) }
        tts.setPitch(pitch)
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
