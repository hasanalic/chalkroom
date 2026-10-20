package app.chalkroom.feature.classroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.ClassState
import app.chalkroom.data.LessonRepository
import app.chalkroom.feature.classroom.ClassroomContract.Board
import app.chalkroom.feature.classroom.ClassroomContract.Learned
import app.chalkroom.feature.classroom.ClassroomContract.Seat
import app.chalkroom.feature.classroom.ClassroomContract.UiEffect
import app.chalkroom.feature.classroom.ClassroomContract.UiEvent
import app.chalkroom.feature.classroom.ClassroomContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentCatalog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClassroomViewModel(private val repository: LessonRepository) : ViewModel() {

    val state: StateFlow<UiState> = repository.state
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, toUiState(repository.state.value))

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: UiEvent) {
        when (event) {
            UiEvent.TeachSomething -> send(UiEffect.OpenPasteNotes)
            UiEvent.BoardTapped -> state.value.board?.let { send(UiEffect.OpenLesson(it.studentId)) }
            is UiEvent.StudentTapped -> {
                if (repository.lesson(event.studentId)?.paused == true) {
                    // Let the nod play before the lesson opens.
                    viewModelScope.launch {
                        delay(NOD_MS)
                        _effects.send(UiEffect.OpenLesson(event.studentId))
                    }
                }
            }
        }
    }

    private fun send(effect: UiEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun toUiState(s: ClassState): UiState {
        val board = s.lastPausedStudentId?.let { id ->
            s.lessons[id]?.takeIf { it.paused }?.let { Board(id, StudentCatalog[id].name, it.queue.size) }
        }
        return UiState(
            board = board,
            learned = s.learned?.takeIf { board == null }?.let { Learned(it.studentId, StudentCatalog[it.studentId].name, it.lines) },
            seats = StudentId.entries.map { id ->
                val lesson = s.lessons[id]?.takeIf { it.paused }
                Seat(id, paused = lesson != null, motes = lesson?.queue?.map { Mote(it.id) }.orEmpty())
            },
        )
    }

    companion object {
        const val NOD_MS = 380L
    }
}
