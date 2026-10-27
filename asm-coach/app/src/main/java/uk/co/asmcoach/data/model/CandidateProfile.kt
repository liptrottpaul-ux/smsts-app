package uk.co.asmcoach.data.model

/**
 * The candidate's master profile. Every agent prompt is grounded in this, so the
 * AI never invents experience the candidate doesn't have.
 */
data class CandidateProfile(
    val name: String,
    val age: Int = 44,
    val targetRole: String = "Assistant Site Manager",
    val targetRegion: String = "North West England",
    val qualifications: List<String> = listOf(
        "HND in Construction",
        "SMSTS (CITB Site Management Safety Training Scheme), recently certified",
    ),
    val tradeExperience: List<String> = listOf(
        "Roofing, including flat roofing and single-ply membranes",
        "Waterproofing",
        "Drylining, including partition systems and fire-rated linings",
    ),
    val tradeYears: Int = 10,
    val commercialExperience: List<String> = listOf(
        "Deal execution across complex M&A transactions to fixed completion deadlines",
        "Due diligence, risk registers and condition-precedent tracking",
        "Commercial negotiation with multiple counterparties and advisers",
        "Stakeholder and board-level reporting",
        "Contract terms, warranties, indemnities and cost/value awareness",
    ),
    val commercialYears: Int = 8,
    val extraNotes: String = "",
) {
    fun toPromptBlock(): String = buildString {
        appendLine("CANDIDATE MASTER PROFILE")
        appendLine("Name: $name (age $age)")
        appendLine("Target: $targetRole roles in $targetRegion")
        appendLine("Qualifications: ${qualifications.joinToString("; ")}")
        appendLine("Early career: $tradeYears years hands-on in trades: ${tradeExperience.joinToString("; ")}")
        appendLine(
            "Then $commercialYears years in M&A commercial deal execution: " +
                commercialExperience.joinToString("; "),
        )
        appendLine("Now returning to UK construction, aiming for site management.")
        if (extraNotes.isNotBlank()) appendLine("Extra notes from the candidate: $extraNotes")
        append(
            "Rules: never invent employers, projects or certificates the profile does not state. " +
                "Where evidence is missing, coach the candidate on how to frame or close the gap.",
        )
    }
}
