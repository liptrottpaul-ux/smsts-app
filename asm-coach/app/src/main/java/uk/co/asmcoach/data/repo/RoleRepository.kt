package uk.co.asmcoach.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import uk.co.asmcoach.data.agent.RolePackAgent
import uk.co.asmcoach.data.agent.StrategyAgent
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.QuestionCategory
import uk.co.asmcoach.data.model.RolePack
import uk.co.asmcoach.data.model.SessionKind
import uk.co.asmcoach.data.model.SessionRecord
import uk.co.asmcoach.data.model.StrategyBrief
import uk.co.asmcoach.data.persist.SessionRepository
import uk.co.asmcoach.data.remote.AppJson
import uk.co.asmcoach.data.settings.SettingsRepository

private val Context.rolesStore by preferencesDataStore(name = "roles")

@Serializable
private data class SavedRoles(
    val selected: JobListing? = null,
    val briefs: Map<String, StrategyBrief> = emptyMap(),
    val packs: Map<String, RolePack> = emptyMap(),
)

/** Progress of preparing one role; [stage] is null when idle. */
data class PrepProgress(val stage: String? = null, val error: String? = null) {
    val running: Boolean get() = stage != null
}

/**
 * The selected vacancy and everything generated for it. "Preparing" a role fetches the full
 * listing once, then builds the strategy brief and the role pack (prep + interview bank) in
 * parallel. Results are saved on the device so every tab opens instantly afterwards.
 */
