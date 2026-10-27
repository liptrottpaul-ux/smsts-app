package uk.co.asmcoach.data.content

import uk.co.asmcoach.data.model.Lesson
import uk.co.asmcoach.data.model.Masterclass
import uk.co.asmcoach.data.model.ModuleTint

/**
 * Curated, offline masterclass content for minutes 5–15 of the daily routine.
 * Always check current manufacturer data and project specifications on a real job.
 */
object MasterclassContent {

    val modules: List<Masterclass> = listOf(
        Masterclass(
            id = "pitch",
            title = "Narrative Pitch Builder",
            subtitle = "Turn ten years away into your edge",
            minutes = 3,
            tint = ModuleTint.Amber,
            lessons = listOf(
                Lesson(
                    id = "pitch-structure",
                    title = "The 90-second career story",
                    points = listOf(
                        "Use four beats: Roots (trades), Range (M&A), Return (why now), Readiness (HND + SMSTS + plan).",
                        "Open with the tools, not the gap: 'I started on the tools in roofing, waterproofing and drylining.'",
                        "Name one tangible site memory: a detail you got right, a leak you traced, a deadline you hit.",
                        "Close on what the employer gets on day one, not on what you hope to learn.",
                        "Rehearse it out loud until it lands in 75–90 seconds without notes.",
                    ),
                    readAloud = "I spent ten years on the tools in roofing, waterproofing and drylining, so I know " +
                        "what good looks like on a flat roof and a fire-rated partition because I've built them. " +
                        "I then spent eight years in M&A deal execution, running complex timetables, risk registers " +
                        "and negotiations where a missed condition cost real money. I've come back to construction " +
                        "deliberately: I've completed my HND and SMSTS, and I want to bring trade-level quality " +
                        "control and commercial discipline to your site from day one.",
                    keyTerms = listOf("Roots", "Range", "Return", "Readiness"),
                ),
                Lesson(
                    id = "pitch-gap",
                    title = "Reframing the 'gap' as a commercial decade",
                    points = listOf(
                        "Never say 'gap' or 'out of the industry'. Say 'my commercial years'.",
                        "Deal timetable ↔ construction programme: critical path, dependencies, long-lead items.",
                        "Due diligence ↔ pre-start checks: RAMS review, design information, surveys, permits.",
                        "Conditions precedent ↔ hold points: nothing proceeds until the inspection is signed off.",
                        "Disclosure letters and warranties ↔ records: diaries, photos and early warnings protect the contractor.",
                        "If asked 'why come back?': you missed seeing something physical get built, and you now have the management toolkit to run it.",
                    ),
                    readAloud = "In M&A, nothing completes until every condition is satisfied and evidenced. On site " +
                        "that's exactly how I treat hold points and inspections. A deal timetable is a programme, " +
                        "due diligence is my pre-start check on RAMS and design information, and a disclosure " +
                        "letter taught me why contemporaneous records win disputes. I didn't leave construction " +
                        "behind; I spent eight years learning the commercial side of it.",
                    keyTerms = listOf("Hold point", "Critical path", "Early warning", "Contemporaneous records"),
                ),
                Lesson(
                    id = "pitch-objections",
                    title = "Handling the hard objections",
                    points = listOf(
                        "'Are you overqualified / will you get bored?' Show commitment: SMSTS, HND, a 3-year plan to Site Manager.",
                        "'Your site knowledge is dated.' Cite what you've refreshed: CDM 2015, Building Safety Act 2022, digital tools.",
                        "'Can you manage younger, experienced foremen?' Lead with respect for trades and clear standards.",
                        "'Salary expectations?' Anchor to the market range for the region and the role, then pivot to value.",
                        "Keep each answer under 60 seconds and finish with a forward-looking line.",
                    ),
                    readAloud = "I understand the concern, and it's fair. Since deciding to come back I've completed " +
                        "SMSTS and my HND, and I've studied the Building Safety Act changes and the digital tools " +
                        "sites now run on. I'm not looking for a stepping stone; I'm looking to become a strong " +
                        "Site Manager within three years, and this role is the right first step.",
                    keyTerms = listOf("Objection handling", "Forward-looking close"),
                ),
            ),
        ),
        Masterclass(
            id = "digital",
            title = "Digital Site Tools",
            subtitle = "Procore · Field View · BIM 360",
            minutes = 2,
            tint = ModuleTint.Slate,
            lessons = listOf(
                Lesson(
                    id = "digital-procore",
                    title = "Procore: the site's single source of truth",
                    points = listOf(
                        "Daily Log: labour numbers by trade, weather, deliveries, visitors, delays, with photos.",
                        "Observations and Inspections: H&S and quality checks raised, assigned and closed with evidence.",
                        "RFIs: one question per RFI, with drawing reference, required-by date and programme impact.",
                        "Punch List: snags with location pins on drawings, assignee, due date and close-out photo.",
                        "Drawings and Specs: always work from the current revision; superseded sheets are watermarked.",
                    ),
                    readAloud = "On Procore I'd keep the daily log honest and complete: who was on site by trade, " +
                        "weather, deliveries and any delays, all with photos. Quality and safety observations get " +
                        "assigned and closed with evidence, RFIs carry a drawing reference and a required-by date, " +
                        "and snags are pinned to the drawing. If it isn't recorded, it didn't happen.",
                    keyTerms = listOf("Daily Log", "RFI", "Punch List", "Observation"),
                ),
                Lesson(
                    id = "digital-fieldview",
                    title = "Viewpoint Field View: QA in your pocket",
                    points = listOf(
                        "Mobile forms for inspections, permits, briefings and QA checklists, working offline on site.",
                        "Snagging and defects tracked against plans, assigned to sub-contractors with deadlines.",
                        "Inspection and test plans (ITPs) turned into digital checklists with sign-off and photos.",
                        "Dashboards show open issues by trade: use them in weekly sub-contractor meetings.",
                        "Handover packs and O&M evidence build up as you go, not in the final fortnight.",
                    ),
                    readAloud = "Field View lets me run quality on the move. Inspection and test plans become digital " +
                        "checklists, so every hold point has a sign-off, a name and a photo. Defects are assigned " +
                        "to the sub-contractor on the plan with a deadline, and I take the dashboard into the " +
                        "weekly progress meeting so we're discussing facts, not opinions.",
                    keyTerms = listOf("ITP", "Hold point", "Defect", "O&M"),
                ),
                Lesson(
                    id = "digital-bim",
                    title = "BIM 360 / Autodesk Construction Cloud",
                    points = listOf(
                        "Docs: controlled drawing and model register with versioning and approval workflows.",
                        "Issues raised directly on 2D sheets or 3D models, so location and context are unambiguous.",
                        "Model coordination and clash detection catch M&E vs structure clashes before they're built.",
                        "Use the model on a tablet to check set-out, service routes and fire-stopping locations.",
                        "Building Safety Act 'golden thread': accurate, accessible digital records for higher-risk buildings.",
                    ),
                    readAloud = "With BIM 360 or Autodesk Construction Cloud I can raise an issue directly on the model, " +
                        "so the designer sees exactly where the clash or query is. I'd use the model on a tablet to " +
                        "check service routes and fire-stopping locations before close-up, and I understand that on " +
                        "higher-risk buildings these records form part of the Building Safety Act golden thread.",
                    keyTerms = listOf("Common Data Environment", "Clash detection", "Golden thread"),
                ),
            ),
        ),
        Masterclass(
            id = "trade",
            title = "Technical Trade QA",
            subtitle = "Single-ply, EFVM and fire-stopping",
            minutes = 3,
            tint = ModuleTint.Sage,
            lessons = listOf(
                Lesson(
                    id = "trade-singleply",
                    title = "Flat roofing: single-ply membranes",
                    points = listOf(
                        "Know the families: PVC and TPO (hot-air welded laps) and EPDM (taped or bonded seams).",
                        "Attachment: mechanically fastened, fully adhered or ballasted, set by a wind uplift calculation (BS EN 1991-1-4).",
                        "Falls: design to 1:40 so the finished roof achieves at least 1:80 (BS 6229); check for ponding.",
                        "Upstands typically at least 150 mm above finished roof level; check detailing at outlets, rooflights and thresholds.",
                        "Daily weld checks: trial welds at shift start, then probe every lap; record and repair defects the same day.",
                        "Protect finished areas: no trafficking or storage without boards, and permits for any hot works nearby.",
                    ),
                    readAloud = "On a single-ply roof I check the system against the specification first: membrane type, " +
                        "attachment method and the wind uplift calculation that sets the fixing pattern. Falls " +
                        "should be designed at one in forty to get at least one in eighty finished. On PVC or TPO, " +
                        "the installer does trial welds at the start of each shift and probes every lap, and I want " +
                        "that recorded with photos before anything is covered.",
                    keyTerms = listOf("PVC", "TPO", "EPDM", "Wind uplift", "BS 6229", "Seam probe"),
                ),
                Lesson(
                    id = "trade-efvm",
                    title = "EFVM and integrity testing",
                    points = listOf(
                        "Electronic Field Vector Mapping (EFVM) finds breaches by tracing current through a wetted membrane to an earthed deck.",
                        "It needs an electrically insulating membrane over a conductive layer; on insulated or timber decks a conductive scrim or primer must be specified before laying.",
                        "Carbon-loaded black EPDM is generally not suitable for EFVM; plan an alternative method with the manufacturer.",
                        "High-voltage (spark) testing is a dry alternative on suitable systems; flood testing risks loading the structure and is slow.",
                        "Test before ballast, paving or green-roof build-ups go on, and keep the test certificate in the handover file.",
                    ),
                    readAloud = "EFVM is a non-destructive leak test: we wet the membrane surface, apply a charge and " +
                        "trace the current to any breach. It only works if the membrane insulates over something " +
                        "conductive, so on a warm roof or timber deck I'd make sure a conductive layer is specified " +
                        "before the membrane goes down. I'd always test before ballast or a green roof goes on, and " +
                        "file the certificate for handover.",
                    keyTerms = listOf("EFVM", "Spark test", "Conductive layer", "Flood test"),
                ),
                Lesson(
                    id = "trade-firestopping",
                    title = "Drylining and fire-stopping inspection",
                    points = listOf(
                        "Build exactly to the manufacturer's tested system (e.g. board type, layers, stud gauge and centres, deflection head).",
                        "Every penetration must be sealed with a product tested for that service and wall type (BS EN 1366-3, classified to BS EN 13501-2).",
                        "Use the right product: intumescent sealant, pipe collars or wraps, coated batt, fire-rated boxes; no expanding foam unless tested for that use.",
                        "Inspect before close-up: photo every seal with a location reference and label; this is a hold point.",
                        "Prefer third-party certified installers (e.g. FIRAS, BM TRADA Q-Mark, IFC) and keep certificates for the golden thread.",
                        "Cavity barriers per Approved Document B; check continuity at junctions and above ceilings.",
                    ),
                    readAloud = "Fire-stopping is a hold point. Partitions must be built exactly as the tested system says: " +
                        "board, layers, studs and the deflection head. Every penetration needs a product tested for " +
                        "that service in that wall, so no generic foam. I'd inspect before ceilings close, photograph " +
                        "each seal against a location reference, and use third-party certified installers so the " +
                        "evidence stands up under the Building Safety Act.",
                    keyTerms = listOf("BS EN 1366-3", "Intumescent", "Cavity barrier", "Approved Document B", "Third-party certification"),
                ),
            ),
        ),
        Masterclass(
            id = "crisis",
            title = "Crisis Management",
            subtitle = "HSE visits, slump failures, disputes",
            minutes = 2,
            tint = ModuleTint.Rose,
            lessons = listOf(
                Lesson(
                    id = "crisis-hse",
                    title = "Unannounced HSE inspection",
                    points = listOf(
                        "Welcome them professionally, check ID, sign them in and give a site induction with PPE.",
                        "Inform the Site Manager, Project Manager and H&S adviser immediately.",
                        "Accompany the inspector throughout; answer factually; never guess or speculate.",
                        "Have ready: Construction Phase Plan, F10 notification (if notifiable), RAMS, registers, scaffold and plant inspections, permits.",
                        "If they find a material breach, Fee for Intervention applies; notices can be Improvement or Prohibition.",
                        "Act on any immediate risk on the spot; record what was said and agreed, then brief the team.",
                    ),
                    readAloud = "If the HSE turned up unannounced, I'd welcome the inspector, check ID, induct them and " +
                        "call my Site Manager and H&S adviser straight away. I'd accompany them throughout and answer " +
                        "factually. I'd have the Construction Phase Plan, F10, RAMS and inspection registers ready. " +
                        "If they flagged a risk, I'd stop that activity immediately, then record everything agreed.",
                    keyTerms = listOf("Construction Phase Plan", "F10", "Fee for Intervention", "Prohibition Notice"),
                ),
                Lesson(
                    id = "crisis-slump",
                    title = "Concrete slump failure",
                    points = listOf(
                        "Check the delivery ticket against the order: mix, consistence class, time batched, admixtures.",
                        "Slump test to BS EN 12350-2; compare with the specified class (e.g. S3) and tolerances.",
                        "Never allow water to be added on site unless the mix design and specification permit it.",
                        "Reject non-conforming loads, record why, and photograph the ticket and the test.",
                        "Take cubes as specified, protect the pour sequence (cold joints), and inform the engineer and supplier.",
                        "Keep commercial records: delay, standing time, pump costs; raise an early warning if programme or cost is affected.",
                    ),
                    readAloud = "If a slump test failed, that truck doesn't discharge. I'd check the ticket against the " +
                        "order, re-test if there's any doubt about the test, and never let the driver add water to " +
                        "get it moving. I'd reject the load with photos and a written reason, protect the pour " +
                        "against a cold joint, call the engineer and the supplier, and record the delay and costs " +
                        "so the commercial team can recover them.",
                    keyTerms = listOf("BS EN 12350-2", "Consistence class", "Cold joint", "Cube test"),
                ),
                Lesson(
                    id = "crisis-dispute",
                    title = "Sub-contractor dispute and walk-off",
                    points = listOf(
                        "Separate the people from the problem: meet the foreman privately, calmly, the same morning.",
                        "Get the facts: valuation date, application, payment notice and what's disputed.",
                        "Don't make promises on payment; escalate the same hour to the PM and QS with the facts.",
                        "Protect the programme: identify critical-path work, re-sequence, and warn affected trades.",
                        "Keep safety first: no unsafe partial work left (e.g. open fire-stopping, unsecured boards).",
                        "Record everything and raise an early warning under the contract where required.",
                    ),
                    readAloud = "I'd speak to the foreman privately and straight away, listen, and get the facts on the " +
                        "valuation and the disputed variation. I wouldn't promise payment I can't authorise; I'd take " +
                        "it to the PM and QS within the hour. Meanwhile I'd protect the programme by re-sequencing " +
                        "critical work, make sure nothing is left unsafe, and record it all, including an early warning " +
                        "if the contract requires one.",
                    keyTerms = listOf("Payment notice", "Variation", "Early warning", "Critical path"),
                ),
            ),
        ),
    )
}
