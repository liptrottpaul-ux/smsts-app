package uk.co.asmcoach.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.asmcoach.BuildConfig
import uk.co.asmcoach.data.model.CandidateProfile
import java.util.UUID

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val geminiKey: String,
    val serperKey: String,
    val firecrawlKey: String,
    val geminiModel: String,
    val candidateName: String,
    val profileNotes: String,
    val speechRate: Float,
    val deviceId: String,
) {
    val profile: CandidateProfile get() = CandidateProfile(name = candidateName, extraNotes = profileNotes)
    val hasGemini: Boolean get() = geminiKey.isNotBlank()
    val hasJobSearch: Boolean get() = serperKey.isNotBlank() || firecrawlKey.isNotBlank()
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val gemini = stringPreferencesKey("gemini_key")
        val serper = stringPreferencesKey("serper_key")
        val firecrawl = stringPreferencesKey("firecrawl_key")
        val model = stringPreferencesKey("gemini_model")
        val name = stringPreferencesKey("candidate_name")
        val notes = stringPreferencesKey("profile_notes")
        val rate = floatPreferencesKey("speech_rate")
        val device = stringPreferencesKey("device_id")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            geminiKey = p[Keys.gemini].orEmpty().ifBlank { BuildConfig.GEMINI_API_KEY },
            serperKey = p[Keys.serper].orEmpty().ifBlank { BuildConfig.SERPER_API_KEY },
            firecrawlKey = p[Keys.firecrawl].orEmpty().ifBlank { BuildConfig.FIRECRAWL_API_KEY },
            geminiModel = p[Keys.model].orEmpty().ifBlank { DEFAULT_MODEL },
            candidateName = p[Keys.name].orEmpty().ifBlank { "Paul" },
            profileNotes = p[Keys.notes].orEmpty(),
            speechRate = p[Keys.rate] ?: 1.0f,
            deviceId = p[Keys.device].orEmpty(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** A stable, random per-install id (never derived from hardware identifiers). */
    suspend fun deviceId(): String {
        val existing = current().deviceId
        if (existing.isNotBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        context.settingsStore.edit { it[Keys.device] = fresh }
        return fresh
    }

    suspend fun update(
        geminiKey: String,
        serperKey: String,
        firecrawlKey: String,
        geminiModel: String,
        candidateName: String,
        profileNotes: String,
        speechRate: Float,
    ) {
        context.settingsStore.edit {
            it[Keys.gemini] = geminiKey.trim()
            it[Keys.serper] = serperKey.trim()
            it[Keys.firecrawl] = firecrawlKey.trim()
            it[Keys.model] = geminiModel.trim()
            it[Keys.name] = candidateName.trim()
            it[Keys.notes] = profileNotes.trim()
            it[Keys.rate] = speechRate
        }
    }

    companion object {
        /**
         * Gemini 1.5 Flash has been retired by Google, so the default is its free-tier successor.
         * Any model id can be set in Settings.
         */
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        val SUGGESTED_MODELS = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-1.5-flash")
    }
}
