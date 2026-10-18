package app.chalkroom.feature.choosestudent

import app.chalkroom.shared.StudentId

object ChooseStudentContract {
    data class UiState(val students: List<Choice> = StudentId.entries.map { Choice(it) })

    /** [paused]: the student has a paused lesson, so picking them continues it. */
    data class Choice(val studentId: StudentId, val paused: Boolean = false)

    sealed interface UiEvent {
        data class Picked(val studentId: StudentId) : UiEvent
    }

    sealed interface UiEffect {
        data class Read(val studentId: StudentId) : UiEffect
        data class ResumeLesson(val studentId: StudentId) : UiEffect
    }
}
