package uk.co.asmcoach.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.asmcoach.AppContainer

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val build: Int) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

data class SettingsForm(
    val geminiKey: String = "",
    val reedKey: String = "",
    val serperKey: String = "",
    val firecrawlKey: String = "",
    val model: String = "",
    val name: String = "",
    val notes: String = "",
    val speechRate: Float = 1f,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _form = MutableStateFlow(SettingsForm())
    val form: StateFlow<SettingsForm> = _form.asStateFlow()

    val cloudSync: Boolean = container.sessions.cloudEnabled
    val currentBuild: Int = container.updates.currentBuild

    private val _update = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val update: StateFlow<UpdateStatus> = _update.asStateFlow()

    fun checkForUpdates() {
        _update.value = UpdateStatus.Checking
        viewModelScope.launch {
            _update.value = runCatching { container.updates.check() }.fold(
                { latest -> if (latest > currentBuild) UpdateStatus.Available(latest) else UpdateStatus.UpToDate },
                { e -> UpdateStatus.Failed(e.message ?: "Couldn't check for updates.") },
            )
        }
    }

    init {
        viewModelScope.launch {
            val s = container.settings.current()
            _form.value = SettingsForm(
                geminiKey = s.geminiKey,
                reedKey = s.reedKey,
                serperKey = s.serperKey,
                firecrawlKey = s.firecrawlKey,
                model = s.geminiModel,
                name = s.candidateName,
                notes = s.profileNotes,
                speechRate = s.speechRate,
                loaded = true,
            )
        }
    }

    fun edit(transform: (SettingsForm) -> SettingsForm) = _form.update { transform(it).copy(message = null) }

    fun save(then: () -> Unit = {}) {
        val f = _form.value
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            container.settings.update(f.geminiKey, f.reedKey, f.serperKey, f.firecrawlKey, f.model, f.name, f.notes, f.speechRate)
            container.tts.baseRate = f.speechRate
            _form.update { it.copy(saving = false, message = "Saved") }
            then()
        }
    }

    /** Saves, then makes a tiny Gemini call to confirm the key and model work. */
    fun testGemini() {
        save {
            _form.update { it.copy(testing = true) }
            viewModelScope.launch {
                val result = runCatching {
                    container.gemini.generateText("Reply with one word.", "Say ready.", temperature = 0f, fast = true)
                }
                _form.update {
                    it.copy(
                        testing = false,
                        message = result.fold({ "Gemini is connected" }, { e -> e.message ?: "Gemini test failed" }),
                    )
                }
            }
        }
    }

    fun previewVoice() {
        container.tts.baseRate = _form.value.speechRate
        container.tts.speak("Morning. Let's get you ready for site.", "preview")
    }
}
