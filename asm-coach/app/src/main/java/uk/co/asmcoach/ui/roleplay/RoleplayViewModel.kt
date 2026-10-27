package uk.co.asmcoach.ui.roleplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.agent.InterviewConfig
import uk.co.asmcoach.data.model.BankQuestion
import uk.co.asmcoach.data.model.CrisisScenario
import uk.co.asmcoach.data.model.Evaluation
import uk.co.asmcoach.data.model.InterviewMode
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.Persona
import uk.co.asmcoach.data.model.QuestionCategory
import uk.co.asmcoach.data.model.RolePack
import uk.co.asmcoach.data.model.SessionKind
import uk.co.asmcoach.data.model.SessionRecord
import uk.co.asmcoach.data.model.Speaker
import uk.co.asmcoach.data.model.Turn
import uk.co.asmcoach.data.remote.AppJson
import uk.co.asmcoach.voice.SpeechRecognizerManager
import uk.co.asmcoach.voice.VoiceStyle
import java.util.UUID

enum class Stage { Setup, Live, Evaluating, Results }

/** One interviewer on the panel; [voice] picks the TTS pitch, rate and voice slot. */
data class Seat(val name: String, val role: String, val voice: Persona) {
    val initials: String get() = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }
}

/** A pre-written question from the role pack, asked instantly with no AI call. */
private data class PlannedQuestion(val seat: Int, val text: String, val bank: BankQuestion?)

/** What the live interview loop is doing right now. */
enum class Phase { Thinking, Speaking, Listening, Typing, Paused, Idle }

data class RoleplayUiState(
    val stage: Stage = Stage.Setup,
    val mode: InterviewMode = InterviewMode.StandardPanel,
    val scenario: CrisisScenario = CrisisScenario.HseVisit,
    /** Index into the role pack's own scenarios, used instead of [scenario] when a pack is active. */
    val roleScenarioIndex: Int = 0,
    val questionCount: Int = 5,
    /** Questions in this interview (the bank can hold fewer than [questionCount]). */
    val total: Int = 5,
    val seats: List<Seat> = emptyList(),
    val activeSeat: Seat? = null,
    val tailorToJob: Boolean = true,
    val transcript: List<Turn> = emptyList(),
    val phase: Phase = Phase.Idle,
    val liveText: String = "",
    val micLevel: Float = 0f,
    val evaluation: Evaluation? = null,
    val error: String? = null,
    val evaluationFailed: Boolean = false,
) {
    val answered: Int get() = transcript.count { it.speaker == Speaker.Candidate }
}

class RoleplayViewModel(private val container: AppContainer) : ViewModel() {

    private val recognizer = SpeechRecognizerManager(container.app)
    private val tts = container.tts
    private var loop: Job? = null
    private var typedOnly = false
    private var plan: List<PlannedQuestion> = emptyList()
    private var planIndex = 0

    private val _state = MutableStateFlow(RoleplayUiState())
    val state: StateFlow<RoleplayUiState> = _state.asStateFlow()

    val targetJob: StateFlow<JobListing?> = container.roles.job

    /** The role pack for the selected job, once prepared. */
    val pack: StateFlow<RolePack?> = combine(container.roles.job, container.roles.packs) { job, packs ->
        job?.let { packs[it.id] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val prepProgress = container.roles.progress

    fun prepareRole() {
        targetJob.value?.let { container.roles.prepare(it) }
    }
    val speakingId = tts.speakingId
    val history: StateFlow<List<SessionRecord>> = container.sessions.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val voiceInputAvailable: Boolean = recognizer.isAvailable

    /** The panel runs on Gemini; without a key the interview can't start. */
    val hasGemini: StateFlow<Boolean> = container.settings.settings
        .map { it.hasGemini }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            container.settings.settings.collect { tts.baseRate = it.speechRate }
        }
        viewModelScope.launch {
            recognizer.state.collect { s ->
                when (s) {
                    is SpeechRecognizerManager.State.Listening ->
                        _state.update { it.copy(liveText = s.text, micLevel = s.level) }
                    is SpeechRecognizerManager.State.Error ->
                        _state.update { it.copy(phase = Phase.Typing, micLevel = 0f, error = s.message) }
                    SpeechRecognizerManager.State.Idle -> _state.update { it.copy(micLevel = 0f) }
                }
            }
        }
    }

    // ---- Setup -------------------------------------------------------------------------------

    fun setMode(mode: InterviewMode) = _state.update { it.copy(mode = mode) }
    fun setScenario(scenario: CrisisScenario) = _state.update { it.copy(scenario = scenario) }
    fun setRoleScenario(index: Int) = _state.update { it.copy(roleScenarioIndex = index) }
    fun setQuestionCount(count: Int) = _state.update { it.copy(questionCount = count) }
    fun setTailor(value: Boolean) = _state.update { it.copy(tailorToJob = value) }

