package uk.co.asmcoach.data.content

data class ChecklistItem(val id: String, val title: String, val detail: String)

/**
 * Pre-interview preparation steps drawn from UK construction interview guidance: research the
 * employer and its values, prepare STAR examples with numbers, have certificates ready, know the
 * regs, prepare questions, and plan the day.
 */
object PrepChecklist {
    val items = listOf(
        ChecklistItem("research", "Research the employer",
            "Recent North West sites, sectors, values and how they're rated (for housebuilders: HBF customer star rating, NHBC Pride in the Job awards)."),
        ChecklistItem("site", "Look at one of their live sites",
            "Drive past or study photos: hoarding, access, phase of build. Note two things you'd check as their ASM."),
        ChecklistItem("stories", "Prepare 6–8 STAR stories",
            "Safety stop, conflict between trades, delay recovered, cost saved, quality defect caught, mistake owned, deadline met under pressure."),
        ChecklistItem("numbers", "Put numbers in every story",
            "Dates, values, team sizes, days recovered, pounds saved. Panels mark vague answers down."),
        ChecklistItem("pitch", "Rehearse your 90-second story out loud",
            "Trades → M&A → why site management now → what they get on day one. No apology for the years away."),
        ChecklistItem("regs", "Refresh the must-know rules",
            "CDM 2015 duties, scaffold inspections (before use, 7-day, after weather/alteration), excavation checks each shift, temporary works (BS 5975), permits, RIDDOR, LOLER."),
        ChecklistItem("docs", "Certificates ready",
            "SMSTS, CSCS card, First Aid, HND, driving licence. Bring copies; many adverts list them as essential."),
        ChecklistItem("questions", "Prepare 3–4 questions for the panel",
            "About the project, the team you'd join, how they develop ASMs into Site Managers, what success looks like at 6 months."),
        ChecklistItem("salary", "Know your salary range",
            "2026 North West ASM adverts mostly sit around £35–50k, often plus a car allowance. Have a range and a reason."),
        ChecklistItem("day", "Plan the day",
            "Route and parking, arrive 15 minutes early, smart but practical clothes, PPE in the car in case of a site walk."),
    )
}
