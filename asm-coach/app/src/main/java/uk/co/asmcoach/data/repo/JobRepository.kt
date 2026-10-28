package uk.co.asmcoach.data.repo

import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.SearchRegion
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import uk.co.asmcoach.data.remote.AppJson
import uk.co.asmcoach.data.remote.FirecrawlClient
import uk.co.asmcoach.data.remote.PageFetcher
import uk.co.asmcoach.data.remote.ReedClient
import uk.co.asmcoach.data.remote.ReedJob
import uk.co.asmcoach.data.remote.RemoteException
import uk.co.asmcoach.data.remote.SerperClient
import uk.co.asmcoach.data.remote.htmlToText
import uk.co.asmcoach.data.settings.SettingsRepository
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Finds live Assistant Site Manager listings. Reed's official API is preferred (real listings
 * with salary and full descriptions); Serper (Google results) and Firecrawl are web-search
 * fallbacks across Reed, Indeed, Totaljobs and CV-Library.
 */
class JobRepository(
    private val reed: ReedClient,
    private val serper: SerperClient,
    private val firecrawl: FirecrawlClient,
    private val settings: SettingsRepository,
    private val pages: PageFetcher,
) {
    /**
     * Opens a board's search-results page and returns the individual vacancies listed on it.
     * Uses Firecrawl when a key is set (needed for Indeed); Reed pages are also read directly.
     */
    suspend fun expandSearchPage(page: JobListing): List<JobListing> {
        val key = settings.current().firecrawlKey
        val found = buildList {
            if (key.isNotBlank()) {
                runCatching { firecrawl.scrape(key, page.url, maxChars = 60_000) }.getOrNull()
                    ?.let { addAll(ListingParser.vacanciesFromMarkdown(it, page.url)) }
            }
            if (isEmpty() && page.url.contains("reed.co.uk")) {
                pages.html(page.url)?.let { addAll(ListingParser.vacanciesFromHtml(it, page.url)) }
            }
        }
        return found.distinctBy { it.url }.take(25).map { v ->
            val context = "${v.title} ${v.context}"
            JobListing(
                id = v.url.hashCode().toUInt().toString(16),
                title = v.title,
                url = v.url,
                snippet = v.context.ifBlank { "Listed on ${page.source}: ${page.title}" },
                source = page.source,
                location = ListingParser.location(context) ?: page.location,
                salary = ListingParser.salary(context),
            )
        }
    }

    suspend fun search(region: SearchRegion, pastWeekOnly: Boolean): List<JobListing> {
        val s = settings.current()
        return when {
            s.reedKey.isNotBlank() -> searchReed(s.reedKey, region, pastWeekOnly)
            s.serperKey.isNotBlank() -> serper.search(s.serperKey, webQuery(region), pastWeekOnly)
                .map { RawHit(it.title, it.link, it.snippet, it.date) }
                .toListings()
            s.firecrawlKey.isNotBlank() -> firecrawl.search(s.firecrawlKey, webQuery(region))
                .map { RawHit(it.title, it.url, it.description, null) }
                .toListings()
            else -> throw RemoteException("Add a free Reed, Serper or Firecrawl key in Settings to search live jobs.")
        }
    }

    /**
     * The fullest version of the advert we can get, in order: Reed's API, the page's own
     * JobPosting structured data (most boards embed it), Firecrawl, readable page text, and
     * finally the search snippet. [ListingDetails.source] says which one was used.
     */
    suspend fun details(job: JobListing): ListingDetails {
        if (job.isSample) return ListingDetails(job.snippet, "sample listing", job.company)
        val s = settings.current()
        if (job.reedJobId != null && s.reedKey.isNotBlank()) {
            runCatching { reed.details(s.reedKey, job.reedJobId) }.getOrNull()
                ?.takeIf { it.length > 400 }
                ?.let { return ListingDetails(it.take(MAX_TEXT), "full advert (Reed)", job.company) }
        }
        val html = pages.html(job.url)
        html?.let { ListingParser.jobPosting(it) }?.takeIf { it.text.length > 400 }?.let { posting ->
            return posting.copy(text = posting.text.take(MAX_TEXT), company = posting.company ?: job.company)
        }
        if (s.firecrawlKey.isNotBlank()) {
            runCatching { firecrawl.scrape(s.firecrawlKey, job.url, maxChars = MAX_TEXT) }.getOrNull()
                ?.takeIf { it.length > 400 }
                ?.let { return ListingDetails(it, "full advert (Firecrawl)", job.company) }
        }
        html?.let { ListingParser.pageText(it) }?.takeIf { it.length > 600 }?.let {
            return ListingDetails(it.take(MAX_TEXT), "advert page text", job.company)
        }
        return ListingDetails(job.snippet, "search snippet only", job.company)
    }

    suspend fun fullDescription(job: JobListing): String = details(job).text

    /** Web results about the employer (recent projects, sector, values) to make prep specific. */
    suspend fun employerResearch(company: String?, location: String?): List<String> {
        val name = company?.trim()?.takeIf { it.length > 2 && !it.contains("sample", ignoreCase = true) && !it.equals("Not stated", true) }
            ?: return emptyList()
        val key = settings.current().serperKey.takeIf { it.isNotBlank() } ?: return emptyList()
        val query = "\"$name\" construction projects ${location ?: "North West"} news"
        return runCatching { serper.search(key, query, pastWeekOnly = false) }.getOrDefault(emptyList())
            .filter { it.snippet.isNotBlank() }
            .take(6)
            .map { "${it.title}: ${it.snippet}" }
    }
        return full?.takeIf { it.length > job.snippet.length }?.take(12_000) ?: job.snippet
    }

    private suspend fun searchReed(key: String, region: SearchRegion, pastWeekOnly: Boolean): List<JobListing> =
        coroutineScope {
            val perTown = region.towns.map { town ->
                async { runCatching { reed.search(key, KEYWORDS, town, radiusMiles = 15) } }
            }.awaitAll()
            // Surface a bad key or outage instead of a silent empty list.
            if (perTown.all { it.isFailure }) throw perTown.first().exceptionOrNull()!!
            val cutoff = LocalDate.now().minusDays(7)
            perTown.flatMap { it.getOrDefault(emptyList()) }
                .distinctBy { it.jobId }
                .filter { job -> RELEVANT.containsMatchIn(job.jobTitle) }
                .filter { job -> !pastWeekOnly || (parseReedDate(job.date)?.let { !it.isBefore(cutoff) } ?: true) }
                .sortedByDescending { parseReedDate(it.date) }
                .map { it.toListing() }
        }

    private fun ReedJob.toListing() = JobListing(
        id = "reed-$jobId",
        title = jobTitle.trim(),
        url = jobUrl.ifBlank { "https://www.reed.co.uk/jobs/$jobId" },
        snippet = htmlToText(jobDescription),
        source = "Reed",
        company = employerName,
        location = locationName,
        salary = formatSalary(minimumSalary, maximumSalary),
        postedLabel = date?.let { "Posted $it" },
        reedJobId = jobId,
    )

    private fun webQuery(region: SearchRegion) =
        "\"Assistant Site Manager\" ${region.query} " +
            "(site:reed.co.uk OR site:uk.indeed.com OR site:totaljobs.com OR site:cv-library.co.uk)"

    /** Single vacancies first; board search pages are kept (labelled) so results are never empty. */
    private fun List<RawHit>.toListings() = mapNotNull { it.toListing() }
        .distinctBy { it.url }
        .sortedBy { it.isSearchPage }

    private data class RawHit(val title: String, val link: String, val snippet: String, val date: String?)

    private fun RawHit.toListing(): JobListing? {
        val source = when {
            link.contains("reed.co.uk") -> "Reed"
            link.contains("indeed.") -> "Indeed"
            link.contains("totaljobs.com") -> "Totaljobs"
            link.contains("cv-library.co.uk") -> "CV-Library"
            else -> return null
        }
        val text = "$title $snippet"
        return JobListing(
            id = link.hashCode().toUInt().toString(16),
            title = cleanTitle(title),
            url = link,
            snippet = snippet.trim(),
            source = source,
            location = ListingParser.location(text),
            salary = ListingParser.salary(text),
            postedLabel = date,
            isSearchPage = !ListingParser.isSingleVacancy(link),
        )
    }

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("\\s*[-|–]\\s*(reed\\.co\\.uk|Reed|Indeed(\\.com)?).*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^\\d[\\d,]*\\+?\\s+"), "")
        .trim()
        .ifBlank { "Assistant Site Manager" }

    companion object {
        private const val MAX_TEXT = 14_000
        private const val KEYWORDS = "assistant site manager"
        private val RELEVANT = Regex("(?i)site manager|site supervisor|assistant site|construction manager")
        private val REED_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        fun parseReedDate(raw: String?): LocalDate? =
            raw?.let { runCatching { LocalDate.parse(it.trim(), REED_DATE) }.getOrNull() }

        fun formatSalary(min: Double?, max: Double?): String? {
            val gbp = NumberFormat.getCurrencyInstance(Locale.UK).apply { maximumFractionDigits = 0 }
            return when {
                min != null && max != null && max > min -> "${gbp.format(min)} – ${gbp.format(max)}"
                min != null -> gbp.format(min)
                max != null -> gbp.format(max)
                else -> null
            }
        }

        /** Offline examples so every screen is usable before any API key is added. */
        val samples = listOf(
            JobListing(
                id = "sample-1",
                title = "Assistant Site Manager – Residential New Build",
                url = "https://www.reed.co.uk/jobs/assistant-site-manager-jobs-in-north-west",
                snippet = "Sample listing. Housebuilder seeks an ASM for a 120-plot development. SMSTS and " +
                    "First Aid required, CSCS card, experience of managing trades and sub-contractors, " +
                    "build quality and NHBC inspections. Strong IT skills for digital site diaries.",
                source = "Sample",
                company = "Regional housebuilder (sample)",
                location = "Warrington",
                salary = "£40,000 - £46,000",
                isSample = true,
            ),
            JobListing(
                id = "sample-2",
                title = "Assistant Site Manager – Commercial Fit-Out",
                url = "https://uk.indeed.com/q-assistant-site-manager-l-manchester-jobs.html",
                snippet = "Sample listing. Main contractor delivering Cat A/B office fit-outs in Manchester city " +
                    "centre. Coordinate drylining, M&E and finishes packages; manage permits, RAMS review, " +
                    "fire-stopping QA and Procore records. SMSTS essential, HNC/HND preferred.",
                source = "Sample",
                company = "Tier 2 fit-out contractor (sample)",
                location = "Manchester",
                salary = "£45,000 - £52,000 + car allowance",
                isSample = true,
            ),
            JobListing(
                id = "sample-3",
                title = "Assistant Site Manager – Education Refurbishment",
                url = "https://www.reed.co.uk/jobs/assistant-site-manager-jobs-in-liverpool",
                snippet = "Sample listing. Live-environment school refurbishment including roof replacement " +
                    "(single-ply), window replacement and internal remodelling. Safeguarding awareness, " +
                    "phasing around occupants, DBS check. SMSTS required.",
                source = "Sample",
                company = "Framework contractor (sample)",
                location = "Liverpool",
                salary = "£42,000 - £48,000",
                isSample = true,
            ),
        )
    }
}

