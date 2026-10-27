package uk.co.asmcoach.data.model

import kotlinx.serialization.Serializable

@Serializable
data class JobListing(
    val id: String,
    val title: String,
    val url: String,
    val snippet: String,
    val source: String,
    val company: String? = null,
    val location: String? = null,
    val salary: String? = null,
    val postedLabel: String? = null,
    val isSample: Boolean = false,
)

enum class SearchRegion(val label: String, val query: String) {
    NorthWest("North West", "(\"North West\" OR Manchester OR Liverpool OR Preston OR Warrington OR Chester)"),
    Manchester("Greater Manchester", "(Manchester OR Salford OR Stockport OR Bolton OR Wigan OR Oldham)"),
    Merseyside("Merseyside", "(Liverpool OR Wirral OR \"St Helens\" OR Knowsley OR Southport)"),
    Lancashire("Lancashire", "(Preston OR Blackburn OR Blackpool OR Lancaster OR Burnley OR Chorley)"),
    Cheshire("Cheshire", "(Chester OR Warrington OR Crewe OR Macclesfield OR Runcorn OR Widnes)"),
}

@Serializable
data class StrategyBrief(
    val compatibilityScore: Int = 0,
    val fitLabel: String = "",
    val matchSummary: String = "",
    val company: String = "",
    val location: String = "",
    val projectType: String = "",
    val strengths: List<RequirementMatch> = emptyList(),
    val gaps: List<GapMitigation> = emptyList(),
    val whyYouWin: String = "",
    val elevatorPitch: String = "",
    val plan: SiteManagementPlan = SiteManagementPlan(),
    val questionsToAsk: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
)

@Serializable
data class RequirementMatch(val requirement: String = "", val evidence: String = "")

@Serializable
data class GapMitigation(val gap: String = "", val mitigation: String = "")

@Serializable
data class SiteManagementPlan(
    val days30: PlanPhase = PlanPhase(),
    val days60: PlanPhase = PlanPhase(),
    val days90: PlanPhase = PlanPhase(),
)

@Serializable
data class PlanPhase(
    val theme: String = "",
    val actions: List<String> = emptyList(),
    val successMeasure: String = "",
)
