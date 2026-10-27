package uk.co.asmcoach.ui.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.RolePack
import uk.co.asmcoach.data.model.StrategyBrief
import uk.co.asmcoach.data.repo.PrepProgress

data class StrategyUiState(
    val job: JobListing? = null,
    val brief: StrategyBrief? = null,
    val pack: RolePack? = null,
    val progress: PrepProgress = PrepProgress(),
) {
    val stage: String? get() = progress.stage
    val error: String? get() = progress.error
}

class StrategyViewModel(private val container: AppContainer) : ViewModel() {
    private val roles = container.roles

    val state: StateFlow<StrategyUiState> = combine(roles.job, roles.briefs, roles.packs, roles.progress) { job, briefs, packs, progress ->
        StrategyUiState(
            job = job,
            brief = job?.let { briefs[it.id] },
            pack = job?.let { packs[it.id] },
            progress = job?.let { progress[it.id] } ?: PrepProgress(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StrategyUiState(job = roles.job.value))

    val speakingId = container.tts.speakingId

    init {
        roles.job.value?.let { roles.prepare(it) }
    }

    /** Builds whatever is still missing (after an error). */
    fun generate() {
        roles.job.value?.let { roles.prepare(it) }
    }

    /** Throws away this role's material and builds it fresh. */
    fun regenerate() {
        roles.job.value?.let { roles.prepare(it, force = true) }
    }

    fun toggleSpeak(id: String, text: String) {
        if (container.tts.speakingId.value == id) container.tts.stop() else container.tts.speak(text, id)
    }

    fun dismissError() {
        roles.job.value?.let { roles.clearError(it.id) }
    }

    override fun onCleared() {
        container.tts.stop()
    }
}
