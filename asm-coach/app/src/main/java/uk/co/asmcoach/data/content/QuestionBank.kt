package uk.co.asmcoach.data.content

import uk.co.asmcoach.data.model.QuestionCategory.COMMERCIAL
import uk.co.asmcoach.data.model.QuestionCategory.CUSTOMER
import uk.co.asmcoach.data.model.QuestionCategory.DIGITAL
import uk.co.asmcoach.data.model.QuestionCategory.MOTIVATION
import uk.co.asmcoach.data.model.QuestionCategory.PEOPLE
import uk.co.asmcoach.data.model.QuestionCategory.PROGRAMME
import uk.co.asmcoach.data.model.QuestionCategory.QUALITY
import uk.co.asmcoach.data.model.QuestionCategory.SAFETY
import uk.co.asmcoach.data.model.QuestionCategory.SCENARIO
import uk.co.asmcoach.data.model.QuestionCategory.TECHNICAL

/** The kind of employer a question is typical of. */
enum class Sector { Any, Housebuilder, Contractor, FitOut, Refurb }

data class CuratedQuestion(
    val id: String,
    val category: String,
    val question: String,
    /** What a strong answer must contain: used by the live interviewer and the scorer. */
    val lookingFor: String,
    /** Real-world follow-ups used when an answer is thin. */
    val probes: List<String>,
    val sector: Sector = Sector.Any,
    /** 1 = opener, 2 = core, 3 = senior/stretch. */
    val level: Int = 2,
)

/**
 * Researched question bank for UK Assistant Site Manager interviews.
 *
 * Built from recurring themes in UK interview guidance (Indeed UK, Randstad UK, Workbred,
 * candidate reports for Barratt, Bellway, Morgan Sindall and Willmott Dixon) and UK regulatory
 * practice: CDM 2015, Work at Height Regulations 2005, excavation inspection under CDM reg 22,
 * scaffold inspection before first use / every 7 days / after weather or alteration, temporary
 * works per BS 5975, RIDDOR, LOLER, NHBC key-stage inspections and reportable items, the Building
 * Safety Act 2022. Common probes follow competency-interview practice: ownership ("what did you
 * personally do?"), evidence ("what was the result, in numbers?") and regulation ("which rule
 * applies?").
 */
object QuestionBank {

    private fun q(
        id: String,
        category: String,
        question: String,
        lookingFor: String,
        vararg probes: String,
        sector: Sector = Sector.Any,
        level: Int = 2,
    ) = CuratedQuestion(id, category, question, lookingFor, probes.toList(), sector, level)

