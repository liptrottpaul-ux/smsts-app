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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
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
import uk.co.asmcoach.data.content.PanelFactory
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
     * The panel is created instantly on the device; reading the advert is capped at ~12s and runs
     * alongside employer research; then every AI part (topics, question batches, scenarios, brief)
     * starts at once and is saved as it lands, so Prep fills in progressively.
     */
    fun prepare(job: JobListing, force: Boolean = false) {
        if (running[job.id]?.isActive == true) return
        if (!force && isReady(job.id)) return
        running[job.id] = scope.launch {
            loaded.join()
            if (force) _packs.update { it - job.id }
            val needBrief = force || job.id !in _briefs.value

            // 1. Instant: a realistic panel for this employer type, so Interview is usable immediately.
            if (_packs.value[job.id] == null) {
                val panel = PanelFactory.panelFor(job.id, "${job.title} ${job.snippet} ${job.company.orEmpty()}")
                _packs.update { it + (job.id to RolePack(panel = panel)) }
                persist()
            }
            val panel = _packs.value[job.id]?.panel.orEmpty()

            // 2. The advert (capped) and employer research, in parallel.
            setProgress(job.id, PrepProgress("Reading the job advert…"))
            val (details, research) = coroutineScope {
                val d = async {
                    withTimeoutOrNull(ADVERT_TIMEOUT_MS) { runCatching { jobs.details(job) }.getOrNull() }
                        ?: ListingDetails(job.snippet, "search snippet only", job.company)
                }
                val r = async {
                    withTimeoutOrNull(RESEARCH_TIMEOUT_MS) {
                        runCatching { jobs.employerResearch(job.company, job.location) }.getOrNull()
                    }.orEmpty()
                }
                d.await() to r.await()
            }
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
            _packs.update { m -> m[job.id]?.let { p -> m + (job.id to p.copy(basedOn = basedOn, thinSource = thin)) } ?: m }

            // 3. Every AI part at once, four at a time, each merged in as it lands.
            val profile = settings.current().profile
            val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
            val limiter = Semaphore(4)
            val existing = _packs.value[job.id]
            val haveCategories = existing?.questions.orEmpty().map { it.category }.toSet()
            val batches = QuestionCategory.PACK.filter { it !in haveCategories }.chunked(2)
            val needTopics = existing?.prepTopics.isNullOrEmpty()
            val needScenarios = existing?.scenarios.isNullOrEmpty()
            val total = batches.size + (if (needTopics) 1 else 0) + (if (needScenarios) 1 else 0) + (if (needBrief) 1 else 0)
            val done = java.util.concurrent.atomic.AtomicInteger()
            fun tick() = setProgress(job.id, PrepProgress("Writing your prep… ${done.incrementAndGet()} of $total parts ready"))
            fun fail(e: Throwable, what: String) {
                if (e is CancellationException) throw e
                errors += e.message ?: "$what failed"
            }
            fun merge(transform: (RolePack) -> RolePack) =
                _packs.update { m -> m[job.id]?.let { p -> m + (job.id to transform(p)) } ?: m }

            setProgress(job.id, PrepProgress("Writing your prep… 0 of $total parts ready"))
            supervisorScope {
                val jobsToRun = buildList {
                    if (needTopics) add(async {
                        runCatching { limiter.withPermit { packAgent.buildCore(job, description, profile) } }
                            .onSuccess { core -> merge { it.copy(roleSummary = core.roleSummary, prepTopics = core.prepTopics) } }
                            .onFailure { fail(it, "Prep topics") }
                        tick()
                    })
                    batches.forEach { cats ->
                        add(async {
                            runCatching { limiter.withPermit { packAgent.buildQuestions(job, description, profile, panel, cats) } }
                                .onSuccess { qs -> merge { it.copy(questions = it.questions + qs) } }
                                .onFailure { fail(it, "Questions") }
                            tick()
                        })
                    }
                    if (needScenarios) add(async {
                        runCatching { limiter.withPermit { packAgent.buildScenarios(job, description, profile, panel) } }
                            .onSuccess { sc -> merge { it.copy(scenarios = sc) } }
                            .onFailure { fail(it, "Scenarios") }
                        tick()
                    })
                    if (needBrief) add(async {
                        runCatching { limiter.withPermit { strategyAgent.analyse(job, description, profile) } }
                            .onSuccess { b ->
                                _briefs.update { it + (job.id to b) }
                                saveBriefRecord(job, b)
                            }
                            .onFailure { fail(it, "Brief") }
                        tick()
                    })
                }
                jobsToRun.awaitAll()
            }
            merge { p ->
                val categories = p.questions.map { it.category }.toSet()
                p.copy(complete = QuestionCategory.PACK.all { it in categories } && p.scenarios.isNotEmpty() && p.prepTopics.isNotEmpty())
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
        const val ADVERT_TIMEOUT_MS = 12_000L
        const val RESEARCH_TIMEOUT_MS = 8_000L
    }
}
