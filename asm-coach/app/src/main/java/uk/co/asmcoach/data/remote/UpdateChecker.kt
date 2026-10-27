package uk.co.asmcoach.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import uk.co.asmcoach.BuildConfig

@Serializable
data class GitHubRelease(val tag_name: String = "")

/**
 * Compares this install's CI build number with the newest GitHub release. Updating is a
 * browser download of the APK, which Android installs over the current app.
 */
class UpdateChecker(private val http: OkHttpClient) {
    private val _latestBuild = MutableStateFlow<Int?>(null)
    val latestBuild: StateFlow<Int?> = _latestBuild.asStateFlow()

    val currentBuild: Int = BuildConfig.VERSION_CODE

    val updateAvailable: Boolean get() = (_latestBuild.value ?: 0) > currentBuild

    /** Returns the newest published build number, or throws [RemoteException]. */
    suspend fun check(): Int {
        val (code, text) = Http.get(
            client = http,
            url = "https://api.github.com/repos/$REPO/releases/latest",
            headers = mapOf("Accept" to "application/vnd.github+json"),
            serviceName = "GitHub",
        )
        if (code !in 200..299) throw RemoteException("Couldn't check for updates ($code).")
        val tag = runCatching { AppJson.decodeFromString(GitHubRelease.serializer(), text).tag_name }.getOrDefault("")
        val build = buildNumber(tag) ?: throw RemoteException("Couldn't read the latest version.")
        _latestBuild.value = build
        return build
    }

    companion object {
        private const val REPO = "liptrottpaul-ux/smsts-app"
        const val DOWNLOAD_URL = "https://github.com/$REPO/releases/latest/download/asm-coach.apk"

        fun buildNumber(tag: String): Int? = Regex("asm-coach-build-(\\d+)").find(tag)?.groupValues?.get(1)?.toIntOrNull()
    }
}
