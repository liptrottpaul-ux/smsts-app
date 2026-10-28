package uk.co.asmcoach.data.content

import uk.co.asmcoach.data.model.BankQuestion
import uk.co.asmcoach.data.model.PrepTopic
import uk.co.asmcoach.data.model.QuestionCategory

/**
 * Builds role prep on the device in milliseconds, with no AI: it pulls the advert's own
 * requirements and turns them into prep topics and questions, backed by the researched
 * [QuestionBank]. The AI later replaces these with richer versions when it can; until then the
 * user always has something specific to work on.
 */
object LocalPackBuilder {

    /** A theme the advert might mention, with the bank questions and prep points that cover it. */
    private data class Theme(
        val title: String,
        val category: String,
        val keywords: Regex,
        val bankIds: List<String>,
        val why: String,
    )

    private val themes = listOf(
        Theme("NHBC key stages and build quality", QuestionCategory.QUALITY, Regex("(?i)nhbc|key stage|reportable|build quality|premier guarantee|lab?c warranty"),
            listOf("q02", "q03", "q04"), "Housebuilders are judged on NHBC inspections and customer quality."),
        Theme("Managing sub-contractors and trades", QuestionCategory.PEOPLE, Regex("(?i)sub-?contract|trades|labour|gang|operatives"),
            listOf("l01", "l03", "l05"), "The advert expects you to run trades day to day."),
        Theme("Programme and look-aheads", QuestionCategory.PROGRAMME, Regex("(?i)programme|schedule|deadline|completion|look-?ahead|on time"),
            listOf("p01", "p02", "p03"), "Delivering to programme is how an ASM is measured."),
        Theme("Health, safety and CDM duties", QuestionCategory.SAFETY, Regex("(?i)health|safety|h&s|cdm|rams|smsts|sssts|inspection|scaffold"),
            listOf("s01", "s02", "s07", "s10"), "Safety leadership is non-negotiable for this role."),
        Theme("Quality checks and snagging", QuestionCategory.QUALITY, Regex("(?i)quality|snag|defect|finish|standard|right first time"),
            listOf("q01", "q07", "q08"), "Getting it right first time protects cost and reputation."),
        Theme("Customer and client focus", QuestionCategory.CUSTOMER, Regex("(?i)customer|client|resident|homeowner|stakeholder|neighbour"),
            listOf("u01", "u02", "u03"), "The advert stresses the people you build for."),
        Theme("Commercial awareness and records", QuestionCategory.COMMERCIAL, Regex("(?i)cost|budget|commercial|valuation|variation|waste|margin"),
            listOf("c01", "c02", "c03"), "Your M&A background is strongest here: make it count."),
        Theme("Digital site tools and reporting", QuestionCategory.DIGITAL, Regex("(?i)procore|field ?view|bim|software|it skills|digital|reporting|computer|asite|viewpoint"),
            listOf("d01", "d02", "d03"), "Sites now run on digital records; show you're fluent."),
        Theme("Fire safety and compliance", QuestionCategory.TECHNICAL, Regex("(?i)fire|building safety|golden thread|compartment"),
            listOf("q06", "t09"), "Post-Grenfell, fire-stopping evidence is closely checked."),
        Theme("Live environments and refurbishment", QuestionCategory.SCENARIO, Regex("(?i)refurb|live environment|occupied|school|hospital|phasing"),
            listOf("p08", "u04", "x03"), "Working around occupants changes how you plan and stay safe."),
        Theme("Technical knowledge of the build", QuestionCategory.TECHNICAL, Regex("(?i)timber frame|masonry|groundwork|roof|drylining|concrete|structural|substructure|superstructure"),
            listOf("t01", "t02", "t04"), "The panel will test that you know how it's built."),
    )

    private val requirementWords = Regex(
        "(?i)experience|knowledge|understanding|ability|able to|essential|required|desirable|must|proven|" +
            "smsts|sssts|cscs|first aid|nhbc|qualified|hnc|hnd|degree|responsib|you will|manage|ensure|oversee",
    )

    /** Short requirement phrases from the advert, best first. */
    fun requirements(advert: String): List<String> =
        advert.split(Regex("[\\n•·]|(?<=[.!?])\\s+"))
            .map { it.trim().trimStart('-', '*', '•', ' ').trim() }
            .filter { it.length in 18..170 && requirementWords.containsMatchIn(it) }
            .distinct()
            .take(12)

    fun build(advert: String, sectorText: String): Pair<List<PrepTopic>, List<BankQuestion>> {
        val reqs = requirements(advert)
        val sector = QuestionBank.sectorOf(sectorText)
        val bank = QuestionBank.all.associateBy { it.id }
        val matched = themes.mapNotNull { theme ->
            val ref = reqs.firstOrNull { theme.keywords.containsMatchIn(it) }
                ?: advert.split(Regex("(?<=[.!?])\\s+")).firstOrNull { theme.keywords.containsMatchIn(it) && it.length < 200 }
            ref?.let { theme to it.trim() }
        }.ifEmpty {
            // Thin advert: fall back to the themes that matter for this kind of employer.
            val defaults = if (sector == Sector.Housebuilder) listOf(0, 1, 3, 5) else listOf(1, 2, 3, 6)
            defaults.map { themes[it] to "" }
        }

        val topics = matched.take(6).map { (theme, ref) ->
            val qs = theme.bankIds.mapNotNull { bank[it] }
            PrepTopic(
                title = theme.title,
                listingRef = ref,
                whyItMatters = theme.why,
                points = qs.map { it.lookingFor }.take(4),
                sayThis = "",
            )
        }

        val questions = buildList {
            // Questions quoting the advert's own requirements, the way real panels work through the spec.
            reqs.take(6).forEachIndexed { i, req ->
                val theme = themes.firstOrNull { it.keywords.containsMatchIn(req) }
                add(
                    BankQuestion(
                        category = theme?.category ?: QuestionCategory.MOTIVATION,
                        askedBy = i % 3,
                        question = "The advert says: “${req.trimEnd('.')}.” Give us a specific example that shows you can do that.",
                        listingRef = req,
                        lookingFor = "A real example matched to this requirement: situation, what you personally did, and a measurable result.",
                        probes = listOf(
                            QuestionBank.genericProbes.getValue("ownership"),
                            QuestionBank.genericProbes.getValue("result"),
                        ),
                    ),
                )
            }
            // The researched bank questions behind each matched theme.
            matched.forEach { (theme, ref) ->
                theme.bankIds.take(2).mapNotNull { bank[it] }.forEach { c ->
                    add(
                        BankQuestion(
                            category = c.category,
                            askedBy = when (c.category) {
                                QuestionCategory.COMMERCIAL, QuestionCategory.DIGITAL -> 1
                                QuestionCategory.SAFETY, QuestionCategory.QUALITY, QuestionCategory.TECHNICAL -> 2
                                else -> 0
                            },
                            question = c.question,
                            listingRef = ref,
                            lookingFor = c.lookingFor,
                            probes = c.probes,
                        ),
                    )
                }
            }
        }.distinctBy { it.question }
        return topics to questions
    }
}
