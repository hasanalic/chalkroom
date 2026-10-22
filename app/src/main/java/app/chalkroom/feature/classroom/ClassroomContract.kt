package app.chalkroom.feature.classroom

import app.chalkroom.feature.classroom.room.BoardColor
import app.chalkroom.feature.classroom.room.DeskStyle
import app.chalkroom.feature.classroom.room.LightColor
import app.chalkroom.feature.classroom.room.RoomConfig
import app.chalkroom.feature.classroom.room.RoomPreset
import app.chalkroom.feature.classroom.room.WallColor
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote

object ClassroomContract {
    /**
     * [board] is null when no lesson is paused; then the board shows what the last finished lesson
     * taught ([learned]), or stays blank. [lightsOn] follows the switch on the wall, each tap
     * flipping it; it starts false on the very first open after install. [room] is how the room is
     * dressed; [roomCustom] once a fine-tune value differs from its preset; [customizing] while the
     * Room sheet is open.
     */
    data class UiState(
        val lightsOn: Boolean = true,
        val board: Board? = null,
        val learned: Learned? = null,
        val seats: List<Seat> = StudentId.entries.map { Seat(it) },
        val bubble: Bubble? = null,
        val room: RoomConfig = RoomConfig(),
        val roomCustom: Boolean = false,
        val customizing: Boolean = false,
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
        data object CustomizeTapped : UiEvent
        data object CustomizeDismissed : UiEvent
        data class PresetPicked(val preset: RoomPreset) : UiEvent
        data class BoardPicked(val board: BoardColor) : UiEvent
        data class WallPicked(val wall: WallColor) : UiEvent
        data class DesksPicked(val desks: DeskStyle) : UiEvent
        data class LightPicked(val light: LightColor) : UiEvent
        data class MottoChanged(val motto: String) : UiEvent
        data object ResetTapped : UiEvent
    }

    sealed interface UiEffect {
        data object OpenPasteNotes : UiEffect
        data class OpenLesson(val studentId: StudentId) : UiEffect

        /** The room just changed in a way they'd see: the students glance at the board. */
        data object StudentsGlance : UiEffect
    }
}
