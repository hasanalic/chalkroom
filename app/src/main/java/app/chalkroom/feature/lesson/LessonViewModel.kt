package app.chalkroom.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.Lesson
import app.chalkroom.data.LessonRepository
import app.chalkroom.shared.StudentId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Slice 4 shows where reading lands: the student, the counter and misconception 1. Teaching comes next. */
class LessonViewModel(private val studentId: StudentId, repository: LessonRepository) : ViewModel() {
    val lesson: StateFlow<Lesson?> = repository.state
        .map { it.lessons[studentId] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.lesson(studentId))
}
