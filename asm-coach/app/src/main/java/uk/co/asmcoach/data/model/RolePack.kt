package uk.co.asmcoach.data.model

import kotlinx.serialization.Serializable

/**
 * Everything needed to prepare for and rehearse one specific vacancy, generated once per role
 * and stored on the device so Prep and Roleplay open instantly.
 */
@Serializable
data class RolePack(
    val roleSummary: String = "",
    /** What the pack was built from, e.g. "full advert (job page) · 1,240 words · 5 employer sources". */
    val basedOn: String = "",
    /** True when only a search snippet was available, so the prep is less specific. */
    val thinSource: Boolean = false,
    val panel: List<PanelMember> = emptyList(),
    val prepTopics: List<PrepTopic> = emptyList(),
    val questions: List<BankQuestion> = emptyList(),
    val scenarios: List<RoleScenario> = emptyList(),
)

/** A plausible interviewer for this employer (fictional name, real-world job title). */
@Serializable
data class PanelMember(
    val name: String = "",
    val role: String = "",
    val focus: String = "",
    /** "male" or "female", so the interviewer gets a matching voice. */
    val voice: String = "",
)

@Serializable
data class PrepTopic(
    val title: String = "",
    /** The exact advert line (or employer fact) this topic comes from. */
    val listingRef: String = "",
    val whyItMatters: String = "",
    val points: List<String> = emptyList(),
    val sayThis: String = "",
)

@Serializable
data class BankQuestion(
    val category: String = "",
    val askedBy: Int = 0,
    val question: String = "",
    /** The exact advert line (or employer fact) this question targets. */
    val listingRef: String = "",
    val lookingFor: String = "",
    val modelAnswer: String = "",
    /** Follow-ups a real interviewer would use to dig into a thin answer. */
    val probes: List<String> = emptyList(),
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
    const val SAFETY = "Safety"
    const val QUALITY = "Quality"
    const val PROGRAMME = "Programme"
    const val PEOPLE = "People"
    const val COMMERCIAL = "Commercial"
    const val TECHNICAL = "Technical"
    const val DIGITAL = "Digital"
    const val CUSTOMER = "Customer"
    const val SCENARIO = "Scenario"

    /** Categories the role pack is asked to cover. */
    val PACK = listOf(MOTIVATION, SAFETY, QUALITY, PROGRAMME, PEOPLE, COMMERCIAL, TECHNICAL, DIGITAL)
    val ALL = PACK + listOf(CUSTOMER, SCENARIO)
}
