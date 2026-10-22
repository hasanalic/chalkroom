package app.chalkroom.feature.classroom.room

import app.chalkroom.data.RoomStyleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomizeRoomTest {
    private class MemoryRepository : RoomStyleRepository {
        var saved: RoomConfig? = null
        override fun load() = saved ?: RoomConfig()
        override fun save(config: RoomConfig) {
            saved = config
        }
    }

    private var clock = 10_000L
    private val repository = MemoryRepository()
    private val customize = CustomizeRoom(repository) { clock }

    private fun RoomConfig.with(change: RoomChange) = customize.apply(this, change).config

    @Test
    fun `each preset sets its own values`() {
        val expected = mapOf(
            RoomPreset.CLASSIC to RoomValues(BoardColor.GREEN, WallColor.CREAM, DeskStyle.WOOD, LightColor.AFTERNOON),
            RoomPreset.NIGHT_STUDY to RoomValues(BoardColor.BLACK, WallColor.NAVY, DeskStyle.PAINTED, LightColor.EVENING),
            RoomPreset.LIBRARY to RoomValues(BoardColor.DARK_GREEN, WallColor.SHELVES, DeskStyle.WOOD, LightColor.GOLDEN),
            RoomPreset.SUNSET to RoomValues(BoardColor.BLUE_GREY, WallColor.SUNSET_GLOW, DeskStyle.PAINTED, LightColor.SUNSET),
            RoomPreset.SPACE_LAB to RoomValues(BoardColor.DEEP_NAVY, WallColor.STEEL, DeskStyle.METAL, LightColor.COOL_BLUE),
        )
        val floors = mapOf(
            RoomPreset.CLASSIC to Floor.WOOD,
            RoomPreset.NIGHT_STUDY to Floor.DARK,
            RoomPreset.LIBRARY to Floor.WOOD,
            RoomPreset.SUNSET to Floor.WOOD,
            RoomPreset.SPACE_LAB to Floor.METAL,
        )
        RoomPreset.entries.forEach { preset ->
            val room = RoomConfig().with(RoomChange.Preset(preset))
            assertEquals(preset, room.preset)
            assertEquals(expected.getValue(preset), room.values, preset.name)
            assertEquals(floors.getValue(preset), room.floor, preset.name)
            assertFalse(CustomizeRoom.isCustom(room), preset.name)
        }
    }

    @Test
    fun `only evening light turns the desk lamps on`() {
        assertTrue(RoomConfig(RoomPreset.NIGHT_STUDY).light.lampsOn)
        RoomPreset.entries.filter { it != RoomPreset.NIGHT_STUDY }.forEach { assertFalse(RoomConfig(it).light.lampsOn, it.name) }
    }

    @Test
    fun `the default room is Classic`() {
        assertEquals(RoomConfig(RoomPreset.CLASSIC, RoomPreset.CLASSIC.values, ""), RoomConfig())
        assertEquals(RoomConfig(), customize.load())
    }

    @Test
    fun `a preset's own shade shows its nearest swatch in the sheet`() {
        val library = RoomConfig(RoomPreset.LIBRARY)
        assertEquals(BoardColor.GREEN, CustomizeRoom.shownBoard(library))
        assertEquals(LightColor.AFTERNOON, CustomizeRoom.shownLight(library))
        val space = RoomConfig(RoomPreset.SPACE_LAB)
        assertEquals(BoardColor.SLATE_BLUE, CustomizeRoom.shownBoard(space))
        assertEquals(LightColor.MORNING, CustomizeRoom.shownLight(space))
        assertTrue(BoardColor.entries.filter { it.swatch }.size == 4)
        assertTrue(LightColor.entries.filter { it.swatch }.size == 3)
        assertTrue(WallColor.entries.size == 6)
    }

    @Test
    fun `any fine-tune change away from the preset is Custom, and picking it again clears that`() {
        val night = RoomConfig().with(RoomChange.Preset(RoomPreset.NIGHT_STUDY))
        listOf(
            RoomChange.Board(BoardColor.BURGUNDY),
            RoomChange.Wall(WallColor.SAGE),
            RoomChange.Desks(DeskStyle.METAL),
            RoomChange.Light(LightColor.MORNING),
        ).forEach { change ->
            val custom = night.with(change)
            assertTrue(CustomizeRoom.isCustom(custom), change.toString())
            assertEquals(RoomPreset.NIGHT_STUDY, custom.preset)
            assertFalse(CustomizeRoom.isCustom(custom.with(RoomChange.Preset(RoomPreset.NIGHT_STUDY))))
        }
        // Back to the preset's own value by hand is not Custom either.
        assertFalse(CustomizeRoom.isCustom(night.with(RoomChange.Desks(DeskStyle.METAL)).with(RoomChange.Desks(DeskStyle.PAINTED))))
    }

    @Test
    fun `the motto alone doesn't make a preset Custom, and a preset keeps it`() {
        val room = RoomConfig().with(RoomChange.Motto("Mistakes welcome"))
        assertFalse(CustomizeRoom.isCustom(room))
        assertEquals("Mistakes welcome", room.with(RoomChange.Preset(RoomPreset.SUNSET)).motto)
    }

    @Test
    fun `the motto is one line of at most 40 characters`() {
        val long = "a".repeat(55)
        assertEquals(40, RoomConfig().with(RoomChange.Motto(long)).motto.length)
        assertEquals("a".repeat(40), RoomConfig().with(RoomChange.Motto("a".repeat(40))).motto)
        assertEquals("one two", RoomConfig().with(RoomChange.Motto("one\ntwo")).motto)
        assertEquals("", RoomConfig().with(RoomChange.Motto("x")).with(RoomChange.Motto("")).motto)
    }

    @Test
    fun `reset goes back to Classic and clears the motto`() {
        val room = RoomConfig(RoomPreset.SPACE_LAB, motto = "To the stars").with(RoomChange.Board(BoardColor.BURGUNDY))
        assertEquals(RoomConfig(), room.with(RoomChange.Reset))
    }

    @Test
    fun `every change is saved at once`() {
        RoomConfig().with(RoomChange.Wall(WallColor.STEEL))
        assertEquals(WallColor.STEEL, repository.saved?.wall)
    }

    @Test
    fun `the students glance once per burst of changes`() {
        var room = RoomConfig()
        fun glance(change: RoomChange): Boolean = customize.apply(room, change).also { room = it.config }.glance

        assertTrue(glance(RoomChange.Board(BoardColor.BURGUNDY)))
        clock += 1_400
        assertFalse(glance(RoomChange.Light(LightColor.EVENING)))
        clock += 200
        assertTrue(glance(RoomChange.Preset(RoomPreset.LIBRARY)))
    }

    @Test
    fun `wall, desks, the motto and a repeated pick never make them glance`() {
        val room = RoomConfig()
        assertFalse(customize.apply(room, RoomChange.Wall(WallColor.SAGE)).glance)
        assertFalse(customize.apply(room, RoomChange.Desks(DeskStyle.METAL)).glance)
        assertFalse(customize.apply(room, RoomChange.Motto("Hi")).glance)
        assertFalse(customize.apply(room, RoomChange.Board(BoardColor.GREEN)).glance)
        assertFalse(customize.apply(room, RoomChange.Preset(RoomPreset.CLASSIC)).glance)
        assertTrue(customize.apply(room, RoomChange.Light(LightColor.MORNING)).glance)
    }
}
