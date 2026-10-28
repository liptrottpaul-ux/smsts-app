package uk.co.asmcoach.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import uk.co.asmcoach.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** A user-presentable failure from a remote service or agent. */
class RemoteException(message: String, cause: Throwable? = null) : Exception(message, cause)

object Http {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun createClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("x-goog-api-key")
                        redactHeader("X-API-KEY")
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    /** GETs a URL and returns (status code, body text). Runs on the IO dispatcher. */
    suspend fun get(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        serviceName: String,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                response.code to (response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            throw RemoteException("$serviceName is unreachable. Check your connection and try again.", e)
        }
    }

    /** POSTs a JSON body and returns (status code, body text). Runs on the IO dispatcher. */
    suspend fun postJson(
        client: OkHttpClient,
        url: String,
        body: String,
        headers: Map<String, String>,
        serviceName: String,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                response.code to (response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            throw RemoteException("$serviceName is unreachable. Check your connection and try again.", e)
        }
    }
}
