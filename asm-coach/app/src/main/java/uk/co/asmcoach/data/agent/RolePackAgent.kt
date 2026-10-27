package uk.co.asmcoach.data.agent

import uk.co.asmcoach.data.model.CandidateProfile
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.QuestionCategory
import uk.co.asmcoach.data.model.RolePack
import uk.co.asmcoach.data.remote.GeminiClient

/**
 * Builds the role-specific prep and interview material for one vacancy in a single call, so the
 * live interview can run from a pre-written question bank with no waiting between questions.
 */
class RolePackAgent(private val gemini: GeminiClient) {

    suspend fun build(job: JobListing, description: String, profile: CandidateProfile): RolePack {
        val prompt = buildString {
            appendLine(profile.toPromptBlock())
            appendLine()
            appendLine("THE VACANCY")
            appendLine("Title: ${job.title}")
            job.company?.let { appendLine("Employer: $it") }
            job.location?.let { appendLine("Location: $it") }
            job.salary?.let { appendLine("Salary: $it") }
            appendLine("Description:")
            appendLine(description.ifBlank { job.snippet })
            appendLine()
            appendLine(SCHEMA)
        }
        val pack = gemini.generateStructured(RolePack.serializer(), SYSTEM, prompt, temperature = 0.6f, fast = true)
        require(pack.questions.isNotEmpty()) { "The role pack came back without questions. Please try again." }
        val seats = pack.panel.size.coerceIn(1, 3)
        return pack.copy(
            panel = pack.panel.take(3),
            questions = pack.questions.map { it.copy(askedBy = it.askedBy.coerceIn(0, seats - 1)) },
            scenarios = pack.scenarios.map { it.copy(askedBy = it.askedBy.coerceIn(0, seats - 1)) },
        )
    }

    private companion object {
        val SYSTEM = """
            You are a UK construction hiring panel chair and interview coach. You prepare one candidate
            for ONE specific Assistant Site Manager vacancy. Everything you write must be specific to
            this employer type, project type, location and the requirements in the listing, not
            generic. Reference real UK practice where relevant: CDM 2015, SMSTS, CSCS, NHBC, RIDDOR,
            temporary works (BS 5975), permits, NEC/JCT, the Building Safety Act, digital site tools.
            Model answers must use only the candidate's real background (10 years roofing,
            waterproofing and drylining; HND Construction; SMSTS; 8 years M&A deal execution),
            framed as strongly as honesty allows, in natural spoken British English, first person.
            Panel member names are fictional. Respond with JSON only.
        """.trimIndent()

        val SCHEMA = """
            Return one JSON object:
            {
              "roleSummary": "2 sentences: what this employer needs from this ASM and what the project looks like",
              "panel": [
                {"name": "fictional full name", "role": "job title typical for this employer, e.g. Construction Manager / Build Manager / Commercial Manager / H&S Advisor", "focus": "what they probe",
                 "voice": "male" | "female" (must match the name; a realistic mix)}
              ] (exactly 3; index 0 is the lead),
              "prepTopics": [
                {"title": "topic taken from THIS listing's requirements", "whyItMatters": "1 sentence tied to the listing",
                 "points": ["4-5 specific things to know or mention"], "sayThis": "70-100 word spoken talking point in first person"}
              ] (5 topics),
              "questions": [
                {"category": one of ${QuestionCategory.PACK.joinToString { "\"$it\"" }},
                 "askedBy": panel index 0-2 (match the member's focus),
                 "question": "the exact question as spoken aloud, max 40 words, specific to this role and listing",
                 "lookingFor": "1 sentence: what a strong answer must contain",
                 "modelAnswer": "90-130 word STAR answer in first person",
                 "probes": ["2 follow-ups this interviewer would use if the answer is thin"]}
              ] (16 questions: 2 per category; include the hard ones a real panel would ask this candidate,
                 e.g. the 8 years away from site, managing experienced trades, site software they haven't used),
              "scenarios": [
                {"title": "short name", "briefing": "2-sentence live incident plausible on THIS type of project",
                 "askedBy": panel index, "questions": ["3 escalating follow-up questions, each adding a new complication"]}
              ] (3 scenarios)
            }
        """.trimIndent()
    }
}
