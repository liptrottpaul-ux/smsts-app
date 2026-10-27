package uk.co.asmcoach.data.model

import kotlinx.serialization.Serializable

/** A fictional interviewer. Pitch and rate give each one a distinct TTS voice. */
enum class Persona(
    val displayName: String,
    val role: String,
    val brief: String,
    val pitch: Float,
    val rate: Float,
) {
    ProjectDirector(
        displayName = "Graham Holt",
        role = "Senior Project Director",
        brief = "Tier 1 contractor director. Cares about programme, leadership, client relationships " +
            "and whether this candidate can run a section of site on their own. Direct, warm but probing.",
        pitch = 0.88f,
        rate = 0.95f,
    ),
    CommercialManager(
        displayName = "Priya Shah",
        role = "Commercial Manager",
        brief = "Focuses on cost control, variations, valuations, subcontractor packages, records for " +
            "claims and early warnings under NEC/JCT. Sharp, numbers-driven; tests the M&A angle hard.",
        pitch = 1.05f,
        rate = 1.0f,
    ),
    SafetyAuditor(
        displayName = "Dave Kerr",
        role = "H&S Auditor",
        brief = "Ex-HSE-minded auditor. Tests CDM 2015, RAMS, permits, temporary works, RIDDOR, " +
            "behavioural safety and SMSTS knowledge. Calm, forensic, asks 'what exactly would you do next?'.",
        pitch = 0.97f,
        rate = 0.92f,
    );

    val title: String get() = "$displayName · $role"

    companion object {
        fun parse(raw: String?): Persona? = entries.firstOrNull {
            it.name.equals(raw, ignoreCase = true) ||
                it.role.equals(raw, ignoreCase = true) ||
                it.displayName.equals(raw, ignoreCase = true)
        }
    }
}

enum class InterviewMode(
    val title: String,
    val tagline: String,
    val focus: String,
    val personas: List<Persona>,
) {
    StandardPanel(
        title = "Standard Panel",
        tagline = "Three-person ASM panel",
        focus = "A realistic Assistant Site Manager panel interview: motivation, the career narrative " +
            "(trades → M&A → site), leadership, H&S, programme, quality and commercial awareness.",
        personas = listOf(Persona.ProjectDirector, Persona.CommercialManager, Persona.SafetyAuditor),
    ),
    CrisisSimulation(
        title = "Crisis Simulation",
        tagline = "Live incident on your site",
        focus = "A live, unfolding site crisis. The candidate is the ASM in charge. Escalate the scenario " +
            "with new facts each turn and test decisions, sequencing, communication and legal duties.",
        personas = listOf(Persona.SafetyAuditor, Persona.ProjectDirector, Persona.CommercialManager),
    ),
    DigitalSiteTech(
        title = "Digital Site Tech",
        tagline = "Procore · Field View · BIM 360",
        focus = "Digital construction: Procore, Viewpoint Field View, Autodesk BIM 360 / Construction " +
            "Cloud, digital QA, snagging, RFIs, daily diaries, photo evidence and the golden thread.",
        personas = listOf(Persona.ProjectDirector, Persona.CommercialManager),
    ),
    TechnicalTradeQa(
        title = "Technical Trade QA",
        tagline = "Roofing · waterproofing · drylining",
        focus = "Technical quality assurance on packages the candidate knows: single-ply flat roofing, " +
            "EFVM/leak testing, waterproofing details, drylining and fire-stopping inspection before close-up.",
        personas = listOf(Persona.SafetyAuditor, Persona.ProjectDirector),
    ),
}

enum class CrisisScenario(val title: String, val briefing: String) {
    HseVisit(
        title = "Unannounced HSE visit",
        briefing = "07:40 on a Tuesday. An HSE inspector arrives unannounced at the gate of a 5-storey " +
            "residential block. Scaffold is being adapted, a MEWP is working at the rear and the Site " +
            "Manager is off sick. The candidate is the most senior person on site.",
    ),
    SlumpFailure(
        title = "Concrete slump failure",
        briefing = "A ground-floor slab pour is underway with a pump booked until 2pm. The third " +
            "truck's slump test comes back well outside the specified consistence class. The driver " +
            "offers to 'add a bit of water' and the gang are pushing to keep going.",
    ),
    SubcontractorDispute(
        title = "Sub-contractor dispute",
        briefing = "The drylining sub-contractor's foreman says his team are walking off at lunchtime " +
            "over an unpaid valuation and a disputed variation. Fire-stopping on two floors must be " +
            "inspected before ceilings close next week and the M&E sub is waiting on them.",
    ),
}

enum class Speaker { Interviewer, Candidate }

@Serializable
data class Turn(
    val speaker: Speaker,
    val text: String,
    val persona: Persona? = null,
    /** Display name and job title when the panel comes from a role pack. */
    val name: String? = null,
    val role: String? = null,
) {
    val speakerName: String? get() = name ?: persona?.displayName
    val speakerRole: String? get() = role ?: persona?.role
}

/** The interviewer agent's structured reply for each turn. */
@Serializable
data class InterviewerTurn(
    val persona: String = "",
    val spokenText: String = "",
)

@Serializable
data class Evaluation(
    val overallScore: Int = 0,
    val readinessBand: String = "",
    val headline: String = "",
    val competencies: List<CompetencyScore> = emptyList(),
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val starCritique: List<StarCritique> = emptyList(),
    val betterAnswers: List<BetterAnswer> = emptyList(),
)

@Serializable
data class CompetencyScore(val name: String = "", val score: Int = 0)

@Serializable
data class StarCritique(
    val question: String = "",
    val answerSummary: String = "",
    val situation: Int = 0,
    val task: Int = 0,
    val action: Int = 0,
    val result: Int = 0,
    val critique: String = "",
)

@Serializable
data class BetterAnswer(
    val question: String = "",
    val whyItWins: String = "",
    val script: String = "",
)
