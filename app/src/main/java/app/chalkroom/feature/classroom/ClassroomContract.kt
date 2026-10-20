package app.chalkroom.feature.classroom

import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote

object ClassroomContract {
    /**
     * [board] is null when no lesson is paused; then the board shows what the last finished lesson
     * taught ([learned]), or stays blank. [lightsOn] is false only on the very first open after
     * install, until the switch on the wall is tapped.
     */
    data class UiState(
        val lightsOn: Boolean = true,
        val board: Board? = null,
        val learned: Learned? = null,
        val seats: List<Seat> = StudentId.entries.map { Seat(it) },
        val bubble: Bubble? = null,
    )

    /** One short line a tapped student says; [serial] tells a new bubble from the last one. */
    data class Bubble(val studentId: StudentId, val text: String, val serial: Int)

    data class Board(val studentId: StudentId, val name: String, val remaining: Int)

    data class Learned(val studentId: StudentId, val name: String, val lines: List<String>)

    data class Seat(val studentId: StudentId, val paused: Boolean = false, val motes: List<Mote> = emptyList())

    sealed interface UiEvent {
        data object TeachSomething : UiEvent
        data class StudentTapped(val studentId: StudentId) : UiEvent
        data object BoardTapped : UiEvent
        data object SwitchTapped : UiEvent
    }

    sealed interface UiEffect {
        data object OpenPasteNotes : UiEffect
        data class OpenLesson(val studentId: StudentId) : UiEffect

        /** The switch tapped with the lights already on: one quick flicker. */
        data object Flicker : UiEffect
    }
}
