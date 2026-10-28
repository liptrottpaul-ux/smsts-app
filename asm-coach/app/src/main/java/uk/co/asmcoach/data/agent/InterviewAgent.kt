package uk.co.asmcoach.data.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import uk.co.asmcoach.data.model.AnswerReview
import uk.co.asmcoach.data.model.BankQuestion
import uk.co.asmcoach.data.model.CandidateProfile
import uk.co.asmcoach.data.model.PanelMember
import uk.co.asmcoach.data.model.Reaction
import uk.co.asmcoach.data.model.RoleScenario
import uk.co.asmcoach.data.model.CrisisScenario
import uk.co.asmcoach.data.model.DrillScore
import uk.co.asmcoach.data.model.Evaluation
import uk.co.asmcoach.data.model.InterviewMode
import uk.co.asmcoach.data.model.InterviewerTurn
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.Persona
import uk.co.asmcoach.data.model.Speaker
import uk.co.asmcoach.data.model.StrategyBrief
import uk.co.asmcoach.data.model.Turn
import uk.co.asmcoach.data.remote.GeminiClient

data class InterviewConfig(
    val mode: InterviewMode,
    val scenario: CrisisScenario?,
    val questionCount: Int,
    val job: JobListing?,
    val brief: StrategyBrief?,
    /** Role-pack panel, scenario and the bank questions that were asked, when interviewing for a real role. */
    val panel: List<PanelMember> = emptyList(),
    val roleScenario: RoleScenario? = null,
    val askedQuestions: List<BankQuestion> = emptyList(),
)

/** Agent 3: plays the interview panel turn by turn, then scores the whole interview. */
class InterviewAgent(private val gemini: GeminiClient) {

