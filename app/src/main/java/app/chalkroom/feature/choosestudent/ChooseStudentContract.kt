package app.chalkroom.feature.choosestudent

import app.chalkroom.shared.StudentId

object ChooseStudentContract {
    data class UiState(
        val students: List<Choice> = StudentId.entries.map { Choice(it) },
        val confirm: Confirm? = null,
    )

    /** [paused]: the student has a paused lesson, so picking them asks before replacing it. */
    data class Choice(val studentId: StudentId, val paused: Boolean = false)

    /** Picked a student with a paused lesson while holding new notes: continue it, or start the new notes. */
    data class Confirm(val studentId: StudentId, val pausedTopic: String, val stillOpen: Int)

    sealed interface UiEvent {
        data class Picked(val studentId: StudentId) : UiEvent
        data object ContinuePaused : UiEvent
        data object StartNew : UiEvent
        data object DismissConfirm : UiEvent
    }

    sealed interface UiEffect {
        data class Read(val studentId: StudentId) : UiEffect
        data class ResumeLesson(val studentId: StudentId) : UiEffect
    }
}
