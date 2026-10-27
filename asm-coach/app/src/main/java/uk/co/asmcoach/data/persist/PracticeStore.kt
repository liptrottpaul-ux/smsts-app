package uk.co.asmcoach.data.persist

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import uk.co.asmcoach.data.remote.AppJson

private val Context.practiceStore by preferencesDataStore(name = "practice")

/** One scored practice answer from the Prep tab. */
@Serializable
data class DrillResult(
    val score: Int = 0,
    val feedback: String = "",
    val betterAnswer: String = "",
    val at: Long = 0,
)

/** A finished mock interview for a role. */
@Serializable
data class InterviewResult(val score: Int = 0, val offerLikelihood: Int = 0, val at: Long = 0)

@Serializable
data class PracticeData(
    /** Question key → how many times it has been asked in mock interviews. */
    val asked: Map<String, Int> = emptyMap(),
    /** "jobId|questionKey" → best drill result. */
    val drills: Map<String, DrillResult> = emptyMap(),
    /** "jobId|itemId" for ticked preparation checklist items and reviewed topics. */
    val done: Set<String> = emptySet(),
    /** jobId → mock interview results, newest last. */
    val interviews: Map<String, List<InterviewResult>> = emptyMap(),
)

/** Practice history that drives question rotation and the Prep readiness score. */
class PracticeStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val key = stringPreferencesKey("practice")
    private val _data = MutableStateFlow(PracticeData())
    val data: StateFlow<PracticeData> = _data.asStateFlow()

    private val loaded = scope.launch {
        val raw = context.practiceStore.data.first()[key] ?: return@launch
        runCatching { AppJson.decodeFromString(PracticeData.serializer(), raw) }.getOrNull()?.let { _data.value = it }
    }

    fun markAsked(questionKeys: List<String>) = change { d ->
        d.copy(asked = d.asked + questionKeys.associateWith { (d.asked[it] ?: 0) + 1 })
    }

    fun saveDrill(jobId: String, questionKey: String, result: DrillResult) = change { d ->
        val k = "$jobId|$questionKey"
        val best = d.drills[k]
        // Keep the best score, but always show the latest feedback.
        val merged = if (best != null && best.score > result.score) result.copy(score = best.score) else result
        d.copy(drills = d.drills + (k to merged))
    }

    fun toggleDone(jobId: String, itemId: String) = change { d ->
        val k = "$jobId|$itemId"
        d.copy(done = if (k in d.done) d.done - k else d.done + k)
    }

    fun markDone(jobId: String, itemId: String) = change { d -> d.copy(done = d.done + "$jobId|$itemId") }

    fun saveInterview(jobId: String, result: InterviewResult) = change { d ->
        d.copy(interviews = d.interviews + (jobId to ((d.interviews[jobId].orEmpty() + result).takeLast(20))))
    }

    private fun change(transform: (PracticeData) -> PracticeData) {
        scope.launch {
            loaded.join()
            _data.update(transform)
            val json = AppJson.encodeToString(PracticeData.serializer(), _data.value)
            context.practiceStore.edit { it[key] = json }
        }
    }

    companion object {
        /** Stable key for a question's text (bank ids are used where available). */
        fun keyOf(text: String): String = "t" + text.lowercase().filter { it.isLetterOrDigit() }.hashCode().toUInt().toString(16)
    }
}
