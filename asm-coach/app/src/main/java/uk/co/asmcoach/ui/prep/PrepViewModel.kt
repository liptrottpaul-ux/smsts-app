package uk.co.asmcoach.ui.prep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.content.MasterclassContent
import uk.co.asmcoach.data.content.PrepChecklist
import uk.co.asmcoach.data.model.BankQuestion
import uk.co.asmcoach.data.model.Lesson
import uk.co.asmcoach.data.model.Masterclass
import uk.co.asmcoach.data.model.RolePack
import uk.co.asmcoach.data.persist.DrillResult
import uk.co.asmcoach.data.persist.PracticeData
import uk.co.asmcoach.data.persist.PracticeStore
import uk.co.asmcoach.voice.SpeechRecognizerManager

data class PrepUiState(
    val expandedModule: String? = null,
    val expandedLessons: Set<String> = emptySet(),
    val completed: Set<String> = emptySet(),
    val personalised: Map<String, String> = emptyMap(),
    val personalising: Set<String> = emptySet(),
    val error: String? = null,
    /** The question currently being practised, its draft answer, and whether the mic is live. */
    val drillKey: String? = null,
    val drillText: String = "",
    val drillListening: Boolean = false,
    val scoring: Boolean = false,
    val revealed: Set<String> = emptySet(),
)

/** How prepared the candidate is for one role, from what they've actually done. */
data class Readiness(
    val percent: Int,
    val verdict: String,
    val questionsPractised: Int,
    val questionsTotal: Int,
    val averageScore: Double,
    val topicsReviewed: Int,
    val topicsTotal: Int,
    val checklistDone: Int,
    val checklistTotal: Int,
    val lastInterviewScore: Int?,
    val lastOfferLikelihood: Int?,
    val nextSteps: List<String>,
)

class PrepViewModel(private val container: AppContainer) : ViewModel() {
    val modules: List<Masterclass> = MasterclassContent.modules
    val speakingId = container.tts.speakingId
    val targetJob = container.roles.job
    val packs = container.roles.packs
    val progress = container.roles.progress
    val practice: StateFlow<PracticeData> = container.practice.data
    private val recognizer = SpeechRecognizerManager(container.app)
    val voiceAvailable: Boolean = recognizer.isAvailable

