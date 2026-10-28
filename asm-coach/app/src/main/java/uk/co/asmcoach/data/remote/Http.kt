package uk.co.asmcoach.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import uk.co.asmcoach.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    /**
     * Runs [request] on OkHttp's own threads. Unlike a blocking execute(), cancelling the
     * coroutine (e.g. a withTimeout) cancels the HTTP call, so time limits really hold.
     */
    private suspend fun send(client: OkHttpClient, request: Request, serviceName: String): Pair<Int, String> =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    try {
                        cont.resume(response.use { it.code to (it.body?.string().orEmpty()) })
                    } catch (e: IOException) {
                        onFailure(call, e)
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(RemoteException("$serviceName is unreachable. Check your connection and try again.", e))
                    }
                }
            })
        }

    /** GETs a URL and returns (status code, body text). Cancellable. */
    suspend fun get(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        serviceName: String,
    ): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return send(client, request, serviceName)
    }

    /** POSTs a JSON body and returns (status code, body text). Cancellable. */
    suspend fun postJson(
        client: OkHttpClient,
        url: String,
        body: String,
        headers: Map<String, String>,
        serviceName: String,
    ): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return send(client, request, serviceName)
    }
}
