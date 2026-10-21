package app.chalkroom.feature.choosestudent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.LessonRepository
import app.chalkroom.feature.choosestudent.ChooseStudentContract.Choice
import app.chalkroom.feature.choosestudent.ChooseStudentContract.Confirm
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiEffect
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiEvent
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Reached only from Paste Notes, so there are always fresh notes in hand: a paused lesson is never resumed silently. */
class ChooseStudentViewModel(private val repository: LessonRepository) : ViewModel() {

    private val confirm = MutableStateFlow<Confirm?>(null)
    private val selection = MutableStateFlow<Pair<StudentId?, Int>>(null to 0)

    val state: StateFlow<UiState> = combine(repository.state, confirm, selection) { s, c, (selected, tick) ->
        UiState(
            StudentId.entries.map { id ->
                val lesson = s.lessons[id]?.takeIf { it.paused }
                Choice(id, paused = lesson != null, motes = lesson?.queue?.map { Mote(it.id) }.orEmpty())
            },
            c,
            selected,
            tick,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Selected -> selection.value = event.studentId to selection.value.second + 1
            UiEvent.Continue -> selection.value.first?.let { id ->
                val paused = repository.lesson(id)?.takeIf { it.paused }
                if (paused != null) {
                    confirm.value = Confirm(id, paused.topicTitle, paused.queue.size)
                } else {
                    send(UiEffect.Read(id))
                }
            }
            UiEvent.ContinuePaused -> confirm.value?.let {
                confirm.value = null
                send(UiEffect.ResumeLesson(it.studentId))
            }
            UiEvent.StartNew -> confirm.value?.let {
                confirm.value = null
                send(UiEffect.Read(it.studentId))
            }
            UiEvent.DismissConfirm -> confirm.value = null
        }
    }

    private fun send(effect: UiEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