    /** [voice] is false when the microphone permission was refused: the candidate types instead. */
    fun start(voice: Boolean) {
        typedOnly = !voice || !voiceInputAvailable
        val s = _state.value
        val activePack = activePack()
        plan = activePack?.let { buildPlan(it, s) }.orEmpty()
        planIndex = 0
        val seats = activePack?.panel?.takeIf { it.isNotEmpty() }
            ?.mapIndexed { i, m -> Seat(m.name, m.role, Persona.entries[i % Persona.entries.size]) }
            ?: s.mode.personas.map { Seat(it.displayName, it.role, it) }
        val total = if (plan.isNotEmpty()) minOf(s.questionCount, plan.size) else s.questionCount
        _state.update {
            it.copy(
                stage = Stage.Live, transcript = emptyList(), evaluation = null, error = null, liveText = "",
                seats = seats, activeSeat = seats.first(), total = total,
            )
        }
        runLoop { interviewerTurn() }
    }

    private fun activePack(): RolePack? = pack.value?.takeIf { _state.value.tailorToJob && it.questions.isNotEmpty() }

    private fun config(): InterviewConfig {
        val s = _state.value
        val job = targetJob.value?.takeIf { s.tailorToJob }
        val activePack = activePack()
        return InterviewConfig(
            mode = s.mode,
            scenario = s.scenario.takeIf { s.mode == InterviewMode.CrisisSimulation && activePack == null },
            questionCount = s.total,
            job = job,
            brief = job?.let { container.roles.briefs.value[it.id] },
            panel = activePack?.panel.orEmpty(),
            roleScenario = activePack?.scenarios?.getOrNull(s.roleScenarioIndex)
                ?.takeIf { s.mode == InterviewMode.CrisisSimulation },
            askedQuestions = plan.take(planIndex).mapNotNull { it.bank },
        )
    }

    /** Orders the role pack's questions for the chosen mode, alternating categories. */
    private fun buildPlan(pack: RolePack, s: RoleplayUiState): List<PlannedQuestion> {
        fun bank(categories: List<String>): List<PlannedQuestion> {
            val byCategory = categories.map { cat -> pack.questions.filter { it.category.equals(cat, ignoreCase = true) } }
            val interleaved = buildList {
                val longest = byCategory.maxOfOrNull { it.size } ?: 0
                for (i in 0 until longest) byCategory.forEach { list -> list.getOrNull(i)?.let(::add) }
            }
            val rest = pack.questions.filter { it !in interleaved }
            return (interleaved + rest).map { PlannedQuestion(it.askedBy, it.question, it) }
        }
        return when (s.mode) {
            InterviewMode.StandardPanel -> bank(QuestionCategory.ALL)
            InterviewMode.DigitalSiteTech -> bank(listOf(QuestionCategory.DIGITAL, QuestionCategory.TECHNICAL, QuestionCategory.COMMERCIAL))
            InterviewMode.TechnicalTradeQa -> bank(listOf(QuestionCategory.TECHNICAL, QuestionCategory.SAFETY, QuestionCategory.DIGITAL))
            InterviewMode.CrisisSimulation -> {
                val scenario = pack.scenarios.getOrNull(s.roleScenarioIndex) ?: pack.scenarios.firstOrNull()
                if (scenario == null) {
                    bank(listOf(QuestionCategory.SAFETY, QuestionCategory.TECHNICAL))
                } else {
                    val qs = scenario.questions.ifEmpty { listOf("What do you do first, and who do you call?") }
                    val opening = PlannedQuestion(scenario.askedBy, "Let's run a live scenario. ${scenario.briefing} ${qs.first()}", null)
                    listOf(opening) + qs.drop(1).map { PlannedQuestion(scenario.askedBy, it, null) } +
                        bank(listOf(QuestionCategory.SAFETY, QuestionCategory.TECHNICAL))
                }
            }
        }
    }

    // ---- Live loop ---------------------------------------------------------------------------

    private fun runLoop(block: suspend () -> Unit) {
        loop?.cancel()
        loop = viewModelScope.launch { block() }
    }

    private suspend fun interviewerTurn() {
        val planned = plan.getOrNull(planIndex)
        val seats = _state.value.seats
        val (seat, text) = if (planned != null) {
            // Role pack: the next question is already written, so it's asked immediately.
            val seat = seats.getOrElse(planned.seat) { seats.first() }
            val intro = if (planIndex == 0) {
                "Thanks for coming in, ${container.settings.current().candidateName}. I'm ${seat.name}, ${seat.role}. "
            } else {
                ""
            }
            planIndex++
            seat to intro + planned.text
        } else {
            _state.update { it.copy(phase = Phase.Thinking, error = null) }
            val profile = container.settings.current().profile
            val result = runCatching { container.interviewAgent.nextTurn(config(), profile, _state.value.transcript) }
            val (persona, line) = result.getOrElse { e ->
                _state.update { it.copy(phase = Phase.Idle, error = e.message ?: "The panel couldn't respond.") }
                return
            }
            (seats.firstOrNull { it.voice == persona } ?: Seat(persona.displayName, persona.role, persona)) to line
        }
        _state.update {
            it.copy(
                transcript = it.transcript + Turn(Speaker.Interviewer, text, seat.voice, seat.name, seat.role),
                activeSeat = seat,
                phase = Phase.Speaking,
                error = null,
            )
        }
        tts.speakAndAwait(text, seat.voice.voice())
        awaitAnswer()
    }

