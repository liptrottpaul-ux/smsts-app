package uk.co.asmcoach.data.content

import uk.co.asmcoach.data.model.PanelMember
import kotlin.random.Random

/**
 * Builds a realistic interview panel instantly, on the device, from the employer type, so the
 * user never waits for the AI just to see who they'll face. Names are fictional and stable per
 * role (seeded by the job id); each seat's voice gender matches the name.
 */
object PanelFactory {

    private data class Seat(val role: String, val focus: String)

    private val seats = mapOf(
        Sector.Housebuilder to listOf(
            Seat("Construction Manager", "running plots to programme, managing site managers and trades, NHBC build quality"),
            Seat("Commercial Manager", "build costs per plot, valuations, sub-contractor packages and waste"),
            Seat("Health & Safety Advisor", "CDM duties, RAMS, scaffold and excavation inspections, site audits"),
        ),
        Sector.Contractor to listOf(
            Seat("Project Manager", "programme, sequencing, client relationships and leading the site team"),
            Seat("Senior Quantity Surveyor", "variations, early warnings, records for claims and cost control"),
            Seat("SHEQ Manager", "CDM 2015, temporary works, permits, RIDDOR and quality assurance"),
        ),
        Sector.FitOut to listOf(
            Seat("Contracts Manager", "fast-track fit-out programmes, trade coordination and client handover"),
            Seat("Commercial Manager", "package buying, variations, client change control"),
            Seat("Health & Safety Manager", "occupied-building safety, hot works, fire-stopping and permits"),
        ),
        Sector.Refurb to listOf(
            Seat("Project Manager", "phasing in live environments, occupants and stakeholder liaison"),
            Seat("Quantity Surveyor", "unforeseen works, variations and records on refurbishment"),
            Seat("Health & Safety Advisor", "asbestos, safeguarding, segregation and permits in occupied buildings"),
        ),
    )

    private val maleNames = listOf(
        "Graham Holt", "Dave Kerr", "Steve Marsden", "Tom Whitworth", "Gary Fenton", "Mark Ashworth",
        "Paul Rigby", "Andy Clough", "Chris Hargreaves", "Neil Barlow", "Rob Pennington", "Ian Moss",
    )
    private val femaleNames = listOf(
        "Priya Shah", "Sarah Lomax", "Claire Heywood", "Emma Gartside", "Rachel Duckworth", "Laura Sefton",
        "Helen Brennan", "Jo Kershaw", "Nicola Platt", "Amy Royle", "Kate Thornley", "Leanne Hough",
    )

    /** Three panel members; the middle seat is a woman so the panel is mixed, as most real ones are. */
    fun panelFor(jobId: String, sectorText: String): List<PanelMember> {
        val random = Random(jobId.hashCode())
        val roles = seats[QuestionBank.sectorOf(sectorText)] ?: seats.getValue(Sector.Contractor)
        val men = maleNames.shuffled(random)
        val women = femaleNames.shuffled(random)
        return roles.mapIndexed { i, seat ->
            val female = i == 1
            PanelMember(
                name = if (female) women[0] else men[i],
                role = seat.role,
                focus = seat.focus,
                voice = if (female) "female" else "male",
            )
        }
    }
}
