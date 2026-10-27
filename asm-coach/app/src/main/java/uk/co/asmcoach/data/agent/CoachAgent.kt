package uk.co.asmcoach.data.agent

import uk.co.asmcoach.data.model.CandidateProfile
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.Lesson
import uk.co.asmcoach.data.model.Masterclass
import uk.co.asmcoach.data.remote.GeminiClient

/** Agent 2: turns a masterclass lesson into a personalised, speakable talking point. */
class CoachAgent(private val gemini: GeminiClient) {

    suspend fun personalise(
        module: Masterclass,
        lesson: Lesson,
        profile: CandidateProfile,
        targetJob: JobListing?,
    ): String {
        val prompt = buildString {
            appendLine(profile.toPromptBlock())
            targetJob?.let { appendLine("Target role: ${it.title}${it.location?.let { l -> " in $l" } ?: ""}") }
            appendLine()
            appendLine("MASTERCLASS: ${module.title} – ${lesson.title}")
            lesson.points.forEach { appendLine("- $it") }
            appendLine()
            appendLine(
                "Write the exact words the candidate could say in an interview to show mastery of this " +
                    "lesson, drawing on their trade and M&A background. 110-150 words, first person, " +
                    "natural spoken British English, no headings, no bullet points, no markdown.",
            )
        }
        return gemini.generateText(SYSTEM, prompt, temperature = 0.75f, fast = true)
            .replace(Regex("[*#_`]"), "")
            .trim()
    }

    private companion object {
        const val SYSTEM = "You are an elite UK construction interview coach preparing a returning candidate " +
            "for Assistant Site Manager interviews. You are precise on regulations and never invent experience."
    }
}
