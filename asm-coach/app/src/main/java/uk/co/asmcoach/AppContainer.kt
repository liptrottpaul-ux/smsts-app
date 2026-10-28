package uk.co.asmcoach

import android.content.Context
import java.util.concurrent.TimeUnit
import uk.co.asmcoach.data.agent.CoachAgent
import uk.co.asmcoach.data.agent.InterviewAgent
import uk.co.asmcoach.data.agent.RolePackAgent
import uk.co.asmcoach.data.agent.StrategyAgent
import uk.co.asmcoach.data.persist.FirestoreSync
import uk.co.asmcoach.data.persist.PracticeStore
import uk.co.asmcoach.data.persist.SessionRepository
import uk.co.asmcoach.data.remote.FirecrawlClient
import uk.co.asmcoach.data.remote.GeminiClient
import uk.co.asmcoach.data.remote.Http
import uk.co.asmcoach.data.remote.PageFetcher
import uk.co.asmcoach.data.remote.ReedClient
import uk.co.asmcoach.data.remote.SerperClient
import uk.co.asmcoach.data.remote.UpdateChecker
import uk.co.asmcoach.data.repo.JobRepository
import uk.co.asmcoach.data.repo.RoleRepository
import uk.co.asmcoach.data.settings.SettingsRepository
import uk.co.asmcoach.voice.TextToSpeechManager

/** Manual dependency graph: small enough that a DI framework would only add build time. */
class AppContainer(val app: Context) {
    private val http = Http.createClient()

    val settings = SettingsRepository(app)
    val gemini = GeminiClient(http, settings)
    val jobs = JobRepository(
        ReedClient(http),
        SerperClient(http),
        FirecrawlClient(http.newBuilder().callTimeout(45, TimeUnit.SECONDS).build()),
        settings,
        // Job pages must answer fast or be skipped: one slow board shouldn't stall preparing a role.
        PageFetcher(http.newBuilder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()),
    )
    val strategyAgent = StrategyAgent(gemini)
    val coachAgent = CoachAgent(gemini)
    val interviewAgent = InterviewAgent(gemini)
    val sessions = SessionRepository(app, FirestoreSync.createIfAvailable(app))
    val tts = TextToSpeechManager(app)
    val roles = RoleRepository(app, jobs, strategyAgent, RolePackAgent(gemini), settings, sessions)
    val updates = UpdateChecker(http)
    val practice = PracticeStore(app)
}
