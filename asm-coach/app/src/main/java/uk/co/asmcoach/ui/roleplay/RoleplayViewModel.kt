package uk.co.asmcoach.ui.roleplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.agent.InterviewConfig
import uk.co.asmcoach.data.model.CrisisScenario
import uk.co.asmcoach.data.model.Evaluation
import uk.co.asmcoach.data.model.InterviewMode
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.Persona
import uk.co.asmcoach.data.model.SessionKind
import uk.co.asmcoach.data.model.SessionRecord
import uk.co.asmcoach.data.model.Speaker
import uk.co.asmcoach.data.model.Turn
import uk.co.asmcoach.data.remote.AppJson
import uk.co.asmcoach.voice.SpeechRecognizerManager
import uk.co.asmcoach.voice.VoiceStyle
import java.util.UUID

enum class Stage { Setup, Live, Evaluating, Results }

/** What the live interview loop is doing right now. */
enum class Phase { Thinking, Speaking, Listening, Typing, Paused, Idle }

data class RoleplayUiState(
    val stage: Stage = Stage.Setup,
    val mode: InterviewMode = InterviewMode.StandardPanel,
    val scenario: CrisisScenario = CrisisScenario.HseVisit,
    val questionCount: Int = 5,
    val tailorToJob: Boolean = true,
    val transcript: List<Turn> = emptyList(),
    val phase: Phase = Phase.Idle,
    val activePersona: Persona? = null,
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

    private val _state = MutableStateFlow(RoleplayUiState())
    val state: StateFlow<RoleplayUiState> = _state.asStateFlow()

    val targetJob: StateFlow<JobListing?> = container.selection.job
    val speakingId = tts.speakingId
    val history: StateFlow<List<SessionRecord>> = container.sessions.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val voiceInputAvailable: Boolean = recognizer.isAvailable

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
    fun setQuestionCount(count: Int) = _state.update { it.copy(questionCount = count) }
    fun setTailor(value: Boolean) = _state.update { it.copy(tailorToJob = value) }

    /** [voice] is false when the microphone permission was refused: the candidate types instead. */
    fun start(voice: Boolean) {
        typedOnly = !voice || !voiceInputAvailable
        _state.update {
            it.copy(stage = Stage.Live, transcript = emptyList(), evaluation = null, error = null, liveText = "")
        }
        runLoop { interviewerTurn() }
    }

    private fun config(): InterviewConfig {
        val s = _state.value
        val job = targetJob.value?.takeIf { s.tailorToJob }
        return InterviewConfig(
            mode = s.mode,
            scenario = s.scenario.takeIf { s.mode == InterviewMode.CrisisSimulation },
            questionCount = s.questionCount,
            job = job,
            brief = job?.let { container.selection.briefs.value[it.id] },
        )
    }

    // ---- Live loop ---------------------------------------------------------------------------

    private fun runLoop(block: suspend () -> Unit) {
        loop?.cancel()
        loop = viewModelScope.launch { block() }
    }

    private suspend fun interviewerTurn() {
        _state.update { it.copy(phase = Phase.Thinking, error = null) }
        val profile = container.settings.current().profile
        val result = runCatching { container.interviewAgent.nextTurn(config(), profile, _state.value.transcript) }
        val (persona, text) = result.getOrElse { e ->
            _state.update { it.copy(phase = Phase.Idle, error = e.message ?: "The panel couldn't respond.") }
            return
        }
        _state.update {
            it.copy(
                transcript = it.transcript + Turn(Speaker.Interviewer, text, persona),
                activePersona = persona,
                phase = Phase.Speaking,
            )
        }
        tts.speakAndAwait(text, persona.voice())
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
            if (_state.value.answered >= _state.value.questionCount) closeAndEvaluate() else interviewerTurn()
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
        val lead = _state.value.mode.personas.first()
        val closing = "Thanks, that's everything from the panel today. We'll be in touch shortly."
        _state.update {
            it.copy(transcript = it.transcript + Turn(Speaker.Interviewer, closing, lead), activePersona = lead, phase = Phase.Speaking)
        }
        tts.speakAndAwait(closing, lead.voice())
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
                    title = s.mode.title + if (s.mode == InterviewMode.CrisisSimulation) " · ${s.scenario.title}" else "",
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
            RoleplayUiState(mode = it.mode, scenario = it.scenario, questionCount = it.questionCount, tailorToJob = it.tailorToJob)
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