    private fun awaitAnswer() {
        if (typedOnly) {
            _state.update { it.copy(phase = Phase.Typing, liveText = "") }
            return
        }
        _state.update { it.copy(phase = Phase.Listening, liveText = "") }
        recognizer.startAnswer { text -> submitAnswer(text) }
    }

    /** Candidate taps "Done" while speaking. */
    fun finishSpeaking() = recognizer.finishAnswer()

    /** Skip the remainder of the interviewer's line and start answering. */
    fun skipQuestionAudio() = tts.stop()

    fun switchToTyping() {
        recognizer.cancel()
        typedOnly = true
        _state.update { it.copy(phase = Phase.Typing) }
    }

    fun switchToVoice() {
        if (!voiceInputAvailable) return
        typedOnly = false
        awaitAnswer()
    }

    fun submitAnswer(text: String) {
        val answer = text.trim()
        if (answer.isEmpty()) {
            _state.update {
                it.copy(phase = if (typedOnly) Phase.Typing else Phase.Idle, error = "I didn't catch an answer. Try again.")
            }
            return
        }
        _state.update {
            it.copy(transcript = it.transcript + Turn(Speaker.Candidate, answer), liveText = "", error = null)
        }
        runLoop {
            if (_state.value.answered >= _state.value.total) closeAndEvaluate() else interviewerTurn()
        }
    }

    /** Retry after an error: re-ask if the panel failed, or re-open the floor for an answer. */
    fun retry() {
        if (_state.value.evaluationFailed) {
            runLoop { evaluate() }
            return
        }
        val last = _state.value.transcript.lastOrNull()
        if (last == null || last.speaker == Speaker.Candidate) runLoop { interviewerTurn() } else awaitAnswer()
    }

    fun pause() {
        if (_state.value.stage != Stage.Live) return
        loop?.cancel()
        recognizer.cancel()
        tts.stop()
        _state.update { it.copy(phase = Phase.Paused, micLevel = 0f) }
    }

    fun resume() {
        if (_state.value.phase == Phase.Paused) retry()
    }

    fun endEarly() {
        recognizer.cancel()
        tts.stop()
        if (_state.value.answered == 0) reset() else runLoop { evaluate() }
    }

    private suspend fun closeAndEvaluate() {
        val lead = _state.value.seats.firstOrNull() ?: Seat("Panel", "Interviewer", Persona.ProjectDirector)
        val closing = "Thanks, that's everything from the panel today. We'll be in touch shortly."
        _state.update {
            it.copy(
                transcript = it.transcript + Turn(Speaker.Interviewer, closing, lead.voice, lead.name, lead.role),
                activeSeat = lead,
                phase = Phase.Speaking,
            )
        }
        tts.speakAndAwait(closing, lead.voice.voice())
        evaluate()
    }

    private suspend fun evaluate() {
        _state.update { it.copy(stage = Stage.Evaluating, phase = Phase.Idle, error = null, evaluationFailed = false) }
        val s = _state.value
        val result = runCatching {
            container.interviewAgent.evaluate(config(), container.settings.current().profile, s.transcript)
        }
        result.onSuccess { evaluation ->
            _state.update { it.copy(stage = Stage.Results, evaluation = evaluation) }
            container.sessions.save(
                SessionRecord(
                    id = UUID.randomUUID().toString(),
                    kind = SessionKind.Interview,
                    title = targetJob.value?.takeIf { s.tailorToJob }?.title
                        ?: (s.mode.title + if (s.mode == InterviewMode.CrisisSimulation) " · ${s.scenario.title}" else ""),
                    subtitle = evaluation.readinessBand,
                    score = evaluation.overallScore,
                    createdAt = System.currentTimeMillis(),
                    payload = AppJson.encodeToString(Evaluation.serializer(), evaluation),
                ),
            )
        }.onFailure { e ->
            _state.update {
                it.copy(stage = Stage.Live, phase = Phase.Idle, evaluationFailed = true, error = e.message ?: "Evaluation failed.")
            }
        }
    }

    fun reset() {
        loop?.cancel()
        recognizer.cancel()
        tts.stop()
        _state.update {
            RoleplayUiState(
                mode = it.mode,
                scenario = it.scenario,
                roleScenarioIndex = it.roleScenarioIndex,
                questionCount = it.questionCount,
                tailorToJob = it.tailorToJob,
            )
        }
    }

    fun toggleSpeak(id: String, text: String) {
        if (tts.speakingId.value == id) tts.stop() else tts.speak(text, id)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        loop?.cancel()
        recognizer.release()
        tts.stop()
    }

    private fun Persona.voice() = VoiceStyle(pitch = pitch, rate = rate, voiceSlot = ordinal)
}
