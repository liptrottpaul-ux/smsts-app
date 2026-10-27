package uk.co.asmcoach.data.repo

import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.SearchRegion
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import uk.co.asmcoach.data.remote.FirecrawlClient
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
) {
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

    /** Full listing text: Reed's job API, else Firecrawl, else the search snippet. */
    suspend fun fullDescription(job: JobListing): String {
        if (job.isSample) return job.snippet
        val s = settings.current()
        val full = when {
            job.reedJobId != null && s.reedKey.isNotBlank() ->
                runCatching { reed.details(s.reedKey, job.reedJobId) }.getOrNull()
            s.firecrawlKey.isNotBlank() -> runCatching { firecrawl.scrape(s.firecrawlKey, job.url) }.getOrNull()
            else -> null
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
