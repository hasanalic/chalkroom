package app.chalkroom.data

import app.chalkroom.feature.classroom.room.BoardColor
import app.chalkroom.feature.classroom.room.DeskStyle
import app.chalkroom.feature.classroom.room.LightColor
import app.chalkroom.feature.classroom.room.RoomConfig
import app.chalkroom.feature.classroom.room.RoomPreset
import app.chalkroom.feature.classroom.room.RoomValues
import app.chalkroom.feature.classroom.room.WallColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoomStyleRepositoryTest {
    @Test
    fun `a fresh install is Classic`() {
        assertEquals(RoomConfig(), PrefsRoomStyleRepository(FakePrefs()).load())
    }

    @Test
    fun `the whole room comes back after a restart`() {
        val prefs = FakePrefs()
        val room = RoomConfig(
            preset = RoomPreset.SPACE_LAB,
            values = RoomValues(BoardColor.BURGUNDY, WallColor.SAGE, DeskStyle.PAINTED, LightColor.EVENING),
            motto = "Mistakes welcome",
        )

        PrefsRoomStyleRepository(prefs).save(room)

        assertEquals(room, PrefsRoomStyleRepository(prefs).load())
    }

    @Test
    fun `an unknown stored value falls back to Classic's`() {
        val prefs = FakePrefs()
        PrefsRoomStyleRepository(prefs).save(RoomConfig(RoomPreset.NIGHT_STUDY))
        prefs.values["style_board"] = "TARTAN"
        prefs.values["style_preset"] = "DISCO"

        val loaded = PrefsRoomStyleRepository(prefs).load()

        assertEquals(RoomPreset.CLASSIC, loaded.preset)
        assertEquals(BoardColor.GREEN, loaded.board)
        assertEquals(WallColor.NAVY, loaded.wall)
    }
}
