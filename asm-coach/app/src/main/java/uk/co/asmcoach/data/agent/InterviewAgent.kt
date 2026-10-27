package uk.co.asmcoach.data.agent

import uk.co.asmcoach.data.model.BankQuestion
import uk.co.asmcoach.data.model.CandidateProfile
import uk.co.asmcoach.data.model.PanelMember
import uk.co.asmcoach.data.model.RoleScenario
import uk.co.asmcoach.data.model.CrisisScenario
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

    suspend fun evaluate(
        config: InterviewConfig,
        profile: CandidateProfile,
        transcript: List<Turn>,
    ): Evaluation {
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
            appendLine(EVALUATION_SCHEMA)
        }
        val evaluation = gemini.generateStructured(
            Evaluation.serializer(), SYSTEM_EVALUATOR, prompt, temperature = 0.4f,
        )
        return evaluation.copy(
            overallScore = evaluation.overallScore.coerceIn(0, 100),
            competencies = evaluation.competencies.map { it.copy(score = it.score.coerceIn(0, 100)) },
            starCritique = evaluation.starCritique.map {
                it.copy(
                    situation = it.situation.coerceIn(0, 5),
                    task = it.task.coerceIn(0, 5),
                    action = it.action.coerceIn(0, 5),
                    result = it.result.coerceIn(0, 5),
                )
            },
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
            Speaker.Interviewer -> "${turn.speakerRole ?: "Interviewer"}: ${turn.text}"
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

        val EVALUATION_SCHEMA = """
            Return a single JSON object:
            {
              "overallScore": integer 0-100 readiness score,
              "readinessBand": "Interview-ready" | "Nearly there" | "Needs practice" | "Not yet",
              "headline": "one sentence verdict",
              "competencies": [{"name": "e.g. H&S leadership", "score": 0-100}] (4-6 items relevant to the mode),
              "strengths": ["3 specific strengths with quotes or evidence"],
              "improvements": ["3 specific, actionable improvements"],
              "starCritique": [{
                 "question": "the question asked",
                 "answerSummary": "one-sentence summary of what the candidate said",
                 "situation": 0-5, "task": 0-5, "action": 0-5, "result": 0-5,
                 "critique": "2 sentences: what was missing and why it matters"
              }] (one per candidate answer),
              "betterAnswers": [{
                 "question": "the question",
                 "whyItWins": "one sentence",
                 "script": "the exact 90-140 word answer they should have given, first person"
              }] (for the 3 weakest answers)
            }
        """.trimIndent()
    }
}
