package uk.co.asmcoach.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient

@Serializable
data class SerperRequest(
    val q: String,
    val gl: String = "uk",
    val hl: String = "en",
    val num: Int = 20,
    val tbs: String? = null,
)

@Serializable
data class SerperResponse(val organic: List<SerperOrganic> = emptyList())

@Serializable
data class SerperOrganic(
    val title: String = "",
    val link: String = "",
    val snippet: String = "",
    val date: String? = null,
)

/** Serper.dev Google Search API (free tier). https://serper.dev */
class SerperClient(private val http: OkHttpClient) {
    private val json = AppJson

    suspend fun search(apiKey: String, query: String, pastWeekOnly: Boolean): List<SerperOrganic> {
        val body = json.encodeToString(
            SerperRequest.serializer(),
            SerperRequest(q = query, tbs = if (pastWeekOnly) "qdr:w" else null),
        )
        val (code, text) = Http.postJson(
            client = http,
            url = "https://google.serper.dev/search",
            body = body,
            headers = mapOf("X-API-KEY" to apiKey),
            serviceName = "Serper",
        )
        when (code) {
            in 200..299 -> Unit
            401, 403 -> throw RemoteException("Serper rejected your API key. Check it in Settings.")
            429 -> throw RemoteException("Serper free quota reached. Try Firecrawl or wait.")
            else -> throw RemoteException("Serper search failed ($code).")
        }
        return try {
            json.decodeFromString(SerperResponse.serializer(), text).organic
        } catch (e: SerializationException) {
            throw RemoteException("Serper returned an unreadable response.", e)
        }
    }
}

@Serializable
data class FirecrawlSearchRequest(val query: String, val limit: Int = 15)

@Serializable
data class FirecrawlSearchResponse(
    val success: Boolean = false,
    val data: List<FirecrawlSearchItem> = emptyList(),
    val error: String? = null,
)

@Serializable
data class FirecrawlSearchItem(
    val url: String = "",
    val title: String = "",
    val description: String = "",
)

@Serializable
data class FirecrawlScrapeRequest(
    val url: String,
    val formats: List<String> = listOf("markdown"),
    val onlyMainContent: Boolean = true,
)

@Serializable
data class FirecrawlScrapeResponse(
    val success: Boolean = false,
    val data: FirecrawlScrapeData? = null,
    val error: String? = null,
)

@Serializable
data class FirecrawlScrapeData(val markdown: String = "")

/** Firecrawl search + scrape API (free credits). https://docs.firecrawl.dev */
class FirecrawlClient(private val http: OkHttpClient) {
    private val json = AppJson

    suspend fun search(apiKey: String, query: String): List<FirecrawlSearchItem> {
        val (code, text) = Http.postJson(
            client = http,
            url = "$BASE/search",
            body = json.encodeToString(FirecrawlSearchRequest.serializer(), FirecrawlSearchRequest(query)),
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            serviceName = "Firecrawl",
        )
        checkStatus(code)
        val parsed = decode(FirecrawlSearchResponse.serializer(), text)
        if (!parsed.success) throw RemoteException(parsed.error ?: "Firecrawl search failed.")
        return parsed.data
    }

    /** Returns the page's main content as Markdown, trimmed to keep prompts inside free-tier limits. */
    suspend fun scrape(apiKey: String, url: String): String {
        val (code, text) = Http.postJson(
            client = http,
            url = "$BASE/scrape",
            body = json.encodeToString(FirecrawlScrapeRequest.serializer(), FirecrawlScrapeRequest(url)),
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            serviceName = "Firecrawl",
        )
        checkStatus(code)
        val parsed = decode(FirecrawlScrapeResponse.serializer(), text)
        if (!parsed.success) throw RemoteException(parsed.error ?: "Firecrawl could not read that page.")
        return parsed.data?.markdown.orEmpty().take(MAX_MARKDOWN)
    }

    private fun checkStatus(code: Int) = when (code) {
        in 200..299 -> Unit
        401, 403 -> throw RemoteException("Firecrawl rejected your API key. Check it in Settings.")
        402, 429 -> throw RemoteException("Firecrawl free credits or rate limit reached.")
        else -> throw RemoteException("Firecrawl request failed ($code).")
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, text: String): T = try {
        json.decodeFromString(serializer, text)
    } catch (e: SerializationException) {
        throw RemoteException("Firecrawl returned an unreadable response.", e)
    }

    private companion object {
        const val BASE = "https://api.firecrawl.dev/v1"
        const val MAX_MARKDOWN = 12_000
    }
}
