package uk.co.asmcoach.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
data class ReedSearchResponse(val results: List<ReedJob> = emptyList(), val totalResults: Int = 0)

@Serializable
data class ReedJob(
    val jobId: Long = 0,
    val employerName: String? = null,
    val jobTitle: String = "",
    val locationName: String? = null,
    val minimumSalary: Double? = null,
    val maximumSalary: Double? = null,
    val date: String? = null,
    val jobDescription: String = "",
    val jobUrl: String = "",
)

/**
 * Reed.co.uk Jobseeker API (free key from https://www.reed.co.uk/developers/jobseeker).
 * Auth is HTTP Basic with the API key as the username and an empty password.
 */
class ReedClient(private val http: OkHttpClient) {
    private val json = AppJson

    suspend fun search(apiKey: String, keywords: String, location: String, radiusMiles: Int): List<ReedJob> {
        val url = BASE.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("keywords", keywords)
            .addQueryParameter("locationName", location)
            .addQueryParameter("distanceFromLocation", radiusMiles.toString())
            .addQueryParameter("resultsToTake", "50")
            .build()
            .toString()
        val text = call(apiKey, url)
        return decode(ReedSearchResponse.serializer(), text).results
    }

    /** Full job description (HTML stripped). */
    suspend fun details(apiKey: String, jobId: Long): String {
        val text = call(apiKey, "$BASE/jobs/$jobId")
        return htmlToText(decode(ReedJob.serializer(), text).jobDescription)
    }

    private suspend fun call(apiKey: String, url: String): String {
        val (code, text) = Http.get(
            client = http,
            url = url,
            headers = mapOf("Authorization" to Credentials.basic(apiKey, "")),
            serviceName = "Reed",
        )
        when (code) {
            in 200..299 -> Unit
            401, 403 -> throw RemoteException("Reed rejected your API key. Check it in Settings.")
            429 -> throw RemoteException("Reed rate limit reached. Wait a minute and try again.")
            else -> throw RemoteException("Reed search failed ($code).")
        }
        return text
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, text: String): T = try {
        json.decodeFromString(serializer, text)
    } catch (e: SerializationException) {
        throw RemoteException("Reed returned an unreadable response.", e)
    }

    private companion object {
        const val BASE = "https://www.reed.co.uk/api/1.0"
    }
}

/** Minimal HTML-to-text for job descriptions, keeping paragraph and list breaks. */
fun htmlToText(html: String): String = html
    .replace(Regex("(?i)<br\\s*/?>|</p>|</li>|</h\\d>"), "\n")
    .replace(Regex("(?i)<li[^>]*>"), "• ")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&pound;", "£")
    .replace("&#163;", "£")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("[ \\t]+"), " ")
    .replace(Regex("\\n\\s*\\n+"), "\n\n")
    .trim()