    private val _state = MutableStateFlow(PrepUiState())
    val state: StateFlow<PrepUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            recognizer.state.collect { s ->
                when (s) {
                    is SpeechRecognizerManager.State.Listening -> _state.update { it.copy(drillText = s.text) }
                    is SpeechRecognizerManager.State.Error -> _state.update { it.copy(drillListening = false, error = s.message) }
                    SpeechRecognizerManager.State.Idle -> Unit
                }
            }
        }
    }

    /** Builds the role pack for the selected job if it isn't ready yet. */
    fun prepareRole() {
        targetJob.value?.let { container.roles.prepare(it) }
    }

    fun toggleModule(id: String) = _state.update {
        it.copy(expandedModule = if (it.expandedModule == id) null else id)
    }

    fun toggleLesson(id: String) {
        _state.update {
            it.copy(expandedLessons = if (id in it.expandedLessons) it.expandedLessons - id else it.expandedLessons + id)
        }
        // Opening a role topic counts as reviewing it.
        if (id.startsWith("topic-")) targetJob.value?.let { container.practice.markDone(it.id, id) }
    }

    fun toggleChecklist(itemId: String) {
        targetJob.value?.let { container.practice.toggleDone(it.id, itemId) }
    }

    fun toggleSpeak(id: String, text: String) {
        val tts = container.tts
        if (tts.speakingId.value == id) {
            tts.stop()
        } else {
            tts.speak(text, id)
            _state.update { it.copy(completed = it.completed + id.substringBefore(':')) }
        }
    }

    fun reveal(key: String) = _state.update { it.copy(revealed = it.revealed + key) }

    // ---- Practising an answer ----------------------------------------------------------------

    fun startDrill(key: String) {
        if (_state.value.drillKey == key) return
        recognizer.cancel()
        _state.update { it.copy(drillKey = key, drillText = "", drillListening = false) }
    }

    fun editDrill(text: String) = _state.update { it.copy(drillText = text) }

    fun toggleMic() {
        if (_state.value.drillListening) {
            recognizer.finishAnswer()
            return
        }
        val before = _state.value.drillText
        _state.update { it.copy(drillListening = true) }
        recognizer.startAnswer { heard ->
            _state.update {
                it.copy(drillListening = false, drillText = listOf(before, heard).filter { t -> t.isNotBlank() }.joinToString(" "))
            }
        }
    }

    /** Scores the practised answer against what the panel looks for and saves the result. */
    fun scoreDrill(key: String, question: BankQuestion) {
        val job = targetJob.value ?: return
        val answer = _state.value.drillText.trim()
        if (answer.split(Regex("\\s+")).size < 8) {
            _state.update { it.copy(error = "Give a fuller answer first: say it the way you would in the room.") }
            return
        }
        recognizer.cancel()
        _state.update { it.copy(scoring = true, drillListening = false, error = null) }
        viewModelScope.launch {
            runCatching {
                container.interviewAgent.scoreDrill(
                    container.settings.current().profile, job.title, question.question, question.lookingFor, answer,
                )
            }.onSuccess { r ->
                container.practice.saveDrill(
                    job.id, key,
                    DrillResult(r.score, listOf(r.verdict, r.feedback).filter { it.isNotBlank() }.joinToString(" "), r.betterAnswer, System.currentTimeMillis()),
                )
                _state.update { it.copy(scoring = false, drillKey = null, drillText = "", revealed = it.revealed + key) }
            }.onFailure { e ->
                _state.update { it.copy(scoring = false, error = e.message ?: "Couldn't score that answer.") }
            }
        }
    }

    fun cancelDrill() {
        recognizer.cancel()
        _state.update { it.copy(drillKey = null, drillText = "", drillListening = false) }
    }

    /** Agent 2: ask Gemini for a talking point grounded in the candidate's own profile. */
    fun personalise(module: Masterclass, lesson: Lesson) {
        if (lesson.id in _state.value.personalising) return
        _state.update { it.copy(personalising = it.personalising + lesson.id, error = null) }
        viewModelScope.launch {
            runCatching {
                container.coachAgent.personalise(module, lesson, container.settings.current().profile, targetJob.value)
            }.onSuccess { text ->
                _state.update {
                    it.copy(personalised = it.personalised + (lesson.id to text), personalising = it.personalising - lesson.id)
                }
            }.onFailure { e ->
                _state.update { it.copy(personalising = it.personalising - lesson.id, error = e.message) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun stopSpeaking() {
        container.tts.stop()
        recognizer.cancel()
        _state.update { it.copy(drillListening = false) }
    }

    override fun onCleared() {
        recognizer.release()
    }

    companion object {
        fun questionKey(q: BankQuestion) = PracticeStore.keyOf(q.question)

        /**
         * Readiness = practised answers (50%), latest mock interview (20%), prep topics (15%) and the
         * preparation checklist (15%). Unpractised questions count as zero, so the score reflects
         * real preparation rather than time spent in the app.
         */
        fun readiness(jobId: String, pack: RolePack, data: PracticeData): Readiness {
            val keys = pack.questions.map(::questionKey)
            val drills = keys.mapNotNull { data.drills["$jobId|$it"] }
            val avg = if (drills.isEmpty()) 0.0 else drills.map { it.score }.average()
            val questionPart = keys.sumOf { k -> data.drills["$jobId|$k"]?.score ?: 0 } / (keys.size * 5.0).coerceAtLeast(1.0)
            val topicsReviewed = pack.prepTopics.indices.count { "$jobId|topic-$it" in data.done }
            val checklistDone = PrepChecklist.items.count { "$jobId|${it.id}" in data.done }
            val lastInterview = data.interviews[jobId]?.lastOrNull()
            val percent = (
                questionPart * 50 +
                    (lastInterview?.score ?: 0) / 100.0 * 20 +
                    topicsReviewed.toDouble() / pack.prepTopics.size.coerceAtLeast(1) * 15 +
                    checklistDone.toDouble() / PrepChecklist.items.size * 15
                ).toInt().coerceIn(0, 100)

            val weakest = pack.questions.groupBy { it.category }
                .mapValues { (_, qs) -> qs.map { data.drills["$jobId|${questionKey(it)}"]?.score ?: 0 }.average() }
                .minByOrNull { it.value }
            val nextSteps = buildList {
                val unpractised = keys.size - drills.size
                if (unpractised > 0) add("Practise $unpractised more of this role's questions out loud.")
                drills.count { it.score <= 2 }.takeIf { it > 0 }?.let { add("Redo the $it answer(s) that scored 2 or less, using the better answer as a guide.") }
                weakest?.takeIf { it.value < 3.5 }?.let { add("Weakest area: ${it.key}. Prepare one more STAR story for it.") }
                if (lastInterview == null) add("Do a full mock interview for this role on the Roleplay tab.")
                else if (lastInterview.offerLikelihood < 60) add("Last mock gave a ${lastInterview.offerLikelihood}% offer chance. Fix its 'to raise your chances' points, then redo it.")
                if (topicsReviewed < pack.prepTopics.size) add("Open the remaining ${pack.prepTopics.size - topicsReviewed} prep topic(s).")
                if (checklistDone < PrepChecklist.items.size) add("Tick off the remaining ${PrepChecklist.items.size - checklistDone} preparation steps.")
            }
            val verdict = when {
                percent >= 80 && (lastInterview?.offerLikelihood ?: 0) >= 60 -> "Ready for this interview"
                percent >= 55 -> "Getting there"
                percent >= 25 -> "Started, not ready yet"
                else -> "Not prepared yet"
            }
            return Readiness(
                percent, verdict, drills.size, keys.size, avg, topicsReviewed, pack.prepTopics.size,
                checklistDone, PrepChecklist.items.size, lastInterview?.score, lastInterview?.offerLikelihood, nextSteps,
            )
        }
    }
}
