package app.chalkroom.feature.pastenotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.LessonRepository
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEffect
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEvent
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiState
import app.chalkroom.shared.StudentId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [preselected] is set when coming from "Teach Mira something else": Continue skips the choice. */
class PasteNotesViewModel(preselected: StudentId?, private val repository: LessonRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        UiState(notes = repository.notes, preselected = preselected, lastTopic = repository.state.value.lastTaught?.topicTitle),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.NotesChanged -> {
                _state.update { it.copy(notes = event.notes) }
                repository.setNotes(event.notes)
            }
            UiEvent.UseLastNotes -> {
                val last = repository.state.value.lastTaught ?: return
                _state.update { it.copy(notes = last.notes) }
                repository.setNotes(last.notes)
            }
            UiEvent.Continue -> {
                val s = _state.value
                if (!s.canContinue) return
                repository.setNotes(s.notes.trim())
                viewModelScope.launch {
                    _effects.send(s.preselected?.let { UiEffect.Read(it) } ?: UiEffect.ChooseStudent)
                }
            }
        }
    }
}
