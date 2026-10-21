package app.chalkroom.feature.choosestudent

import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote

object ChooseStudentContract {
    data class UiState(
        val students: List<Choice> = StudentId.entries.map { Choice(it) },
        val confirm: Confirm? = null,
        /** The student picked so far; Continue stays off until there is one. */
        val selected: StudentId? = null,
        /** Bumped on every pick, so picking the same student again still plays their celebration. */
        val selectTick: Int = 0,
    )

    /** [paused]: the student has a paused lesson, so picking them asks before replacing it. [motes]: its beliefs still open. */
    data class Choice(val studentId: StudentId, val paused: Boolean = false, val motes: List<Mote> = emptyList())

    /** Picked a student with a paused lesson while holding new notes: continue it, or start the new notes. */
    data class Confirm(val studentId: StudentId, val pausedTopic: String, val stillOpen: Int)

    sealed interface UiEvent {
        /** Tapped a student's box: picks them, nothing more. */
        data class Selected(val studentId: StudentId) : UiEvent
        /** Goes on with the picked student (asks first if they have a paused lesson). */
        data object Continue : UiEvent
        data object ContinuePaused : UiEvent
        data object StartNew : UiEvent
        data object DismissConfirm : UiEvent
    }

    sealed interface UiEffect {
        data class Read(val studentId: StudentId) : UiEffect
        data class ResumeLesson(val studentId: StudentId) : UiEffect
    }
}
