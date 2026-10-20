package app.chalkroom.feature.closing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.LessonRepository
import app.chalkroom.feature.closing.ClosingContract.Beat
import app.chalkroom.feature.closing.ClosingContract.UiEffect
import app.chalkroom.feature.closing.ClosingContract.UiEvent
import app.chalkroom.feature.closing.ClosingContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Expression
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Takes the finished lesson out of the room (the desk goes idle) and plays the closing beats. */
class ClosingViewModel(private val studentId: StudentId, repository: LessonRepository) : ViewModel() {

    private val finished = repository.finish(studentId)

    private val _state = MutableStateFlow(
        UiState(
            studentId,
            topicTitle = finished?.topicTitle.orEmpty(),
            whatStuck = finished?.cleared.orEmpty(),
            chalkLines = finished?.chalkLines.orEmpty(),
            boardFitLines = finished?.totalCount?.coerceAtLeast(1) ?: 3,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            delay(RELIEF_AT_MS)
            _state.update { it.copy(beat = Beat.RELIEF) }
            delay(SETTLE_MS)
            _state.update { it.copy(expression = Expression.RELAXED) }
            if (_state.value.whatStuck.isNotEmpty()) {
                delay(WHAT_STUCK_AFTER_MS)
                _state.update { it.copy(beat = Beat.WHAT_STUCK) }
            }
            delay(NOTEBOOK_AFTER_MS)
            _state.update { it.copy(beat = Beat.NOTEBOOK_CLOSED) }
            delay(NOTEBOOK_CLOSE_MS)
            _effects.send(UiEffect.SoftHaptic)
            delay(OPTIONS_AFTER_MS)
            _state.update { it.copy(beat = Beat.OPTIONS) }
        }
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.ToggleItem -> _state.update { it.copy(openIndex = event.index.takeUnless { i -> i == it.openIndex }) }
            UiEvent.TeachSomethingElse -> viewModelScope.launch { _effects.send(UiEffect.TeachAgain(studentId)) }
            UiEvent.BackToClass -> viewModelScope.launch { _effects.send(UiEffect.BackToClass) }
        }
    }

    companion object {
        const val RELIEF_AT_MS = 700L
        const val SETTLE_MS = 900L
        const val WHAT_STUCK_AFTER_MS = 500L
        const val NOTEBOOK_AFTER_MS = 1_400L
        const val NOTEBOOK_CLOSE_MS = 650L // matches the cover flip in ClosingScreen
        const val OPTIONS_AFTER_MS = 350L
    }
}
