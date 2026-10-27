package uk.co.asmcoach.ui.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.SearchRegion

data class JobsUiState(
    val region: SearchRegion = SearchRegion.NorthWest,
    val pastWeekOnly: Boolean = false,
    val loading: Boolean = false,
    val jobs: List<JobListing> = emptyList(),
    val searched: Boolean = false,
    val error: String? = null,
    /** Id of the search-page card currently being opened up into individual jobs. */
    val expanding: String? = null,
    val notice: String? = null,
)

class JobsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(JobsUiState())
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    val hasSearchKey: StateFlow<Boolean> = container.settings.settings
        .map { it.hasJobSearch }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val briefs = container.roles.briefs
    val packs = container.roles.packs

    val updates = container.updates

    init {
        // Quietly look for a newer build; failures (offline, rate limit) are ignored.
        viewModelScope.launch { runCatching { container.updates.check() } }
    }

    fun setRegion(region: SearchRegion) = _state.update { it.copy(region = region) }

    fun setPastWeekOnly(value: Boolean) = _state.update { it.copy(pastWeekOnly = value) }

    fun search() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val s = _state.value
            runCatching { container.jobs.search(s.region, s.pastWeekOnly) }
                .onSuccess { jobs -> _state.update { it.copy(loading = false, jobs = jobs, searched = true) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Search failed.") } }
        }
    }

    fun loadSamples() = _state.update {
        it.copy(jobs = uk.co.asmcoach.data.repo.JobRepository.samples, searched = true, error = null)
    }

    fun select(job: JobListing) = container.roles.select(job)

    /**
     * Opens a role. A board search page is first expanded into the individual vacancies it lists;
     * if none can be read, the page itself is prepared as the role so the user always gets prep.
     */
    fun open(job: JobListing, onOpenRole: () -> Unit) {
        if (!job.isSearchPage) {
            select(job)
            onOpenRole()
            return
        }
        if (_state.value.expanding != null) return
        _state.update { it.copy(expanding = job.id, notice = null, error = null) }
        viewModelScope.launch {
            val found = runCatching { container.jobs.expandSearchPage(job) }.getOrDefault(emptyList())
            if (found.isNotEmpty()) {
                _state.update { s ->
                    val i = s.jobs.indexOfFirst { it.id == job.id }.coerceAtLeast(0)
                    val known = s.jobs.map { it.url }.toSet()
                    val fresh = found.filter { it.url !in known }
                    val merged = s.jobs.toMutableList().apply {
                        removeAll { it.id == job.id }
                        addAll(i.coerceAtMost(size), fresh)
                    }
                    s.copy(
                        jobs = merged,
                        expanding = null,
                        notice = "Found ${found.size} jobs on that ${job.source} page. Tap one to prepare for it.",
                    )
                }
            } else {
                _state.update { it.copy(expanding = null) }
                select(job.copy(isSearchPage = false))
                onOpenRole()
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun dismissError() = _state.update { it.copy(error = null) }
}
