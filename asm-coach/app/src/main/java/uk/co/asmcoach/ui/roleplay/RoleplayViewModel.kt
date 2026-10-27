package uk.co.asmcoach.ui.roleplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.agent.InterviewConfig
import uk.co.asmcoach.data.content.QuestionBank
import uk.co.asmcoach.data.content.Sector
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
import uk.co.asmcoach.data.persist.InterviewResult
import uk.co.asmcoach.data.persist.PracticeStore
import uk.co.asmcoach.data.remote.AppJson
import uk.co.asmcoach.voice.SpeechRecognizerManager
import uk.co.asmcoach.voice.VoiceStyle
import java.util.UUID

enum class Stage { Setup, Live, Evaluating, Results }

/** One interviewer on the panel; [voice] picks the TTS pitch, rate and voice slot. */
data class Seat(val name: String, val role: String, val voice: Persona) {
    val initials: String get() = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }
}

/** A main question in this interview: from the role pack or the researched bank. */
private data class PlannedQuestion(
    val seat: Int,
    val text: String,
    val bank: BankQuestion,
    val key: String,
    val fromRole: Boolean = false,
)

/** What the live interview loop is doing right now. */
enum class Phase { Thinking, Speaking, Listening, Typing, Paused, Idle }

data class RoleplayUiState(
    val stage: Stage = Stage.Setup,
    val mode: InterviewMode = InterviewMode.StandardPanel,
    val scenario: CrisisScenario = CrisisScenario.HseVisit,
    /** Index into the role pack's own scenarios, used instead of [scenario] when a pack is active. */
    val roleScenarioIndex: Int = 0,
    val questionCount: Int = 8,
    /** Main questions in this interview; follow-ups come on top. */
    val total: Int = 8,
    val mainAsked: Int = 0,
    val onFollowUp: Boolean = false,
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
    private val practice = container.practice
    private var loop: Job? = null
    private var typedOnly = false
    private var plan: List<PlannedQuestion> = emptyList()
    private var planIndex = 0
    private var probesOnCurrent = 0
    private var threadStart = 0

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

    /** Scoring needs Gemini; without a key the interview can't start. */
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
        val seats = activePack?.panel?.takeIf { it.isNotEmpty() }
            ?.mapIndexed { i, m -> Seat(m.name, m.role, Persona.entries[i % Persona.entries.size]) }
            ?: s.mode.personas.map { Seat(it.displayName, it.role, it) }
        plan = buildPlan(activePack, seats, s)
        planIndex = 0
        probesOnCurrent = 0
        threadStart = 0
        _state.update {
            it.copy(
                stage = Stage.Live, transcript = emptyList(), evaluation = null, error = null, liveText = "",
                seats = seats, activeSeat = seats.first(), total = plan.size, mainAsked = 0, onFollowUp = false,
            )
        }
        runLoop { askNextMain() }
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
            askedQuestions = plan.take(planIndex).map { it.bank },
        )
    }

    /**
     * Picks this interview's main questions: role-pack questions plus the researched bank, filtered
     * to the employer type, rotating categories, and putting never-asked questions first so repeat
     * sessions don't churn the same few questions.
     */
    private fun buildPlan(pack: RolePack?, seats: List<Seat>, s: RoleplayUiState): List<PlannedQuestion> {
        val asked = practice.data.value.asked
        val job = targetJob.value?.takeIf { s.tailorToJob }
        val sector = job?.let { QuestionBank.sectorOf("${it.title} ${it.snippet} ${it.company.orEmpty()}") }
        val candidates = buildList {
            pack?.questions?.forEach { q ->
                add(PlannedQuestion(q.askedBy.coerceIn(0, seats.lastIndex), q.question, q, PracticeStore.keyOf(q.question), fromRole = true))
            }
            QuestionBank.all
                .filter { it.sector == Sector.Any || it.sector == sector || (sector == null && it.sector == Sector.Contractor) }
                .forEach { c ->
                    val bank = BankQuestion(c.category, 0, c.question, c.lookingFor, "", c.probes)
                    add(PlannedQuestion(seatFor(c.category, seats), c.question, bank, c.id))
                }
        }
        val order = when (s.mode) {
            InterviewMode.StandardPanel -> listOf(
                QuestionCategory.MOTIVATION, QuestionCategory.SAFETY, QuestionCategory.PEOPLE, QuestionCategory.QUALITY,
                QuestionCategory.PROGRAMME, QuestionCategory.COMMERCIAL, QuestionCategory.TECHNICAL,
                QuestionCategory.SCENARIO, QuestionCategory.CUSTOMER, QuestionCategory.DIGITAL,
            )
            InterviewMode.CrisisSimulation -> listOf(
                QuestionCategory.SCENARIO, QuestionCategory.SAFETY, QuestionCategory.PEOPLE, QuestionCategory.PROGRAMME,
            )
            InterviewMode.DigitalSiteTech -> listOf(
                QuestionCategory.DIGITAL, QuestionCategory.PROGRAMME, QuestionCategory.QUALITY, QuestionCategory.COMMERCIAL,
            )
            InterviewMode.TechnicalTradeQa -> listOf(
                QuestionCategory.TECHNICAL, QuestionCategory.QUALITY, QuestionCategory.SAFETY,
            )
        }
        // Within a category: least-asked first, role-specific before generic, then shuffled for variety.
        val pools = order.associateWith { cat ->
            candidates.filter { it.bank.category.equals(cat, ignoreCase = true) }
                .shuffled()
                .sortedWith(compareBy({ asked[it.key] ?: 0 }, { if (it.fromRole) 0 else 1 }))
                .toMutableList()
        }
        val chosen = mutableListOf<PlannedQuestion>()
        if (s.mode == InterviewMode.CrisisSimulation) {
            scenarioOpening(pack, seats, s)?.let { chosen += it }
        }
        var progress = true
        while (chosen.size < s.questionCount && progress) {
            progress = false
            for (cat in order) {
                if (chosen.size >= s.questionCount) break
                val next = pools[cat]?.removeFirstOrNull() ?: continue
                if (chosen.none { it.key == next.key }) {
                    chosen += next
                    progress = true
                }
            }
        }
        return chosen
    }

    private fun scenarioOpening(pack: RolePack?, seats: List<Seat>, s: RoleplayUiState): PlannedQuestion? {
        val sc = pack?.scenarios?.getOrNull(s.roleScenarioIndex) ?: pack?.scenarios?.firstOrNull()
        return if (sc != null) {
            val first = sc.questions.firstOrNull() ?: "What do you do first, and who do you call?"
            val bank = BankQuestion(
                QuestionCategory.SCENARIO, sc.askedBy, "${sc.briefing} $first",
                "Safety first, clear priorities and sequence, escalation to the right people, legal duties, records.",
                "", sc.questions.drop(1),
            )
            PlannedQuestion(sc.askedBy.coerceIn(0, seats.lastIndex), "Let's run a live scenario. ${sc.briefing} $first", bank, PracticeStore.keyOf(sc.briefing))
        } else if (pack == null) {
            val crisis = s.scenario
            val bank = BankQuestion(
                QuestionCategory.SCENARIO, 0, crisis.briefing,
                "Safety first, clear priorities and sequence, escalation to the right people, legal duties, records.",
                "", listOf("What's the very first thing you do?", "Who do you call, in what order?"),
            )
            PlannedQuestion(seatFor(QuestionCategory.SAFETY, seats), "Let's run a live scenario. ${crisis.briefing} What do you do?", bank, "crisis-${crisis.name}")
        } else {
            null
        }
    }

    /** Routes a question to the panel member whose job title fits it. */
    private fun seatFor(category: String, seats: List<Seat>): Int {
        fun find(vararg words: String) = seats.indexOfFirst { seat -> words.any { seat.role.contains(it, ignoreCase = true) } }
        val idx = when (category) {
            QuestionCategory.SAFETY, QuestionCategory.QUALITY, QuestionCategory.SCENARIO, QuestionCategory.TECHNICAL ->
                find("H&S", "Safety", "SHE", "Quality", "Technical", "Auditor")
            QuestionCategory.COMMERCIAL, QuestionCategory.DIGITAL -> find("Commercial", "QS", "Surveyor", "Cost")
            QuestionCategory.PROGRAMME -> find("Project", "Build", "Construction", "Site")
            else -> 0
        }
        return if (idx >= 0) idx else 0
    }

    // ---- Live loop ---------------------------------------------------------------------------

    private fun runLoop(block: suspend () -> Unit) {
        loop?.cancel()
        loop = viewModelScope.launch { block() }
    }

    private suspend fun askNextMain() {
        val planned = plan.getOrNull(planIndex)
        if (planned == null) {
            closeAndEvaluate()
            return
        }
        val seats = _state.value.seats
        val seat = seats.getOrElse(planned.seat) { seats.first() }
        val intro = if (planIndex == 0) {
            "Thanks for coming in, ${container.settings.current().candidateName}. I'm ${seat.name}, ${seat.role}. "
        } else {
            ""
        }
        planIndex++
        probesOnCurrent = 0
        threadStart = _state.value.transcript.size
        speakQuestion(seat, intro + planned.text, followUp = false)
    }

    private suspend fun speakQuestion(seat: Seat, text: String, followUp: Boolean) {
        _state.update {
            it.copy(
                transcript = it.transcript + Turn(Speaker.Interviewer, text, seat.voice, seat.name, seat.role, followUp),
                activeSeat = seat,
                phase = Phase.Speaking,
                mainAsked = planIndex,
                onFollowUp = followUp,
                error = null,
            )
        }
        tts.speakAndAwait(text, seat.voice.voice())
        awaitAnswer()
    }

    /**
     * After an answer: a short spoken acknowledgement covers a quick AI decision to probe, challenge
     * or move on. If the AI is slow or busy, an instant local rule picks a realistic probe instead.
     */
    private suspend fun react() {
        val current = plan.getOrNull(planIndex - 1) ?: return askNextMain()
        val seats = _state.value.seats
        val seat = seats.getOrElse(current.seat) { seats.first() }
        if (probesOnCurrent >= MAX_PROBES) return askNextMain()

        val thread = _state.value.transcript.drop(threadStart)
        _state.update { it.copy(phase = Phase.Thinking, activeSeat = seat) }
        val followUp = coroutineScope {
            val ai = async {
                withTimeoutOrNull(REACTION_TIMEOUT_MS) {
                    runCatching {
                        container.interviewAgent.react(
                            config(), container.settings.current().profile, seat.role, current.text,
                            current.bank.lookingFor, thread, probesOnCurrent, current.bank.probes,
                        )
                    }.getOrNull()
                }
            }
            tts.speakAndAwait(ACKS.random(), seat.voice.voice())
            val reaction = ai.await()
            when {
                reaction == null -> localProbe(current, thread)
                reaction.action == "move_on" || reaction.spokenText.isBlank() -> null
                else -> reaction.spokenText
            }
        }
        if (followUp != null) {
            probesOnCurrent++
            speakQuestion(seat, followUp, followUp = true)
        } else {
            askNextMain()
        }
    }

    /** Instant fallback: probes thin answers the way panels do (example, result, ownership). */
    private fun localProbe(current: PlannedQuestion, thread: List<Turn>): String? {
        val answer = thread.lastOrNull { it.speaker == Speaker.Candidate }?.text.orEmpty()
        val alreadyAsked = thread.filter { it.speaker == Speaker.Interviewer }.map { it.text }.toSet()
        val words = answer.split(Regex("\\s+")).count { it.isNotBlank() }
        val lower = " ${answer.lowercase()} "
        val hasResult = Regex("\\d|%|£|result|saved|reduced|finished|completed|delivered|on time|outcome").containsMatchIn(lower)
        val we = Regex("\\bwe\\b").findAll(lower).count()
        val me = Regex("\\bi\\b").findAll(lower).count()
        val options = buildList {
            if (words < 45) {
                addAll(current.bank.probes)
                add(QuestionBank.genericProbes.getValue("example"))
            }
            if (we > me * 2 && we >= 3) add(QuestionBank.genericProbes.getValue("ownership"))
            if (!hasResult && words >= 25) add(QuestionBank.genericProbes.getValue("result"))
            if (probesOnCurrent == 0 && current.bank.probes.isNotEmpty() && words in 45..140) add(current.bank.probes.first())
        }
        return options.firstOrNull { it !in alreadyAsked }
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
        runLoop { react() }
    }

    /** Retry after an error: re-open the floor, or carry on to the next question. */
    fun retry() {
        if (_state.value.evaluationFailed) {
            runLoop { evaluate() }
            return
        }
        val last = _state.value.transcript.lastOrNull()
        if (last == null || last.speaker == Speaker.Candidate) runLoop { askNextMain() } else awaitAnswer()
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
            val job = targetJob.value?.takeIf { s.tailorToJob }
            practice.markAsked(plan.take(planIndex).map { it.key })
            practice.saveInterview(
                job?.id ?: GENERAL,
                InterviewResult(evaluation.overallScore, evaluation.offerLikelihood, System.currentTimeMillis()),
            )
            container.sessions.save(
                SessionRecord(
                    id = UUID.randomUUID().toString(),
                    kind = SessionKind.Interview,
                    title = job?.title ?: (s.mode.title + if (s.mode == InterviewMode.CrisisSimulation) " · ${s.scenario.title}" else ""),
                    subtitle = listOf(evaluation.readinessBand, "offer ${evaluation.offerLikelihood}%").joinToString(" · "),
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

    companion object {
        const val GENERAL = "general"
        private const val MAX_PROBES = 2
        private const val REACTION_TIMEOUT_MS = 4_000L
        private val ACKS = listOf("Okay.", "Right.", "Okay, thanks.", "Mm, right.", "Okay. Let me just note that.", "Right, okay.")
    }
}
