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
)

class JobsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(JobsUiState())
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    val hasSearchKey: StateFlow<Boolean> = container.settings.settings
        .map { it.hasJobSearch }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val briefs = container.selection.briefs

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

    fun select(job: JobListing) = container.selection.select(job)

    fun dismissError() = _state.update { it.copy(error = null) }
}
