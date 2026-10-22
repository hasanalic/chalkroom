package app.chalkroom.feature.classroom.room

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import app.chalkroom.students.StudentCatalog
import app.chalkroom.ui.theme.ChalkroomColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoomPaletteTest {
    private val base = ChalkroomColors()

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `Classic draws exactly today's room`() {
        val classic = RoomConfig()
        assertEquals(base, RoomPalette.roomColors(classic))
        assertEquals(base, RoomPalette.deskColors(classic))
        assertEquals(base, RoomPalette.boardColors(RoomPalette.board(classic.board)))
        assertEquals(0f, RoomPalette.light(classic.light).alpha)
    }

    @Test
    fun `Space Lab's wall is dark enough for light status bar icons`() {
        assertTrue(RoomPalette.darkWall(RoomPreset.SPACE_LAB.values.wall))
    }

    @Test
    fun `chalk stays readable on every board in every light`() {
        // The handwriting (93%), the dimmer second line on a paused board (80%), under each light's wash.
        BoardColor.entries.forEach { board ->
            LightColor.entries.forEach { light ->
                val wash = RoomPalette.light(light)
                val slate = wash.compositeOver(RoomPalette.board(board))
                listOf(0.93f, 0.8f).forEach { alpha ->
                    val chalk = wash.compositeOver(base.card.copy(alpha = alpha).compositeOver(RoomPalette.board(board)))
                    val ratio = contrast(chalk, slate)
                    assertTrue(ratio >= 4.5f, "chalk $alpha on $board in $light: $ratio")
                }
            }
        }
    }

    @Test
    fun `each student's colour reads on every board at least as well as on today's`() {
        // "<Name> learned:" and the newest line's underline are drawn in the student's colour. Pip's
        // pink already sits under 3:1 on Classic's green, which must not change, so no board may do worse.
        BoardColor.entries.forEach { board ->
            StudentCatalog.all.forEach { student ->
                val ratio = contrast(student.color, RoomPalette.board(board))
                val floor = minOf(3f, contrast(student.color, base.board))
                assertTrue(ratio >= floor - 0.01f, "${student.name} on $board: $ratio (floor $floor)")
            }
        }
    }
}
