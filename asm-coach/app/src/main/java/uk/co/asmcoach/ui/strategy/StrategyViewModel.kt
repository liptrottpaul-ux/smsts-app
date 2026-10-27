package uk.co.asmcoach.ui.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.SessionKind
import uk.co.asmcoach.data.model.SessionRecord
import uk.co.asmcoach.data.model.StrategyBrief
import uk.co.asmcoach.data.remote.AppJson

data class StrategyUiState(
    val job: JobListing? = null,
    val brief: StrategyBrief? = null,
    val stage: String? = null,
    val error: String? = null,
)

class StrategyViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(StrategyUiState())
    val state: StateFlow<StrategyUiState> = _state.asStateFlow()

    val speakingId = container.tts.speakingId

    init {
        val job = container.selection.job.value
        val cached = job?.let { container.selection.briefs.value[it.id] }
        _state.value = StrategyUiState(job = job, brief = cached)
        if (job != null && cached == null) generate()
    }

    fun generate() {
        val job = _state.value.job ?: return
        if (_state.value.stage != null) return
        viewModelScope.launch {
            _state.update { it.copy(stage = "Reading the full listing…", error = null) }
            val description = runCatching { container.jobs.fullDescription(job) }.getOrDefault(job.snippet)
            _state.update { it.copy(stage = "Scoring your fit and writing your plan…") }
            runCatching {
                container.strategyAgent.analyse(job, description, container.settings.current().profile)
            }.onSuccess { brief ->
                container.selection.putBrief(job.id, brief)
                _state.update { it.copy(brief = brief, stage = null) }
                container.sessions.save(
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
            }.onFailure { e ->
                _state.update { it.copy(stage = null, error = e.message ?: "Couldn't build the brief.") }
            }
        }
    }

    fun toggleSpeak(id: String, text: String) {
        if (container.tts.speakingId.value == id) container.tts.stop() else container.tts.speak(text, id)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        container.tts.stop()
    }
}