    val all: List<CuratedQuestion> = listOf(
        // ---- Motivation, background and the career story ------------------------------------
        q("m01", MOTIVATION, "Talk us through your background and what brings you to this role.",
            "A tight 90-second story: trades, M&A, why site management now, and what they get on day one.",
            "Why not go back on the tools instead?", "What specifically drew you to us rather than any contractor?", level = 1),
        q("m02", MOTIVATION, "You've been out of construction for eight years. Why come back now, and why should we believe you'll stay?",
            "Honest reason, evidence of commitment (HND, SMSTS), a clear 3–5 year plan, no apology.",
            "What would make you leave within a year?", "What have you done in the last six months to get site-ready?", level = 1),
        q("m03", MOTIVATION, "What do you know about us and the projects we're delivering in the North West?",
            "Specific research: recent sites, sectors, values, how they build; links to own fit.",
            "Which of our sites have you looked at?", "What do you think our biggest challenge is right now?", level = 1),
        q("m04", MOTIVATION, "What does an Assistant Site Manager actually do day to day?",
            "Realistic picture: H&S, coordinating trades, quality checks, records, deliveries, supporting the Site Manager.",
            "What would your first hour on site look like?", "Where does the ASM role end and the Site Manager's begin?", level = 1),
        q("m05", MOTIVATION, "How does eight years in M&A make you a better site manager rather than a worse one?",
            "Concrete mapping: deal timetable to programme, due diligence to pre-start checks, negotiation to subbie management, records to claims.",
            "Give me one real example from a deal that maps to site.", "What did M&A not teach you that site will?"),
        q("m06", MOTIVATION, "Some would say you're overqualified commercially and underqualified on site. How do you answer that?",
            "Acknowledges the gap, shows how it's being closed, turns commercial skill into an asset for the team.",
            "Which site skills are you weakest on?", "How will you earn the respect of a 25-year-old foreman with ten years on site?", level = 3),
        q("m07", MOTIVATION, "Where do you see yourself in three to five years?",
            "Ambition aligned to the employer's path: ASM to Site Manager, possibly Project Manager; commitment to the business.",
            "What would you need from us to get there?"),
        q("m08", MOTIVATION, "What are your salary expectations?",
            "Market-aware range for an ASM in the region (roughly £35–50k in 2026 adverts), flexibility, focus on the package and progression.",
            "Would you accept the bottom of our range?"),
        q("m09", MOTIVATION, "Describe yourself in three words, and give us evidence for each.",
            "Three relevant traits with a quick proof for each, not clichés.", "Which of those would your old team disagree with?", level = 1),
        q("m10", MOTIVATION, "What's your biggest weakness as a site manager right now?",
            "A real, current gap (e.g. recent site software, newer regs) with an active plan to fix it.",
            "What have you done about it this month?"),
        q("m11", MOTIVATION, "Tell us about something you're proud of from your time on the tools.",
            "A specific job, the standard achieved, why it mattered, what it shows about how they'll manage quality.",
            "What would the client have said about it?", level = 1),

        // ---- Health and safety ---------------------------------------------------------------
        q("s01", SAFETY, "How would you make sure everyone on your site follows safe working practice, every day?",
            "Inductions, RAMS briefings, toolbox talks, daily inspections, visible leadership, challenging unsafe acts, records.",
            "What do you do with someone who keeps ignoring you?", "How would you know it's working?"),
        q("s02", SAFETY, "Walk us through your daily safety checks before the gang starts.",
            "Access/egress, edge protection, scaffold tags, excavations, housekeeping, plant, welfare, fire points, permits; recorded.",
            "What's the most common thing you'd expect to find wrong?", "Where do you record it?"),
        q("s03", SAFETY, "When must a scaffold be inspected, by whom, and what are you looking for?",
            "Before first use, at least every 7 days, after adverse weather or alteration; competent person; ties, boards, guardrails, toe boards, base plates, tag and register.",
            "The scaffold tag is out of date and the gang are on it. What now?", "Who's allowed to alter a scaffold?"),
        q("s04", SAFETY, "What are your duties for an excavation on your site?",
            "Underground services located (CAT and Genny, drawings, permit to dig), support or battering, competent inspection at the start of each shift and after events, edge protection, safe access, keep plant and spoil back.",
            "A digger has just hit an unmarked cable. What do you do?", "When can someone enter a trench?"),
        q("s05", SAFETY, "What is temporary works, and what is your role in it as an ASM?",
            "Examples (props, formwork, excavation support, scaffolds); TWC/TWS roles under BS 5975; design checks, permits to load/strike; never alter without authority.",
            "A subbie removes a prop early to get access. What do you do?", "Who can give permission to strike formwork?", level = 3),
        q("s06", SAFETY, "Explain how a permit-to-work system works and when you'd use one.",
            "Formal written control for high-risk work: hot works, confined spaces, excavation, live services, roof work; issue, brief, monitor, close out.",
            "Who signs it off at the end, and why does that matter?"),
        q("s07", SAFETY, "What are the main duties of a principal contractor under CDM 2015, and how do they land on you?",
            "Plan, manage and monitor the construction phase, Construction Phase Plan, inductions, welfare, cooperation between contractors; ASM delivers it day to day.",
            "What's in a Construction Phase Plan?", "What's an F10 and when is one needed?", level = 3),
        q("s08", SAFETY, "Tell us about a time you stopped work because it wasn't safe.",
            "Specific situation, personal decision, how they handled pushback and programme pressure, outcome and follow-up.",
            "Who pushed back, and what did you say to them?", "What did it cost the programme?"),
        q("s09", SAFETY, "An operative falls from a stepladder and can't put weight on his leg. Take us through the next hour.",
            "First aid and emergency services, make safe and preserve the scene, inform managers, witness statements, RIDDOR assessment (specified injury / over-7-day), investigation, lessons.",
            "Is it RIDDOR reportable?", "What would the investigation look at?", level = 3),
        q("s10", SAFETY, "How do you check a sub-contractor's RAMS before they start?",
            "Specific to the task and site, sequence, hazards, controls, competence/cards, plant, PPE, emergency; reject generic RAMS; brief and sign.",
            "What's the most common weakness you'd expect to find?", "What if they start before you've approved them?"),
        q("s11", SAFETY, "How do you manage lifting operations on site?",
            "LOLER, lift plan by an appointed person, competent operator and slinger/signaller, thorough examination records, exclusion zones, weather.",
            "Who writes the lift plan?", "The crane driver says the wind's fine. You're not sure. What do you do?"),
        q("s12", SAFETY, "How would you deliver a toolbox talk that people actually listen to?",
            "Short, relevant to today's risk, interactive, real examples, check understanding, record attendance.",
            "Give us a toolbox talk now on working at height, in 60 seconds."),
        q("s13", SAFETY, "What does good welfare look like on a site, and whose job is it?",
            "Toilets, washing, drinking water, drying room, rest area, kept clean; principal contractor duty under CDM Schedule 2; ASM checks daily.",
            "The cabins are a mess and the trades are complaining. What do you do?", level = 1),
        q("s14", SAFETY, "How do you manage the public and site security on a live residential or town-centre site?",
            "Hoarding, signage, gates locked, pedestrian routes, banksmen for deliveries, out-of-hours checks, occupied-homes interfaces.",
            "Kids have been getting onto site at night. What do you change?"),
        q("s15", SAFETY, "What does SMSTS mean for you in practice, beyond the certificate?",
            "Applies the legal duties, risk assessment, accident investigation and leadership from the course to daily decisions.",
            "Name one thing from SMSTS you've already used.", level = 1),

        // ---- Quality and inspection ----------------------------------------------------------
        q("q01", QUALITY, "How do you make sure work is built right first time?",
            "Clear info and briefings, sample/benchmark panels, inspection and test plans, hold points, checks before covering up, photo records, close snags fast.",
            "How do you stop work being covered before you've inspected it?", "Give an example where you caught a defect early."),
        q("q02", QUALITY, "What are NHBC key-stage inspections, and how do you prepare a plot for them?",
            "Foundations, drainage, superstructure, pre-plaster, pre-handover; 24h notice; own pre-check, photos, close reportable items before proceeding.",
            "You get three reportable items on one plot. What happens next?", "How would you reduce reportable items across the site?", sector = Sector.Housebuilder),
        q("q03", QUALITY, "What would you check on a plot before plastering?",
            "First fix M&E complete and tested, insulation and vapour control, fire-stopping and cavity barriers, noggins/patresses, board type and fixings, photos.",
            "What's most often missed?", sector = Sector.Housebuilder),
        q("q04", QUALITY, "How do you manage snagging so the customer gets a defect-free home?",
            "Own snag walk early, trade-by-trade lists with dates, re-inspect, pre-handover quality check, no surprises at demonstration.",
            "A customer finds twenty snags at handover. Whose fault is that?", sector = Sector.Housebuilder),
        q("q05", QUALITY, "How would you inspect a flat roof before it's signed off?",
            "Falls and ponding, laps/welds probed, upstands at least 150 mm, outlets and details, integrity test (e.g. EFVM) before covering, certificates.",
            "The roofer says it's been tested. What evidence do you want?"),
        q("q06", QUALITY, "How do you inspect fire-stopping and fire-rated partitions before they're closed up?",
            "Built to the tested system, every penetration sealed with a tested product, labelling, photos against location, third-party certified installer, hold point.",
            "You find expanding foam in a fire wall penetration. What now?", "Why does this matter more since Grenfell?", level = 3),
        q("q07", QUALITY, "A finish isn't to standard and the trade says it's 'within tolerance'. How do you handle it?",
            "Check the spec and tolerance standard, measure, refer to sample panel, agree remedial, record, escalate if needed.",
            "What if the trade refuses?"),
        q("q08", QUALITY, "What records would you keep to prove the quality of what's been built?",
            "ITPs, inspection sheets, test certificates, photos, NHBC/warranty records, as-builts, O&M inputs, golden thread for higher-risk buildings.",
            "Who uses those records after you've left the site?"),

        // ---- Programme and planning ----------------------------------------------------------
        q("p01", PROGRAMME, "How do you keep a site on programme?",
            "Master programme to 2–3 week look-aheads, daily coordination, constraints log, long-lead items, early warning of slippage, recovery plans.",
            "What's in your look-ahead that isn't in the master programme?", "How do you know you're behind before it's too late?"),
        q("p02", PROGRAMME, "Tell us about a time a project fell behind. What did you do?",
            "Diagnosed causes, re-sequenced, resourced, communicated, measurable recovery; honest about what couldn't be saved.",
            "How many days did you recover?", "What would you do differently?"),
        q("p03", PROGRAMME, "Two trades need the same area on the same day. How do you decide?",
            "Critical path, dependencies, safety interfaces, agree a sequence with both foremen, record it.",
            "One of them is your biggest subbie and threatens to walk. Now what?"),
        q("p04", PROGRAMME, "A key delivery hasn't turned up and the gang is standing. What do you do?",
            "Chase supplier, redeploy labour to other work, re-sequence, record standing time and cause, notify PM, prevent recurrence.",
            "Who pays for the standing time?"),
        q("p05", PROGRAMME, "How do you plan for weather on a programme?",
            "Watch forecasts, sequence weather-sensitive work, protect materials and finished work, temporary weather protection, record weather days.",
            "Concrete pour tomorrow and frost forecast. What do you do?"),
        q("p06", PROGRAMME, "What does a good daily coordination meeting look like on site?",
            "Short, every trade, today's work, interfaces, safety points, constraints, actions recorded.",
            "How do you stop it becoming a moaning session?", level = 1),
        q("p07", PROGRAMME, "How do you manage build-stage completions so plots hit their legal completion dates?",
            "Plot-by-plot programme, critical trades, early warning to sales, pre-handover checks, no rushing quality.",
            "Sales has promised a completion you know is unrealistic. What do you do?", sector = Sector.Housebuilder),
        q("p08", PROGRAMME, "How do you phase work around occupants on a live refurbishment?",
            "Phasing plan, segregation, noise/dust windows, safeguarding, communication with occupants, out-of-hours work.",
            "The client changes the phasing at short notice. What happens?", sector = Sector.Refurb),

        // ---- People, sub-contractors and leadership -------------------------------------------
        q("l01", PEOPLE, "How do you manage sub-contractors to get the best from them?",
            "Clear scope and standards, pre-start meeting, daily coordination, fair but firm, performance tracking, recognition, escalation route.",
            "Tell us about a subbie who wasn't performing.", "How do you manage someone who's been on site longer than you?"),
        q("l02", PEOPLE, "Tell us about a time you resolved a conflict between two people or trades.",
            "Specific conflict, met both, facts not blame, agreed outcome, followed up; result.",
            "What did you personally say to each of them?", "Did the relationship hold afterwards?"),
        q("l03", PEOPLE, "A tradesperson ignores your instruction on how their work should be done. How do you respond?",
            "Calm, private, explain why (spec/safety), check understanding, record, escalate through their employer if repeated.",
            "And if it's a safety instruction?"),
        q("l04", PEOPLE, "How would you motivate a team that's behind and fed up?",
            "Understand causes, clear short-term goals, remove blockers, recognise progress, lead from the front, fair overtime decisions.",
            "Give a real example where you've done that."),
        q("l05", PEOPLE, "How will you build credibility with experienced foremen when you're new to the role?",
            "Respect for trades, use own trade knowledge, listen first, be consistent, follow through on promises, know the drawings.",
            "One foreman openly undermines you in the morning meeting. What do you do?", level = 3),
        q("l06", PEOPLE, "Tell us about a time you disagreed with your manager.",
            "Raised it privately with evidence, listened, accepted or escalated appropriately, professional outcome.",
            "Were you right?"),
        q("l07", PEOPLE, "How do you communicate with someone whose first language isn't English on a safety-critical task?",
            "Check understanding, visuals/demonstration, translated RAMS, buddy/interpreter, never assume.",
            "How do you know they've understood?"),
        q("l08", PEOPLE, "Tell us about a mistake you made and what you did about it.",
            "Owns it, fixed it, told the right people, changed the process.",
            "Who did you tell first?"),
        q("l09", PEOPLE, "How do you handle a site manager who's under pressure and taking it out on you?",
            "Stay professional, focus on facts and solutions, raise it calmly at the right time, keep the site running.",
            "Where's the line where you'd escalate it?"),

        // ---- Commercial and cost --------------------------------------------------------------
        q("c01", COMMERCIAL, "How can an Assistant Site Manager help protect the project's margin?",
            "Accurate records, early warnings, controlling waste and rework, checking deliveries, measuring work done, spotting variations, plant off-hire.",
            "Give a cost you've personally saved, in pounds."),
        q("c02", COMMERCIAL, "A sub-contractor says the work they've been asked to do is a variation. What do you do?",
            "Check scope and drawings, don't instruct extra work verbally, record facts, refer to QS/PM, follow the contract process (e.g. NEC early warning/compensation event, JCT instructions).",
            "They're refusing to carry on until it's agreed. What now?", level = 3),
        q("c03", COMMERCIAL, "What records do you keep that the QS will thank you for later?",
            "Daily diary, labour returns, weather, delays and causes, instructions received, photos with dates, delivery notes, plant records.",
            "How would those records win or lose a claim?"),
        q("c04", COMMERCIAL, "Tell us about a time you contributed to a cost saving.",
            "Specific action, quantified saving, no compromise on safety or quality.",
            "How did you measure it?"),
        q("c05", COMMERCIAL, "What's the difference between NEC and JCT from a site point of view?",
            "Awareness level: NEC collaborative with early warnings and compensation events, JCT instructions and variations; follow the PM/QS on process.",
            "What's an early warning and when would you raise one?", level = 3),
        q("c06", COMMERCIAL, "How do you check a delivery before you sign for it?",
            "Against order and spec: quantity, damage, grade/class, certificates; note discrepancies on the ticket; reject if wrong.",
            "The driver's in a hurry and says just sign it. What do you do?", level = 1),
        q("c07", COMMERCIAL, "How would you reduce waste on site?",
            "Order accurately, protect materials, segregation and skips, reuse, fair accountability for damage, measure skip volumes.",
            "What's the biggest source of waste you've seen?"),

        // ---- Technical build knowledge --------------------------------------------------------
        q("t01", TECHNICAL, "What would you check before a concrete pour?",
            "Formwork and props, reinforcement and cover checked, embedded items, access, pump and plant, weather, mix order, cube and slump arrangements, sign-off.",
            "The slump test fails on the third truck. What do you do?"),
        q("t02", TECHNICAL, "Talk us through the build sequence of a typical two-storey timber-frame or masonry house.",
            "Foundations, oversite, drainage, superstructure, roof, first fix, plaster, second fix, finishes, externals; interfaces and inspections.",
            "Where do most delays happen in that sequence?", sector = Sector.Housebuilder),
        q("t03", TECHNICAL, "What's the difference between a warm roof and a cold roof, and why does it matter?",
            "Insulation position, condensation risk, ventilation, vapour control layer placement.",
            "Where does the vapour control layer go on a warm deck?"),
        q("t04", TECHNICAL, "How do you check drylining is installed correctly?",
            "Correct board type and layers per the system, stud centres and gauge, fixings, deflection heads, joints, fire/acoustic requirements, patresses.",
            "What's a deflection head for?"),
        q("t05", TECHNICAL, "How would you manage a leak appearing in a newly finished flat roof?",
            "Make safe and protect, trace (not guess), check details and outlets, integrity test, roofer to repair, record and close out.",
            "How would you find the source without ripping up the roof?"),
        q("t06", TECHNICAL, "How do you read and check set-out against the drawings?",
            "Gridlines, levels, dimensions, current revision, engineer's set-out, check before work proceeds.",
            "What happens if a wall is 50 mm out?"),
        q("t07", TECHNICAL, "How do you make sure everyone's working from the latest drawings?",
            "Drawing register, document control (e.g. CDE), withdraw superseded copies, brief changes.",
            "Someone's built off an old revision. What now?"),
        q("t08", TECHNICAL, "What are the main checks on brickwork and blockwork?",
            "Line, level, plumb, gauge, joints, cavity clean, wall ties, DPCs, cavity trays, weep vents, cavity barriers.",
            "What happens if cavities aren't kept clean?", sector = Sector.Housebuilder),
        q("t09", TECHNICAL, "What do you understand by the 'golden thread' under the Building Safety Act?",
            "Accurate, up-to-date digital information about a higher-risk building, kept through design, construction and occupation; site records feed it.",
            "What would you personally contribute to it?", level = 3),
        q("t10", TECHNICAL, "What does a waterproofing or tanking installation need to pass inspection?",
            "Substrate prep, correct system and grade, laps and details, protection, testing where required, certificates and photos.",
            "Who signs it off before backfill?"),

        // ---- Digital site tools and admin -----------------------------------------------------
        q("d01", DIGITAL, "What site management software have you used, and how would you get up to speed with ours?",
            "Honest about experience, transferable (deal-room/document systems), concrete plan to learn fast, digital records habits.",
            "What would your daily log on that system contain?", level = 1),
        q("d02", DIGITAL, "How do you raise and track an RFI?",
            "One clear question, drawing reference, required-by date, programme impact, tracked to close, design team answer briefed to trades.",
            "The designer hasn't answered and the trade is waiting. What now?"),
        q("d03", DIGITAL, "What goes into a good daily site diary?",
            "Labour by trade, plant, deliveries, weather, visitors, instructions, delays and causes, H&S issues, photos.",
            "Why does the weather matter in a diary?", level = 1),
        q("d04", DIGITAL, "How would you use photos and digital records to protect the company?",
            "Dated, located photos before cover-up, linked to inspections and snags, evidence for claims and warranties.",
            "Give an example where a photo would settle an argument."),
        q("d05", DIGITAL, "How would you use BIM or 3D models on site?",
            "Check service routes, clashes, set-out, fire-stopping locations, raise issues on the model.",
            "Have you raised an issue on a model before?", level = 3),

        // ---- Customers and stakeholders -------------------------------------------------------
        q("u01", CUSTOMER, "How do you make sure a customer has a great experience when they move into a new home?",
            "Quality at handover, home demonstration, responsive aftercare, honest communication, customer-first culture (housebuilders are rated on satisfaction).",
            "A customer is angry about a delayed completion. What do you say?", sector = Sector.Housebuilder),
        q("u02", CUSTOMER, "A neighbour complains about noise and mud on the road. How do you handle it?",
            "Listen, apologise where right, act (wheel wash, road sweeper, working hours), follow up, record; Considerate Constructors mindset.",
            "They say they'll go to the council. Now what?"),
        q("u03", CUSTOMER, "How would you handle a client representative who keeps changing their mind on site?",
            "Polite, record requests, route changes through the formal process, explain cost/programme impact via PM.",
            "What if they instruct a trade directly?"),
        q("u04", CUSTOMER, "How do you work in an occupied building, like a school or hospital, without disrupting it?",
            "Phasing, segregation, safeguarding/DBS, noise windows, liaison with the site contact, out-of-hours works.",
            "A child gets past the hoarding. What do you do?", sector = Sector.Refurb),

        // ---- Live scenarios -------------------------------------------------------------------
        q("x01", SCENARIO, "An HSE inspector arrives unannounced and your Site Manager is off. What do you do, step by step?",
            "Welcome and induct, inform managers, accompany, factual answers, documents ready (CPP, RAMS, registers), act immediately on any risk, record outcome.",
            "The inspector points at a scaffold with a missing toe board. What do you say?", "What notices could they issue?", level = 3),
        q("x02", SCENARIO, "It's 3pm, the crane's booked until 4, and the wind's picking up. The lift isn't finished. What do you do?",
            "Follow the lift plan wind limits and appointed person, stop if outside limits, make safe, re-plan, record; cost isn't a reason.",
            "The subbie says he'll lose a day's hire. Your answer?"),
        q("x03", SCENARIO, "You find asbestos-looking material during a refurbishment strip-out. Talk us through it.",
            "Stop work, evacuate the area, prevent spread, check the asbestos survey, get it tested by a specialist, notify, licensed removal if needed, record.",
            "The client says it was surveyed and is fine. Do you restart?", sector = Sector.Refurb, level = 3),
        q("x04", SCENARIO, "A water main bursts in a trench at 7am and the road is flooding. What do you do?",
            "Make safe, evacuate trench, isolate supply/call utility, protect public, inform managers, record, investigate.",
            "Who do you call first?"),
        q("x05", SCENARIO, "The drylining sub-contractor's gang walk off site over a payment dispute. What do you do today?",
            "Facts, don't promise payment, escalate to PM/QS, make work safe, re-sequence critical work, record, early warning.",
            "The M&E sub now can't progress. How do you keep them productive?"),
        q("x06", SCENARIO, "You arrive and find the site has been broken into overnight and tools and copper stolen.",
            "Preserve scene, check for hazards (stripped cables, damaged scaffold), police and insurer, secure, record, review security.",
            "Is it safe to let the trades start?"),
    )

    /** Guesses the employer type from the listing so sector-specific questions only appear where they fit. */
    fun sectorOf(text: String): Sector {
        val t = text.lowercase()
        return when {
            listOf("plot", "housebuilder", "new homes", "nhbc", "residential development", "persimmon", "barratt", "bellway", "taylor wimpey", "redrow", "vistry")
                .any { it in t } -> Sector.Housebuilder
            listOf("fit-out", "fit out", "cat a", "cat b", "interiors").any { it in t } -> Sector.FitOut
            listOf("refurb", "refurbishment", "live environment", "occupied", "school", "hospital").any { it in t } -> Sector.Refurb
            else -> Sector.Contractor
        }
    }

    /** Generic probes used when an answer is thin and no question-specific probe fits. */
    val genericProbes = mapOf(
        "ownership" to "You've said 'we' a lot. What did you personally do?",
        "result" to "And what was the result? Give me a number if you can.",
        "example" to "That's the theory. Give me a real example where you've actually done that.",
        "regulation" to "Which regulation or standard applies there?",
        "next" to "And what happens next? Walk me through the next step.",
        "risk" to "What could go wrong with that approach?",
    )
}