/** The advert text used to build a role, and where it came from (shown to the user). */
data class ListingDetails(
    val text: String,
    val source: String,
    val company: String? = null,
    val location: String? = null,
    val salary: String? = null,
)

/** Small, testable heuristics for pulling structure out of search-result text. */
object ListingParser {
    private val towns = listOf(
        "Manchester", "Salford", "Stockport", "Bolton", "Wigan", "Oldham", "Rochdale", "Bury", "Trafford",
        "Liverpool", "Wirral", "St Helens", "Knowsley", "Southport", "Birkenhead",
        "Preston", "Blackburn", "Blackpool", "Lancaster", "Burnley", "Chorley",
        "Chester", "Warrington", "Crewe", "Macclesfield", "Runcorn", "Widnes",
        "Carlisle", "Kendal", "Barrow",
    )
    private val salaryRegex =
        Regex("£\\s?\\d[\\d,.]*k?(?:\\s*(?:-|–|to)\\s*£?\\s?\\d[\\d,.]*k?)?(?:\\s*(?:per annum|a year|p\\.?a\\.?))?", RegexOption.IGNORE_CASE)

    data class Vacancy(val title: String, val url: String, val context: String)

    private val ldJson = Regex("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** Reads schema.org JobPosting data, which Reed, Totaljobs, CV-Library and Indeed embed in job pages. */
    fun jobPosting(html: String): ListingDetails? {
        for (m in ldJson.findAll(html)) {
            val root = runCatching { AppJson.parseToJsonElement(m.groupValues[1].trim()) }.getOrNull() ?: continue
            val posting = findPosting(root) ?: continue
            val text = htmlToText(htmlToText(posting.str("description") ?: continue))
            val company = (posting["hiringOrganization"] as? JsonObject)?.str("name")
            val address = ((posting["jobLocation"] as? JsonObject) ?: (posting["jobLocation"] as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.let { it["address"] as? JsonObject }
            val location = address?.str("addressLocality") ?: address?.str("addressRegion")
            val salary = (posting["baseSalary"] as? JsonObject)?.let { base ->
                val value = base["value"] as? JsonObject
                val min = value?.str("minValue") ?: value?.str("value")
                val max = value?.str("maxValue")
                listOfNotNull(min, max).joinToString(" – ") { "£$it" }.ifBlank { null }
            }
            val title = posting.str("title")
            val header = listOfNotNull(title, company, location, salary).joinToString(" | ")
            return ListingDetails("$header\n\n$text", "full advert (job page)", company, location, salary)
        }
        return null
    }

    private fun findPosting(e: JsonElement): JsonObject? = when (e) {
        is JsonObject -> when {
            e.str("@type")?.equals("JobPosting", ignoreCase = true) == true -> e
            e["@graph"] != null -> findPosting(e["@graph"]!!)
            else -> null
        }
        is JsonArray -> e.firstNotNullOfOrNull { findPosting(it) }
        else -> null
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** Readable text of a job page without scripts, styles and navigation. */
    fun pageText(html: String): String = htmlToText(
        html.replace(Regex("<(script|style|nav|header|footer|noscript|svg)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " "),
    )

    private val relevantTitle = Regex("(?i)site|construction|manager|supervisor|foreman|build|assistant")
    private val markdownLink = Regex("\\[([^\\]]{6,160})\\]\\((https?://[^)\\s]+)[^)]*\\)")
    private val htmlLink = Regex("<a\\b[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** Pulls single-vacancy links (with a little following text for location/salary) from page markdown. */
    fun vacanciesFromMarkdown(markdown: String, pageUrl: String): List<Vacancy> =
        markdownLink.findAll(markdown).mapNotNull { m ->
            val title = cleanText(m.groupValues[1])
            val url = absolute(m.groupValues[2], pageUrl)
            val context = cleanText(markdown.substring(m.range.last + 1, minOf(markdown.length, m.range.last + 360)))
            Vacancy(title, url, context).takeIf { isSingleVacancy(url) && relevantTitle.containsMatchIn(title) && !title.startsWith("!") }
        }.toList()

    /** Same, for raw HTML (Reed's server-rendered results). */
    fun vacanciesFromHtml(html: String, pageUrl: String): List<Vacancy> =
        htmlLink.findAll(html).mapNotNull { m ->
            val url = absolute(m.groupValues[1].replace("&amp;", "&"), pageUrl)
            val title = cleanText(m.groupValues[2].replace(Regex("<[^>]+>"), " "))
            val context = cleanText(html.substring(m.range.last + 1, minOf(html.length, m.range.last + 1200)).replace(Regex("<[^>]+>"), " "))
                .take(300)
            Vacancy(title, url, context).takeIf { title.length in 6..160 && isSingleVacancy(url) && relevantTitle.containsMatchIn(title) }
        }.toList()

    private fun absolute(url: String, pageUrl: String): String = when {
        url.startsWith("http") -> url
        url.startsWith("/") -> Regex("^(https?://[^/]+)").find(pageUrl)?.value.orEmpty() + url
        else -> url
    }

    private fun cleanText(raw: String): String = raw
        .replace(Regex("[*_#>`|\\[\\]]"), " ")
        .replace(Regex("\\(https?://[^)]*\\)"), " ")
        .replace(Regex("&[a-z]+;"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** True for a single job advert URL; false for a board's search or category page. */
    fun isSingleVacancy(url: String): Boolean {
        val u = url.lowercase()
        return when {
            "indeed." in u -> "viewjob" in u || "jk=" in u || "/rc/clk" in u || "/pagead/" in u
            "reed.co.uk" in u -> Regex("/jobs/[^/?#]+/\\d{6,}").containsMatchIn(u)
            "totaljobs.com" in u -> Regex("/job/.+job\\d+").containsMatchIn(u)
            "cv-library.co.uk" in u -> Regex("/job/\\d+").containsMatchIn(u)
            else -> true
        }
    }

    fun salary(text: String): String? = salaryRegex.find(text)?.value?.trim()

    fun location(text: String): String? = towns.firstOrNull { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(text) }
}
