package app.chalkroom.feature.pickpart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.LessonRepository
import app.chalkroom.data.pdf.PdfParts
import app.chalkroom.feature.pickpart.PickPartContract.UiEffect
import app.chalkroom.feature.pickpart.PickPartContract.UiEvent
import app.chalkroom.feature.pickpart.PickPartContract.UiState
import app.chalkroom.students.StudentCatalog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Picks one part of a long PDF; its text becomes the notes, then today's path goes on as for a paste. */
class PickPartViewModel(private val repository: LessonRepository, holder: PdfParts) : ViewModel() {

    private val offer = holder.offer

    private val _state = MutableStateFlow(
        UiState(parts = offer?.parts.orEmpty(), studentName = offer?.preselected?.let { StudentCatalog[it].name }),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Selected -> if (event.index in _state.value.parts.indices) _state.update { it.copy(selected = event.index) }
            UiEvent.Confirm -> {
                val s = _state.value
                val part = s.parts.getOrNull(s.selected) ?: return
                repository.setNotes(part.text)
                send(offer?.preselected?.let { UiEffect.Read(it) } ?: UiEffect.ChooseStudent)
            }
            UiEvent.Back -> send(UiEffect.Back)
        }
    }

    private fun send(effect: UiEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
