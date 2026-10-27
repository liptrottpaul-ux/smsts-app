package uk.co.asmcoach.data.repo

import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.SearchRegion
import uk.co.asmcoach.data.remote.FirecrawlClient
import uk.co.asmcoach.data.remote.RemoteException
import uk.co.asmcoach.data.remote.SerperClient
import uk.co.asmcoach.data.settings.SettingsRepository

/**
 * Finds live Assistant Site Manager listings on Reed and Indeed. Serper (Google results) is
 * preferred for breadth; Firecrawl is the fallback and is also used to read full job pages.
 */
class JobRepository(
    private val serper: SerperClient,
    private val firecrawl: FirecrawlClient,
    private val settings: SettingsRepository,
) {
    suspend fun search(region: SearchRegion, pastWeekOnly: Boolean): List<JobListing> {
        val s = settings.current()
        val query = "\"Assistant Site Manager\" ${region.query} (site:reed.co.uk OR site:uk.indeed.com)"
        val raw: List<RawHit> = when {
            s.serperKey.isNotBlank() -> serper.search(s.serperKey, query, pastWeekOnly)
                .map { RawHit(it.title, it.link, it.snippet, it.date) }
            s.firecrawlKey.isNotBlank() -> firecrawl.search(s.firecrawlKey, query)
                .map { RawHit(it.title, it.url, it.description, null) }
            else -> throw RemoteException("Add a free Serper or Firecrawl key in Settings to search live jobs.")
        }
        return raw.mapNotNull { it.toListing() }.distinctBy { it.url }
    }

    /** Full listing text via Firecrawl when available; otherwise the search snippet. */
    suspend fun fullDescription(job: JobListing): String {
        if (job.isSample) return job.snippet
        val key = settings.current().firecrawlKey
        if (key.isBlank()) return job.snippet
        return runCatching { firecrawl.scrape(key, job.url) }
            .getOrNull()
            ?.takeIf { it.length > job.snippet.length }
            ?: job.snippet
    }

    private data class RawHit(val title: String, val link: String, val snippet: String, val date: String?)

    private fun RawHit.toListing(): JobListing? {
        val source = when {
            link.contains("reed.co.uk") -> "Reed"
            link.contains("indeed.") -> "Indeed"
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
        )
    }

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("\\s*[-|–]\\s*(reed\\.co\\.uk|Reed|Indeed(\\.com)?).*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^\\d[\\d,]*\\+?\\s+"), "")
        .trim()
        .ifBlank { "Assistant Site Manager" }

    companion object {
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

    fun salary(text: String): String? = salaryRegex.find(text)?.value?.trim()

    fun location(text: String): String? = towns.firstOrNull { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(text) }
}