class RoleRepository(
    private val context: Context,
    private val jobs: JobRepository,
    private val strategyAgent: StrategyAgent,
    private val packAgent: RolePackAgent,
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val key = stringPreferencesKey("roles")
    private val running = mutableMapOf<String, Job>()

    private val _job = MutableStateFlow<JobListing?>(null)
    val job: StateFlow<JobListing?> = _job.asStateFlow()

    private val _briefs = MutableStateFlow<Map<String, StrategyBrief>>(emptyMap())
    val briefs: StateFlow<Map<String, StrategyBrief>> = _briefs.asStateFlow()

    private val _packs = MutableStateFlow<Map<String, RolePack>>(emptyMap())
    val packs: StateFlow<Map<String, RolePack>> = _packs.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, PrepProgress>>(emptyMap())
    val progress: StateFlow<Map<String, PrepProgress>> = _progress.asStateFlow()

    private val loaded = scope.launch {
        val raw = context.rolesStore.data.first()[key] ?: return@launch
        val saved = runCatching { AppJson.decodeFromString(SavedRoles.serializer(), raw) }.getOrNull() ?: return@launch
        // Anything set during start-up wins over the saved copy.
        if (_job.value == null) _job.value = saved.selected
        _briefs.update { saved.briefs + it }
        _packs.update { saved.packs + it }
    }

    fun select(job: JobListing) {
        _job.value = job
        persist()
    }

    fun isReady(jobId: String): Boolean = jobId in _briefs.value && _packs.value[jobId]?.complete == true

    /**
     * Builds whatever is missing for [job] (everything when [force]). Safe to call repeatedly.
     * Parts are written in parallel and saved as each arrives, so Prep and Interview can start
     * on the first content within seconds instead of waiting for one large reply.
     */
    fun prepare(job: JobListing, force: Boolean = false) {
        if (running[job.id]?.isActive == true) return
        if (!force && isReady(job.id)) return
        running[job.id] = scope.launch {
            loaded.join()
            if (force) _packs.update { it - job.id }
            val needBrief = force || job.id !in _briefs.value
            setProgress(job.id, PrepProgress("Reading the job advert…"))
            val details = runCatching { jobs.details(job) }.getOrNull() ?: ListingDetails(job.snippet, "search snippet only", job.company)
            val research = runCatching { jobs.employerResearch(details.company ?: job.company, details.location ?: job.location) }
                .getOrDefault(emptyList())
            val description = buildString {
                append(details.text)
                if (research.isNotEmpty()) {
                    append("\n\nEMPLOYER RESEARCH (recent web results about ${details.company ?: job.company}):\n")
                    research.forEach { append("- ").append(it).append('\n') }
                }
            }
            val words = details.text.split(Regex("\\s+")).size
            val basedOn = listOfNotNull(
                details.source,
                "%,d words".format(words),
                research.size.takeIf { it > 0 }?.let { "$it employer sources" },
            ).joinToString(" · ")
            val thin = details.source == "search snippet only" || words < 120
            val profile = settings.current().profile
            val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
            val limiter = Semaphore(3)

            fun fail(e: Throwable, what: String) {
                if (e is CancellationException) throw e
                errors += e.message ?: "$what failed"
            }

            supervisorScope {
                val brief = if (needBrief) {
                    async { limiter.withPermit { strategyAgent.analyse(job, description, profile) } }
                } else {
                    null
                }

                // 1. Core: panel, summary and prep topics, the first thing the user sees.
                setProgress(job.id, PrepProgress("Building your panel and prep topics…"))
                var pack = _packs.value[job.id]
                if (pack == null || pack.panel.isEmpty()) {
                    runCatching { limiter.withPermit { packAgent.buildCore(job, description, profile) } }
                        .onSuccess { core ->
                            pack = RolePack(
                                roleSummary = core.roleSummary, panel = core.panel, prepTopics = core.prepTopics,
                                basedOn = basedOn, thinSource = thin,
                            )
                            _packs.update { it + (job.id to pack!!) }
                            persist()
                        }
                        .onFailure { fail(it, "Prep") }
                }
                val panel = pack?.panel.orEmpty()

                // 2. Question batches and scenarios in parallel, merged in as each lands.
                if (panel.isNotEmpty()) {
                    val have = pack?.questions.orEmpty().map { it.category }.toSet()
                    val batches = QuestionCategory.PACK.filter { it !in have }.chunked(2)
                    val done = java.util.concurrent.atomic.AtomicInteger()
                    val total = batches.size + if (pack?.scenarios.isNullOrEmpty()) 1 else 0
                    setProgress(job.id, PrepProgress("Writing interview questions… 0 of $total parts"))
                    val parts = batches.map { cats ->
                        async {
                            runCatching { limiter.withPermit { packAgent.buildQuestions(job, description, profile, panel, cats) } }
                                .onSuccess { qs ->
                                    _packs.update { m -> m[job.id]?.let { p -> m + (job.id to p.copy(questions = p.questions + qs)) } ?: m }
                                }
                                .onFailure { fail(it, "Questions") }
                            setProgress(job.id, PrepProgress("Writing interview questions… ${done.incrementAndGet()} of $total parts"))
                        }
                    } + listOfNotNull(
                        if (pack?.scenarios.isNullOrEmpty()) {
                            async {
                                runCatching { limiter.withPermit { packAgent.buildScenarios(job, description, profile, panel) } }
                                    .onSuccess { sc -> _packs.update { m -> m[job.id]?.let { p -> m + (job.id to p.copy(scenarios = sc)) } ?: m } }
                                    .onFailure { fail(it, "Scenarios") }
                                setProgress(job.id, PrepProgress("Writing interview questions… ${done.incrementAndGet()} of $total parts"))
                            }
                        } else {
                            null
                        },
                    )
                    parts.awaitAll()
                }

                brief?.let { deferred ->
                    runCatching { deferred.await() }
                        .onSuccess { b ->
                            _briefs.update { it + (job.id to b) }
                            saveBriefRecord(job, b)
                        }
                        .onFailure { fail(it, "Brief") }
                }
            }
            _packs.update { m ->
                m[job.id]?.let { p ->
                    val categories = p.questions.map { it.category }.toSet()
                    m + (job.id to p.copy(complete = QuestionCategory.PACK.all { it in categories } && p.scenarios.isNotEmpty()))
                } ?: m
            }
            persist()
            setProgress(job.id, PrepProgress(error = errors.distinct().joinToString(" ").ifBlank { null }))
        }
    }

    fun clearError(jobId: String) = setProgress(jobId, PrepProgress())

    private fun setProgress(jobId: String, p: PrepProgress) = _progress.update { it + (jobId to p) }

    private fun persist() {
        scope.launch {
            loaded.join()
            // Keep storage bounded: the selected role plus the most recently prepared others.
            val recent = (_briefs.value.keys + _packs.value.keys).toList().distinct().takeLast(MAX_ROLES)
            val keep = (recent + listOfNotNull(_job.value?.id)).toSet()
            val saved = SavedRoles(
                selected = _job.value,
                briefs = _briefs.value.filterKeys { it in keep },
                packs = _packs.value.filterKeys { it in keep },
            )
            context.rolesStore.edit { it[key] = AppJson.encodeToString(SavedRoles.serializer(), saved) }
        }
    }

    private suspend fun saveBriefRecord(job: JobListing, brief: StrategyBrief) = sessions.save(
        SessionRecord(
            id = "brief-${job.id}",
            kind = SessionKind.StrategyBrief,
            title = job.title,
            subtitle = listOfNotNull(brief.company.takeIf { it.isNotBlank() }, job.location).joinToString(" · "),
            score = brief.compatibilityScore,
            createdAt = System.currentTimeMillis(),
            payload = AppJson.encodeToString(StrategyBrief.serializer(), brief),
        ),
    )

    private companion object {
        const val MAX_ROLES = 12
    }
}
