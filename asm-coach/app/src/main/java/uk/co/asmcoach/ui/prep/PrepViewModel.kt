package uk.co.asmcoach.ui.prep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.content.MasterclassContent
import uk.co.asmcoach.data.model.Lesson
import uk.co.asmcoach.data.model.Masterclass

data class PrepUiState(
    val expandedModule: String? = MasterclassContent.modules.first().id,
    val expandedLessons: Set<String> = emptySet(),
    val completed: Set<String> = emptySet(),
    val personalised: Map<String, String> = emptyMap(),
    val personalising: Set<String> = emptySet(),
    val error: String? = null,
)

class PrepViewModel(private val container: AppContainer) : ViewModel() {
    val modules: List<Masterclass> = MasterclassContent.modules
    val speakingId = container.tts.speakingId
    val targetJob = container.selection.job

    private val _state = MutableStateFlow(PrepUiState())
    val state: StateFlow<PrepUiState> = _state.asStateFlow()

    fun toggleModule(id: String) = _state.update {
        it.copy(expandedModule = if (it.expandedModule == id) null else id)
    }

    fun toggleLesson(id: String) = _state.update {
        it.copy(expandedLessons = if (id in it.expandedLessons) it.expandedLessons - id else it.expandedLessons + id)
    }

    fun toggleSpeak(id: String, text: String) {
        val tts = container.tts
        if (tts.speakingId.value == id) {
            tts.stop()
        } else {
            tts.speak(text, id)
            _state.update { it.copy(completed = it.completed + id.substringBefore(':')) }
        }
    }

    /** Agent 2: ask Gemini for a talking point grounded in the candidate's own profile. */
    fun personalise(module: Masterclass, lesson: Lesson) {
        if (lesson.id in _state.value.personalising) return
        _state.update { it.copy(personalising = it.personalising + lesson.id, error = null) }
        viewModelScope.launch {
            runCatching {
                container.coachAgent.personalise(module, lesson, container.settings.current().profile, targetJob.value)
            }.onSuccess { text ->
                _state.update {
                    it.copy(personalised = it.personalised + (lesson.id to text), personalising = it.personalising - lesson.id)
                }
            }.onFailure { e ->
                _state.update { it.copy(personalising = it.personalising - lesson.id, error = e.message) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun stopSpeaking() = container.tts.stop()
}
