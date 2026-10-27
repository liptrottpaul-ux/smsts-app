package uk.co.asmcoach.data.agent

import uk.co.asmcoach.data.model.CandidateProfile
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.StrategyBrief
import uk.co.asmcoach.data.remote.GeminiClient

/** Agent 1: scores fit against the master profile and writes the brief and 30-60-90 plan. */
class StrategyAgent(private val gemini: GeminiClient) {

    suspend fun analyse(job: JobListing, description: String, profile: CandidateProfile): StrategyBrief {
        val prompt = buildString {
            appendLine(profile.toPromptBlock())
            appendLine()
            appendLine("JOB LISTING")
            appendLine("Title: ${job.title}")
            job.company?.let { appendLine("Company: $it") }
            job.location?.let { appendLine("Location: $it") }
            job.salary?.let { appendLine("Salary: $it") }
            appendLine("Source: ${job.source} (${job.url})")
            appendLine("Description:")
            appendLine(description.ifBlank { job.snippet })
            appendLine()
            appendLine(SCHEMA)
        }
        val brief = gemini.generateStructured(StrategyBrief.serializer(), SYSTEM, prompt, temperature = 0.5f)
        return brief.copy(compatibilityScore = brief.compatibilityScore.coerceIn(0, 100))
    }

    private companion object {
        val SYSTEM = """
            You are a senior UK construction recruitment strategist and former Project Director.
            You assess candidates for Assistant Site Manager roles with total honesty and then build
            a winning strategy. You know CDM 2015, SMSTS, CSCS cards, NHBC, JCT and NEC contracts,
            the Building Safety Act 2022, and how UK main contractors and housebuilders hire.
            The candidate's 8 years in M&A deal execution is a genuine differentiator: map it to
            programme control, risk management, commercial awareness, negotiation with
            sub-contractors and stakeholder reporting. Treat the time away from site as a
            "commercial decade", never as a gap to apologise for. Use British English.
            Respond with JSON only.
        """.trimIndent()

        val SCHEMA = """
            Return a single JSON object with exactly these fields:
            {
              "compatibilityScore": integer 0-100 (be realistic, not flattering),
              "fitLabel": "Strong fit" | "Good fit" | "Stretch" | "Long shot",
              "matchSummary": "2 sentences on overall fit",
              "company": "employer name or 'Not stated'",
              "location": "site/town or 'North West'",
              "projectType": "e.g. residential new build, fit-out, refurbishment",
              "strengths": [{"requirement": "what the listing asks for", "evidence": "specific proof from the profile"}] (4-6 items),
              "gaps": [{"gap": "missing or weak area", "mitigation": "concrete action or framing"}] (2-4 items),
              "whyYouWin": "A confident 120-160 word 'Why You Win This Role' brief written to the candidate in second person",
              "elevatorPitch": "A 60-second spoken pitch in first person tailored to this role",
              "plan": {
                "days30": {"theme": "...", "actions": ["4-5 specific actions"], "successMeasure": "..."},
                "days60": {"theme": "...", "actions": ["4-5 specific actions"], "successMeasure": "..."},
                "days90": {"theme": "...", "actions": ["4-5 specific actions"], "successMeasure": "..."}
              },
              "questionsToAsk": ["4 sharp questions the candidate should ask the panel"],
              "keywords": ["6-10 keywords from the listing to echo in interview"]
            }
            The 30-60-90 Day Site Management Plan must be specific to the project type: induction and
            CPP familiarisation, RAMS and permit systems, sub-contractor coordination, look-ahead
            programmes, QA and inspection test plans, digital records, and commercial early warnings.
        """.trimIndent()
    }
}