    suspend fun nextTurn(
        config: InterviewConfig,
        profile: CandidateProfile,
        transcript: List<Turn>,
    ): Pair<Persona, String> {
        val answered = transcript.count { it.speaker == Speaker.Candidate }
        val prompt = buildString {
            appendLine(context(config, profile))
            appendLine()
            appendLine("TRANSCRIPT SO FAR")
            if (transcript.isEmpty()) appendLine("(The interview is just starting.)")
            appendLine(transcript.render())
            appendLine()
            appendLine("This will be question ${answered + 1} of ${config.questionCount}.")
            appendLine(
                if (transcript.isEmpty()) {
                    "Open the interview: the lead persona gives a one-sentence welcome" +
                        (if (config.scenario != null) ", sets the scene of the live scenario in two sentences," else ",") +
                        " then asks the first question."
                } else {
                    "Choose the most appropriate panel member. Either probe a weak or vague part of the last " +
                        "answer (ask for specifics, numbers, regulations, or the result) or move to a new " +
                        "competency. Do not repeat earlier questions. Do not give feedback or praise."
                },
            )
            appendLine()
            appendLine(
                "Return JSON: {\"persona\": one of ${config.mode.personas.joinToString { "\"${it.name}\"" }}, " +
                    "\"spokenText\": \"what they say aloud, max 70 words, natural spoken British English, no markdown\"}",
            )
        }
        val turn = gemini.generateStructured(
            InterviewerTurn.serializer(), SYSTEM_INTERVIEWER, prompt, temperature = 0.85f, fast = true,
        )
        val persona = Persona.parse(turn.persona)?.takeIf { it in config.mode.personas }
            ?: config.mode.personas.first()
        val text = turn.spokenText.replace(Regex("[*#_`]"), "").trim()
        require(text.isNotEmpty()) { "Empty interviewer turn" }
        return persona to text
    }

    /**
     * Decides, in one quick call, whether the panel probes, challenges or moves on after an answer.
     * Callers must handle failure (they fall back to an instant local probe).
     */
    suspend fun react(
        config: InterviewConfig,
        profile: CandidateProfile,
        speakerRole: String,
        question: String,
        lookingFor: String,
        thread: List<Turn>,
        probesSoFar: Int,
        suggestedProbes: List<String>,
    ): Reaction {
        val prompt = buildString {
            appendLine("Role: ${config.job?.title ?: "Assistant Site Manager"}${config.job?.company?.let { " at $it" } ?: ""}")
            appendLine("Candidate: ${profile.name}, returning to site after 8 years in M&A; 10 years in roofing, waterproofing, drylining; HND; SMSTS.")
            appendLine("You are the $speakerRole.")
            appendLine("Main question: $question")
            if (lookingFor.isNotBlank()) appendLine("A strong answer contains: $lookingFor")
            appendLine("Exchange on this question so far:")
            appendLine(thread.render())
            appendLine("Follow-ups already asked on this question: $probesSoFar (maximum 2).")
            if (suggestedProbes.isNotEmpty()) appendLine("Follow-ups real panels use here: ${suggestedProbes.joinToString(" | ")}")
            appendLine()
            appendLine(
                "Decide like a real UK construction interviewer. Probe if the last answer was vague, hypothetical, " +
                    "said 'we' without personal actions, had no result or numbers, or skipped the key point. Challenge " +
                    "(politely but firmly) if it was wrong on safety, law or technique. Move on if it was complete and " +
                    "specific, or if 2 follow-ups have been asked.",
            )
            appendLine(
                "Return JSON: {\"action\": \"probe\" | \"challenge\" | \"move_on\", " +
                    "\"ack\": \"a short, natural, neutral reaction, max 10 words, no praise beyond 'okay' or 'right'\", " +
                    "\"spokenText\": \"the follow-up question, max 30 words, specific to what they just said; empty if move_on\"}",
            )
        }
        val r = gemini.generateStructured(Reaction.serializer(), SYSTEM_INTERVIEWER, prompt, temperature = 0.7f, quick = true)
        return r.copy(
            ack = r.ack.replace(Regex("[*#_`]"), "").trim(),
            spokenText = r.spokenText.replace(Regex("[*#_`]"), "").trim(),
        )
    }

    /** Scores one practised answer from the Prep tab against what the panel looks for. */
    suspend fun scoreDrill(
        profile: CandidateProfile,
        jobTitle: String?,
        question: String,
        lookingFor: String,
        answer: String,
    ): DrillScore {
        val prompt = buildString {
            appendLine(profile.toPromptBlock())
            appendLine()
            appendLine("Role: ${jobTitle ?: "Assistant Site Manager"}")
            appendLine("Interview question: $question")
            if (lookingFor.isNotBlank()) appendLine("A strong answer contains: $lookingFor")
            appendLine("Candidate's spoken answer: $answer")
            appendLine()
            appendLine(
                "Score it as a UK construction panel would (1 = poor, 3 = acceptable, 5 = outstanding: specific, " +
                    "personal actions, quantified result, correct regulations). Return JSON: {\"score\": 1-5, " +
                    "\"verdict\": \"one line\", \"feedback\": \"2-3 sentences: exactly what was missing or wrong\", " +
                    "\"betterAnswer\": \"a 90-130 word spoken answer in first person using only the candidate's real background\"}",
            )
        }
        val r = gemini.generateStructured(DrillScore.serializer(), SYSTEM_EVALUATOR, prompt, temperature = 0.4f, fast = true)
        return r.copy(score = r.score.coerceIn(1, 5))
    }

    /**
     * Scores the interview in small calls instead of one huge one: a summary (score, offer
     * likelihood, competencies) plus one review per question thread, at most three at a time.
     * A failed thread review drops only that card; the summary is required.
     */
    suspend fun evaluate(
        config: InterviewConfig,
        profile: CandidateProfile,
        transcript: List<Turn>,
    ): Evaluation = coroutineScope {
        val threads = threadsOf(transcript)
        val limiter = Semaphore(3)
        val summary = async { limiter.withPermit { summarise(config, profile, transcript) } }
        val reviews = threads.mapIndexed { i, thread ->
            async { limiter.withPermit { runCatching { reviewThread(config, profile, thread, i, threads.size) }.getOrNull() } }
        }
        val evaluation = summary.await()
        val answerReviews = reviews.awaitAll().filterNotNull()
        evaluation.copy(
            overallScore = evaluation.overallScore.coerceIn(0, 100),
            competencies = evaluation.competencies.map { it.copy(score = it.score.coerceIn(0, 100)) },
            offerLikelihood = evaluation.offerLikelihood.coerceIn(0, 100),
            answerReviews = answerReviews,
        )
    }

    private suspend fun summarise(config: InterviewConfig, profile: CandidateProfile, transcript: List<Turn>): Evaluation {
        val prompt = buildString {
            appendLine(context(config, profile))
            appendLine()
            appendLine("FULL TRANSCRIPT")
            appendLine(transcript.render())
            if (config.askedQuestions.isNotEmpty()) {
                appendLine()
                appendLine("WHAT THE PANEL WAS LOOKING FOR")
                config.askedQuestions.forEach { appendLine("- ${it.question} → ${it.lookingFor}") }
            }
            appendLine()
            appendLine(SUMMARY_SCHEMA)
        }
        return gemini.generateStructured(Evaluation.serializer(), SYSTEM_EVALUATOR, prompt, temperature = 0.3f, fast = true)
    }

    /** One main question with its follow-ups and the candidate's answers. */
    private data class Thread(val question: String, val turns: List<Turn>, val lookingFor: String)

    private fun threadsOf(transcript: List<Turn>): List<Thread> {
        val out = mutableListOf<MutableList<Turn>>()
        transcript.forEach { turn ->
            if (turn.speaker == Speaker.Interviewer && !turn.followUp) out += mutableListOf(turn) else out.lastOrNull()?.add(turn)
        }
        return out.filter { t -> t.any { it.speaker == Speaker.Candidate } }
            .map { t -> Thread(t.first().text, t, "") }
    }

    private suspend fun reviewThread(
        config: InterviewConfig,
        profile: CandidateProfile,
        thread: Thread,
        index: Int,
        count: Int,
    ): AnswerReview {
        val lookingFor = config.askedQuestions.firstOrNull { thread.question.contains(it.question.take(40)) }?.lookingFor.orEmpty()
        val prompt = buildString {
            appendLine(profile.toPromptBlock())
            config.job?.let { appendLine("Role: ${it.title}${it.company?.let { c -> " at $c" } ?: ""}") }
            appendLine()
            appendLine("QUESTION ${index + 1} OF $count, WITH FOLLOW-UPS")
            appendLine(thread.turns.render())
            if (lookingFor.isNotBlank()) appendLine("A strong answer contains: $lookingFor")
            appendLine()
            appendLine(REVIEW_SCHEMA)
        }
        val r = gemini.generateStructured(AnswerReview.serializer(), SYSTEM_EVALUATOR, prompt, temperature = 0.3f, fast = true)
        // Show the candidate's own words verbatim rather than a model summary.
        val said = thread.turns.filter { it.speaker == Speaker.Candidate }.joinToString(" … ") { it.text }
        return r.copy(
            question = thread.question.replace(Regex("^Thanks for coming in[^.]*\\. I'm [^.]*\\.\\s*"), ""),
            yourAnswer = said,
            score = r.score.coerceIn(1, 5),
            situation = r.situation.coerceIn(0, 5),
            task = r.task.coerceIn(0, 5),
            action = r.action.coerceIn(0, 5),
            result = r.result.coerceIn(0, 5),
        )
    }

    private fun context(config: InterviewConfig, profile: CandidateProfile) = buildString {
        appendLine(profile.toPromptBlock())
        appendLine()
        appendLine("INTERVIEW MODE: ${config.mode.title}. ${config.mode.focus}")
        config.roleScenario?.let { appendLine("LIVE SCENARIO: ${it.title}. ${it.briefing}") }
            ?: config.scenario?.let { appendLine("LIVE SCENARIO: ${it.title}. ${it.briefing}") }
        config.job?.let { job ->
            appendLine("ROLE BEING INTERVIEWED FOR: ${job.title}${job.location?.let { " – $it" } ?: ""}")
            appendLine("Listing summary: ${job.snippet.take(600)}")
        }
        config.brief?.let { brief ->
            if (brief.keywords.isNotEmpty()) appendLine("Listing keywords: ${brief.keywords.joinToString()}")
        }
        appendLine("PANEL")
        if (config.panel.isNotEmpty()) {
            config.panel.forEach { appendLine("- ${it.name}, ${it.role}. Focus: ${it.focus}") }
        } else {
            config.mode.personas.forEach { appendLine("- ${it.name}: ${it.displayName}, ${it.role}. ${it.brief}") }
        }
    }

    private fun List<Turn>.render(): String = joinToString("\n") { turn ->
        when (turn.speaker) {
            Speaker.Interviewer -> "${turn.speakerRole ?: "Interviewer"}${if (turn.followUp) " (follow-up)" else ""}: ${turn.text}"
            Speaker.Candidate -> "Candidate: ${turn.text}"
        }
    }

    private companion object {
        val SYSTEM_INTERVIEWER = """
            You are role-playing a UK construction interview panel for an Assistant Site Manager role.
            Stay in character as the chosen panel member. Be realistic: tier 1 and tier 2 contractors
            ask behavioural (STAR) questions, technical questions and scenario questions. Reference real
            UK frameworks where natural: CDM 2015, RIDDOR, SMSTS, LOLER, PUWER, Work at Height
            Regulations 2005, temporary works (BS 5975), NEC4 early warnings, JCT, Building Safety Act.
            In crisis mode, the scenario evolves: introduce a new complication each turn.
            Respond with JSON only.
        """.trimIndent()

        val SYSTEM_EVALUATOR = """
            You are the panel chair writing a candid post-interview evaluation for an Assistant Site
            Manager candidate. Score like a real UK contractor would: 70+ means likely to be offered.
            Judge every answer with the STAR method (Situation, Task, Action, Result). Then write the
            better answers the candidate should have used, using only experience in their profile
            (trades, HND, SMSTS, M&A deal execution) framed as strongly as honesty allows.
            Spoken scripts must be natural British English the candidate can say aloud.
            Respond with JSON only.
        """.trimIndent()

        val SUMMARY_SCHEMA = """
            Return a single JSON object (do NOT review answers one by one; that is done separately):
            {
              "overallScore": integer 0-100 readiness score,
              "readinessBand": "Interview-ready" | "Nearly there" | "Needs practice" | "Not yet",
              "headline": "one sentence verdict",
              "offerLikelihood": integer 0-100, the realistic chance this performance gets an offer against a typical
                 shortlist for this role (experienced ASMs and internal candidates); be honest, not encouraging,
              "offerVerdict": "Likely offer" | "Borderline" | "Unlikely",
              "likelihoodReasons": ["3-4 reasons driving that likelihood, citing what was said"],
              "toRaiseIt": ["3 specific changes that would raise the likelihood most"],
              "redFlags": ["anything a real panel would mark down hard: unsafe statements, wrong law, blame, vagueness; empty if none"],
              "competencies": [{"name": "e.g. H&S leadership", "score": 0-100}] (5-7 items relevant to the role),
              "strengths": ["3 specific strengths with quotes or evidence"],
              "improvements": ["3 specific, actionable improvements"]
            }
        """.trimIndent()

        val REVIEW_SCHEMA = """
            Review this one question thread as the panel would. Return JSON:
            {
              "score": 1-5 (1 poor, 3 acceptable, 5 outstanding),
              "situation": 0-5, "task": 0-5, "action": 0-5, "result": 0-5,
              "whatWorked": "1 sentence quoting what they said that helped",
              "whatWasMissing": "1-2 sentences: the specific gap and why this panel cares",
              "betterAnswer": "the exact 90-140 word answer they should have given, first person, using only their real background"
            }
        """.trimIndent()
    }
}
