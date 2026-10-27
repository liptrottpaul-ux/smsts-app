package uk.co.asmcoach.data.model

import kotlinx.serialization.Serializable

/**
 * Everything needed to prepare for and rehearse one specific vacancy, generated once per role
 * and stored on the device so Prep and Roleplay open instantly.
 */
@Serializable
data class RolePack(
    val roleSummary: String = "",
    val panel: List<PanelMember> = emptyList(),
    val prepTopics: List<PrepTopic> = emptyList(),
    val questions: List<BankQuestion> = emptyList(),
    val scenarios: List<RoleScenario> = emptyList(),
)

/** A plausible interviewer for this employer (fictional name, real-world job title). */
@Serializable
data class PanelMember(val name: String = "", val role: String = "", val focus: String = "")

@Serializable
data class PrepTopic(
    val title: String = "",
    val whyItMatters: String = "",
    val points: List<String> = emptyList(),
    val sayThis: String = "",
)

@Serializable
data class BankQuestion(
    val category: String = "",
    val askedBy: Int = 0,
    val question: String = "",
    val lookingFor: String = "",
    val modelAnswer: String = "",
)

@Serializable
data class RoleScenario(
    val title: String = "",
    val briefing: String = "",
    val askedBy: Int = 0,
    val questions: List<String> = emptyList(),
)

object QuestionCategory {
    const val MOTIVATION = "Motivation"
    const val TECHNICAL = "Technical"
    const val SAFETY = "Safety"
    const val COMMERCIAL = "Commercial"
    const val DIGITAL = "Digital"
    val ALL = listOf(MOTIVATION, TECHNICAL, SAFETY, COMMERCIAL, DIGITAL)
}
