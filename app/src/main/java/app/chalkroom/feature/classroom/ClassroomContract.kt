package app.chalkroom.feature.classroom

import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote

object ClassroomContract {
    /**
     * [board] is null when no lesson is paused; then the board shows what the last finished lesson
     * taught ([learned]), or stays blank.
     */
    data class UiState(
        val board: Board? = null,
        val learned: Learned? = null,
        val seats: List<Seat> = StudentId.entries.map { Seat(it) },
    )

    data class Board(val studentId: StudentId, val name: String, val remaining: Int)

    data class Learned(val studentId: StudentId, val name: String, val lines: List<String>)

    data class Seat(val studentId: StudentId, val paused: Boolean = false, val motes: List<Mote> = emptyList())

    sealed interface UiEvent {
        data object TeachSomething : UiEvent
        data class StudentTapped(val studentId: StudentId) : UiEvent
        data object BoardTapped : UiEvent
    }

    sealed interface UiEffect {
        data object OpenPasteNotes : UiEffect
        data class OpenLesson(val studentId: StudentId) : UiEffect
    }
}
