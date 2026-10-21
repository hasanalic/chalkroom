package app.chalkroom.feature.classroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.ClassState
import app.chalkroom.data.LessonRepository
import app.chalkroom.data.RoomLights
import app.chalkroom.feature.classroom.ClassroomContract.Board
import app.chalkroom.feature.classroom.ClassroomContract.Bubble
import app.chalkroom.feature.classroom.ClassroomContract.Learned
import app.chalkroom.feature.classroom.ClassroomContract.Seat
import app.chalkroom.feature.classroom.ClassroomContract.UiEffect
import app.chalkroom.feature.classroom.ClassroomContract.UiEvent
import app.chalkroom.feature.classroom.ClassroomContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClassroomViewModel(private val repository: LessonRepository, private val lights: RoomLights) : ViewModel() {

    private val lightsOn = MutableStateFlow(lights.on)
    private val bubble = MutableStateFlow<Bubble?>(null)
    private var bubbleJob: Job? = null
    private val lastLine = mutableMapOf<StudentId, String>()

    val state: StateFlow<UiState> = combine(repository.state, lightsOn, bubble, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, toUiState(repository.state.value, lightsOn.value, bubble.value))

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: UiEvent) {
        when (event) {
            UiEvent.TeachSomething -> send(UiEffect.OpenPasteNotes)
            UiEvent.SwitchTapped -> {
                val on = !lightsOn.value
                lights.on = on
                lightsOn.value = on
                // In the dark nobody talks: a tap bubble ends at once.
                if (!on) {
                    bubbleJob?.cancel()
                    bubble.value = null
                }
            }
            UiEvent.BoardTapped -> state.value.board?.let { send(UiEffect.OpenLesson(it.studentId)) }
            is UiEvent.StudentTapped -> {
                if (repository.lesson(event.studentId)?.paused == true) {
                    // Let the nod play before the lesson opens.
                    viewModelScope.launch {
                        delay(NOD_MS)
                        _effects.send(UiEffect.OpenLesson(event.studentId))
                    }
                } else {
                    say(event.studentId)
                }
            }
        }
    }

    /** One short line in a bubble, never the same as this student's last one; a new tap replaces it. */
    private fun say(id: StudentId) {
        val line = StudentCatalog[id].chatter.filter { it != lastLine[id] }.random()
        lastLine[id] = line
        bubble.value = Bubble(id, line, (bubble.value?.serial ?: 0) + 1)
        bubbleJob?.cancel()
        bubbleJob = viewModelScope.launch {
            delay(BUBBLE_MS)
            bubble.value = null
        }
    }

    private fun send(effect: UiEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun toUiState(s: ClassState, lightsOn: Boolean, bubble: Bubble?): UiState {
        val board = s.lastPausedStudentId?.let { id ->
            s.lessons[id]?.takeIf { it.paused }?.let { Board(id, StudentCatalog[id].name, it.queue.size) }
        }
        return UiState(
            lightsOn = lightsOn,
            board = board,
            learned = s.learned?.takeIf { board == null }?.let { Learned(it.studentId, StudentCatalog[it.studentId].name, it.lines) },
            seats = StudentId.entries.map { id ->
                val lesson = s.lessons[id]?.takeIf { it.paused }
                Seat(id, paused = lesson != null, motes = lesson?.queue?.map { Mote(it.id) }.orEmpty())
            },
            bubble = bubble,
        )
    }

    companion object {
        const val NOD_MS = 380L
        const val BUBBLE_MS = 2_500L
    }
}
