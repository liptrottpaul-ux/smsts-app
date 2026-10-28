package uk.co.asmcoach.data.agent

import kotlinx.serialization.Serializable
import uk.co.asmcoach.data.model.BankQuestion
import uk.co.asmcoach.data.model.CandidateProfile
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.PanelMember
import uk.co.asmcoach.data.model.PrepTopic
import uk.co.asmcoach.data.model.RoleScenario
import uk.co.asmcoach.data.remote.GeminiClient

/**
 * Builds the role-specific prep and interview material for one vacancy in small parts, so the
 * first content appears in seconds and the rest fills in while the user starts prepping:
 * [buildCore] (summary, panel, topics), [buildQuestions] per category batch, and [buildScenarios].
 */
class RolePackAgent(private val gemini: GeminiClient) {

    @Serializable
    data class Core(
        val roleSummary: String = "",
        val panel: List<PanelMember> = emptyList(),
        val prepTopics: List<PrepTopic> = emptyList(),
    )

    @Serializable
    private data class Questions(val questions: List<BankQuestion> = emptyList())

    @Serializable
    private data class Scenarios(val scenarios: List<RoleScenario> = emptyList())

    suspend fun buildCore(job: JobListing, description: String, profile: CandidateProfile): Core {
        val core = gemini.generateStructured(
            Core.serializer(), SYSTEM, vacancy(job, description, profile) + CORE_SCHEMA, temperature = 0.6f, fast = true,
        )
        require(core.panel.isNotEmpty()) { "The panel didn't come back. Please try again." }
        return core.copy(panel = core.panel.take(3), prepTopics = core.prepTopics.take(6))
    }

    /** Two questions per category in [categories], asked by members of the existing [panel]. */
    suspend fun buildQuestions(
        job: JobListing,
        description: String,
        profile: CandidateProfile,
        panel: List<PanelMember>,
        categories: List<String>,
    ): List<BankQuestion> {
        val prompt = buildString {
            append(vacancy(job, description, profile))
            appendLine("PANEL (use these indexes for askedBy)")
            panel.forEachIndexed { i, m -> appendLine("$i: ${m.name}, ${m.role}. Focus: ${m.focus}") }
            appendLine()
            appendLine(questionsSchema(categories))
        }
        val result = gemini.generateStructured(Questions.serializer(), SYSTEM, prompt, temperature = 0.6f, fast = true)
        val seats = panel.size.coerceIn(1, 3)
        return result.questions.map { it.copy(askedBy = it.askedBy.coerceIn(0, seats - 1)) }
    }

    suspend fun buildScenarios(job: JobListing, description: String, profile: CandidateProfile, panel: List<PanelMember>): List<RoleScenario> {
        val prompt = buildString {
            append(vacancy(job, description, profile))
            appendLine("PANEL (use these indexes for askedBy)")
            panel.forEachIndexed { i, m -> appendLine("$i: ${m.name}, ${m.role}") }
            appendLine()
            appendLine(SCENARIO_SCHEMA)
        }
        val result = gemini.generateStructured(Scenarios.serializer(), SYSTEM, prompt, temperature = 0.7f, fast = true)
        val seats = panel.size.coerceIn(1, 3)
        return result.scenarios.take(3).map { it.copy(askedBy = it.askedBy.coerceIn(0, seats - 1)) }
    }

    private fun vacancy(job: JobListing, description: String, profile: CandidateProfile) = buildString {
        appendLine(profile.toPromptBlock())
        appendLine()
        appendLine("THE VACANCY")
        appendLine("Title: ${job.title}")
        job.company?.let { appendLine("Employer: $it") }
        job.location?.let { appendLine("Location: $it") }
        job.salary?.let { appendLine("Salary: $it") }
        appendLine("Advert:")
        appendLine(description.ifBlank { job.snippet })
        appendLine()
    }

    private companion object {
        val SYSTEM = """
            You are a UK construction hiring panel chair and interview coach. You prepare one candidate
            for ONE specific Assistant Site Manager vacancy. Everything you write must come from THIS
            advert and THIS employer: their named requirements, project type, sector, location,
            systems, certifications and any employer research provided. Generic site-management
            content that could apply to any job is a failure. If the advert names something (e.g.
            NHBC, Procore, live environment, timber frame, S106 affordable homes, a client), use it
            by name. Reference real UK practice where relevant: CDM 2015, SMSTS, CSCS, NHBC, RIDDOR,
            temporary works (BS 5975), permits, NEC/JCT, the Building Safety Act.
            Model answers use only the candidate's real background (10 years roofing, waterproofing
            and drylining; HND Construction; SMSTS; 8 years M&A deal execution), framed as strongly as
            honesty allows, in natural spoken British English, first person.
            Panel member names are fictional. Respond with JSON only. Be concise.
        """.trimIndent()

        val CORE_SCHEMA = """
            Return JSON:
            {
              "roleSummary": "2 sentences: what this employer needs from this ASM and what the project looks like",
              "panel": [{"name": "fictional full name", "role": "job title typical for this employer",
                         "focus": "what they probe", "voice": "male" | "female" (must match the name)}] (exactly 3, index 0 is the lead),
              "prepTopics": [{"title": "topic from THIS advert", "listingRef": "exact advert words it comes from (max 20 words)",
                              "whyItMatters": "1 sentence", "points": ["4 specific things to know or mention"],
                              "sayThis": "60-80 word spoken talking point, first person"}] (5 topics)
            }
        """.trimIndent()

        fun questionsSchema(categories: List<String>) = """
            Write 2 interview questions for EACH of these categories: ${categories.joinToString()}.
            Return JSON:
            {"questions": [{
               "category": one of ${categories.joinToString { "\"$it\"" }},
               "askedBy": panel index,
               "question": "as spoken aloud, max 35 words, naming this employer's projects, sector or requirements",
               "listingRef": "exact advert or employer-research words it targets (max 20 words)",
               "lookingFor": "1 sentence: what a strong answer must contain",
               "modelAnswer": "80-110 word STAR answer, first person",
               "probes": ["2 follow-ups this interviewer would use if the answer is thin"]
            }]}
            Include at least one hard question this panel would ask this particular candidate.
        """.trimIndent()

        val SCENARIO_SCHEMA = """
            Return JSON:
            {"scenarios": [{"title": "short name", "briefing": "2-sentence live incident plausible on THIS project",
                            "askedBy": panel index,
                            "questions": ["3 escalating follow-ups, each adding a new complication"]}]} (3 scenarios)
        """.trimIndent()
    }
}
