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
import kotlinx.coroutines.TimeoutCancellationException
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
import uk.co.asmcoach.data.content.LocalPackBuilder
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
     *
     * 1. Instantly, on the device: a panel for this employer type and starter prep pulled from the
     *    advert's own requirements (no AI), so Prep and Interview work within seconds.
     * 2. The full advert (capped at 12s) and employer research, in parallel; starter prep is
     *    rebuilt from the full advert.
     * 3. AI parts in parallel, each limited to 40s, upgrading the starter content as they land.
     * Every step is written to [timeline] so slow or failing steps are visible.
     */
    fun prepare(job: JobListing, force: Boolean = false) {
        if (running[job.id]?.isActive == true) return
        if (!force && isReady(job.id)) return
        running[job.id] = scope.launch {
            loaded.join()
            val started = System.currentTimeMillis()
            _timeline.update { it + (job.id to emptyList()) }
            fun log(text: String) {
                val secs = (System.currentTimeMillis() - started) / 1000.0
                _timeline.update { it + (job.id to (it[job.id].orEmpty() + "%.1fs  %s".format(secs, text))) }
            }
            fun merge(transform: (RolePack) -> RolePack) =
                _packs.update { m -> m[job.id]?.let { p -> m + (job.id to transform(p)) } ?: m }

            if (force) _packs.update { it - job.id }
            val needBrief = force || job.id !in _briefs.value
            val sectorText = "${job.title} ${job.snippet} ${job.company.orEmpty()}"

            // 1. Instant panel and starter prep from the search snippet.
            if (_packs.value[job.id] == null) {
                val (topics, questions) = LocalPackBuilder.build(job.snippet, sectorText)
                _packs.update {
                    it + (job.id to RolePack(panel = PanelFactory.panelFor(job.id, sectorText), prepTopics = topics, questions = questions))
                }
                persist()
                log("Panel and starter prep ready")
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
                    // Search results often omit the employer; then wait for the advert to name it.
                    val company = job.company ?: d.await().company
                    withTimeoutOrNull(RESEARCH_TIMEOUT_MS) {
                        runCatching { jobs.employerResearch(company, job.location) }.getOrNull()
                    }.orEmpty()
                }
                d.await() to r.await()
            }
            val words = details.text.split(Regex("\\s+")).size
            log("Advert: ${details.source}, $words words; employer research: ${research.size} results")
            val description = buildString {
                append(details.text)
                if (research.isNotEmpty()) {
                    append("\n\nEMPLOYER RESEARCH (recent web results about ${details.company ?: job.company}):\n")
                    research.forEach { append("- ").append(it).append('\n') }
                }
            }
            val basedOn = listOfNotNull(
                details.source,
                "%,d words".format(words),
                research.size.takeIf { it > 0 }?.let { "$it employer sources" },
            ).joinToString(" · ")
            val thin = details.source == "search snippet only" || words < 120
            // Rebuild the starter prep from the full advert where the AI hasn't replaced it yet.
            val (localTopics, localQuestions) = LocalPackBuilder.build(details.text, sectorText)
            merge { p ->
                p.copy(
                    basedOn = basedOn,
                    thinSource = thin,
                    prepTopics = if (p.prepTopics.any { it.sayThis.isNotBlank() }) p.prepTopics else localTopics,
                    questions = p.questions.filter { it.modelAnswer.isNotBlank() } + localQuestions,
                )
            }
            persist()

            // 3. AI parts in parallel, each capped, upgrading the starter content.
            val profile = settings.current().profile
            val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
            val limiter = Semaphore(3)
            val existing = _packs.value[job.id]
            val aiCategories = existing?.questions.orEmpty().filter { it.modelAnswer.isNotBlank() }.map { it.category }.toSet()
            val batches = QuestionCategory.PACK.filter { it !in aiCategories }.chunked(2)
            val needTopics = existing?.prepTopics.orEmpty().none { it.sayThis.isNotBlank() }
            val needScenarios = existing?.scenarios.isNullOrEmpty()
            val total = batches.size + (if (needTopics) 1 else 0) + (if (needScenarios) 1 else 0) + (if (needBrief) 1 else 0)
            val done = java.util.concurrent.atomic.AtomicInteger()
            fun tick() = setProgress(job.id, PrepProgress("Upgrading your prep with AI… ${done.incrementAndGet()} of $total parts"))

            /** Runs one AI part with a hard time limit, logging how it went. */
            suspend fun <T> part(name: String, block: suspend () -> T): T? {
                val t0 = System.currentTimeMillis()
                val result = withTimeoutOrNull(PART_TIMEOUT_MS) { runCatching { limiter.withPermit { block() } } }
                val secs = (System.currentTimeMillis() - t0) / 1000
                val timedOut = result == null || result.exceptionOrNull() is TimeoutCancellationException
                when {
                    timedOut -> { errors += "$name timed out"; log("✗ $name: timed out after ${secs}s") }
                    result!!.isFailure -> {
                        val e = result.exceptionOrNull()!!
                        if (e is CancellationException) throw e
                        errors += e.message ?: "$name failed"
                        log("✗ $name: ${e.message ?: "failed"} (${secs}s)")
                    }
                    else -> log("✓ $name (${secs}s)")
                }
                tick()
                return if (timedOut) null else result?.getOrNull()
            }

            setProgress(job.id, PrepProgress("Upgrading your prep with AI… 0 of $total parts"))
            supervisorScope {
                val tasks = buildList {
                    if (needTopics) add(async {
                        part("Prep topics") { packAgent.buildCore(job, description, profile) }?.let { core ->
                            merge { it.copy(roleSummary = core.roleSummary, prepTopics = core.prepTopics) }
                        }
                    })
                    batches.forEach { cats ->
                        add(async {
                            part("Questions: ${cats.joinToString(", ")}") {
                                packAgent.buildQuestions(job, description, profile, panel, cats)
                            }?.let { qs ->
                                // AI questions replace the starter ones for these categories.
                                merge { p -> p.copy(questions = p.questions.filterNot { it.modelAnswer.isBlank() && it.category in cats } + qs) }
                            }
                        })
                    }
                    if (needScenarios) add(async {
                        part("Crisis scenarios") { packAgent.buildScenarios(job, description, profile, panel) }
                            ?.let { sc -> merge { it.copy(scenarios = sc) } }
                    })
                    if (needBrief) add(async {
                        part("Strategy brief") { strategyAgent.analyse(job, description, profile) }?.let { b ->
                            _briefs.update { it + (job.id to b) }
                            saveBriefRecord(job, b)
                        }
                    })
                }
                tasks.awaitAll()
            }
            merge { p ->
                val categories = p.questions.filter { it.modelAnswer.isNotBlank() }.map { it.category }.toSet()
                p.copy(
                    complete = QuestionCategory.PACK.all { it in categories } && p.scenarios.isNotEmpty() &&
                        p.prepTopics.any { it.sayThis.isNotBlank() },
                )
            }
            persist()
            log(if (errors.isEmpty()) "All done" else "Finished with ${errors.size} part(s) missing; tap Retry to fill them in")
            setProgress(job.id, PrepProgress(error = errors.distinct().joinToString(" · ").ifBlank { null }))
        }
    }

    private val _timeline = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    /** jobId → what happened while preparing, with timings, for the user to see. */
    val timeline: StateFlow<Map<String, List<String>>> = _timeline.asStateFlow()

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
        const val PART_TIMEOUT_MS = 40_000L
    }
}
