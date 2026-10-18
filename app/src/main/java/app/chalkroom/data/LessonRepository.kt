package app.chalkroom.data

import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.StudentId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The single source of truth across screens. A Koin singleton. */
class LessonRepository {
    private val _state = MutableStateFlow(ClassState())
    val state: StateFlow<ClassState> = _state.asStateFlow()

    val notes: String get() = _state.value.lastNotes

    fun lesson(studentId: StudentId): Lesson? = _state.value.lessons[studentId]

    fun setNotes(notes: String) = _state.update { it.copy(lastNotes = notes) }

    /** Replaces any earlier lesson for this student. */
    fun startLesson(studentId: StudentId, deck: CreateDeckResponse) = _state.update {
        it.copy(lessons = it.lessons + (studentId to LessonLogic.startLesson(studentId, deck)))
    }
}
