package uk.co.asmcoach

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.asmcoach.data.agent.CoachAgent
import uk.co.asmcoach.data.agent.InterviewAgent
import uk.co.asmcoach.data.agent.StrategyAgent
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.StrategyBrief
import uk.co.asmcoach.data.persist.FirestoreSync
import uk.co.asmcoach.data.persist.SessionRepository
import uk.co.asmcoach.data.remote.FirecrawlClient
import uk.co.asmcoach.data.remote.GeminiClient
import uk.co.asmcoach.data.remote.Http
import uk.co.asmcoach.data.remote.ReedClient
import uk.co.asmcoach.data.remote.SerperClient
import uk.co.asmcoach.data.remote.UpdateChecker
import uk.co.asmcoach.data.repo.JobRepository
import uk.co.asmcoach.data.settings.SettingsRepository
import uk.co.asmcoach.voice.TextToSpeechManager

/** Manual dependency graph: small enough that a DI framework would only add build time. */
class AppContainer(val app: Context) {
    private val http = Http.createClient()

    val settings = SettingsRepository(app)
    val gemini = GeminiClient(http, settings)
    val jobs = JobRepository(ReedClient(http), SerperClient(http), FirecrawlClient(http), settings)
    val strategyAgent = StrategyAgent(gemini)
    val coachAgent = CoachAgent(gemini)
    val interviewAgent = InterviewAgent(gemini)
    val sessions = SessionRepository(app, FirestoreSync.createIfAvailable(app))
    val tts = TextToSpeechManager(app)
    val selection = SelectionStore()
    val updates = UpdateChecker(http)
}

/** The job chosen in Tab 1, shared with the strategy screen, masterclasses and the roleplay. */
class SelectionStore {
    private val _job = MutableStateFlow<JobListing?>(null)
    val job: StateFlow<JobListing?> = _job.asStateFlow()

    private val _briefs = MutableStateFlow<Map<String, StrategyBrief>>(emptyMap())
    val briefs: StateFlow<Map<String, StrategyBrief>> = _briefs.asStateFlow()

    fun select(job: JobListing) {
        _job.value = job
    }

    fun putBrief(jobId: String, brief: StrategyBrief) {
        _briefs.update { it + (jobId to brief) }
    }
}
