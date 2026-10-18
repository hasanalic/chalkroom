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

    /** Applies a pure [LessonLogic] step to this student's lesson; no-op when there is none. */
    fun update(studentId: StudentId, step: (Lesson) -> Lesson) = _state.update { s ->
        val lesson = s.lessons[studentId] ?: return@update s
        s.copy(lessons = s.lessons + (studentId to step(lesson)))
    }

    /** Closing: the lesson leaves the room and the desk goes idle. Returns what it was. */
    fun finish(studentId: StudentId): Lesson? {
        val lesson = lesson(studentId)
        _state.update { s ->
            s.copy(
                lessons = s.lessons - studentId,
                lastPausedStudentId = s.lastPausedStudentId.takeUnless { it == studentId },
            )
        }
        return lesson
    }
}
