package app.chalkroom.feature.choosestudent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.LessonRepository
import app.chalkroom.feature.choosestudent.ChooseStudentContract.Choice
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiEffect
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiEvent
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiState
import app.chalkroom.shared.StudentId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChooseStudentViewModel(private val repository: LessonRepository) : ViewModel() {

    val state: StateFlow<UiState> = repository.state
        .map { s -> UiState(StudentId.entries.map { Choice(it, paused = s.lessons[it]?.paused == true) }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Picked -> viewModelScope.launch {
                val paused = repository.lesson(event.studentId)?.paused == true
                _effects.send(if (paused) UiEffect.ResumeLesson(event.studentId) else UiEffect.Read(event.studentId))
            }
        }
    }
}
