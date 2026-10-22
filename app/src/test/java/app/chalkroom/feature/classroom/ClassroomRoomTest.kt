package app.chalkroom.feature.classroom

import app.chalkroom.data.LessonRepository
import app.chalkroom.data.RoomLights
import app.chalkroom.data.RoomStyleRepository
import app.chalkroom.feature.classroom.ClassroomContract.UiEffect
import app.chalkroom.feature.classroom.room.BoardColor
import app.chalkroom.feature.classroom.room.CustomizeRoom
import app.chalkroom.feature.classroom.room.RoomConfig
import app.chalkroom.feature.classroom.room.RoomPreset
import kotlinx.coroutines.flow.toList
import app.chalkroom.feature.classroom.ClassroomContract.UiEvent
import app.chalkroom.shared.StudentId
import app.chalkroom.students.StudentCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClassroomRoomTest {
    private class FakeLights(override var on: Boolean) : RoomLights

    private class MemoryRoom : RoomStyleRepository {
        var saved: RoomConfig? = null
        override fun load() = saved ?: RoomConfig()
        override fun save(config: RoomConfig) {
            saved = config
        }
    }

    private fun room(repository: RoomStyleRepository = MemoryRoom()) = CustomizeRoom(repository) { 0L }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `each tap on the switch flips the lights and remembers them`() = runTest {
        val lights = FakeLights(on = false)
        val vm = ClassroomViewModel(LessonRepository(), lights, room())
        assertFalse(vm.state.value.lightsOn)

        listOf(true, false, true, false, true).forEach { expected ->
            vm.onEvent(UiEvent.SwitchTapped)
            assertEquals(expected, vm.state.value.lightsOn)
            assertEquals(expected, lights.on)
        }
    }

    @Test
    fun `the room opens as the switch was left`() = runTest {
        val lights = FakeLights(on = true)
        ClassroomViewModel(LessonRepository(), lights, room()).onEvent(UiEvent.SwitchTapped)
        assertFalse(ClassroomViewModel(LessonRepository(), lights, room()).state.value.lightsOn)

        ClassroomViewModel(LessonRepository(), lights, room()).onEvent(UiEvent.SwitchTapped)
        assertTrue(ClassroomViewModel(LessonRepository(), lights, room()).state.value.lightsOn)
    }

    @Test
    fun `switching off ends a tap bubble at once`() = runTest {
        val vm = ClassroomViewModel(LessonRepository(), FakeLights(on = true), room())
        vm.onEvent(UiEvent.StudentTapped(StudentId.MIRA))
        assertTrue(vm.state.value.bubble != null)

        vm.onEvent(UiEvent.SwitchTapped)

        assertNull(vm.state.value.bubble)
    }

    @Test
    fun `tapping a student without a paused lesson shows one of their lines, never the same twice running`() = runTest {
        val vm = ClassroomViewModel(LessonRepository(), FakeLights(on = true), room())
        var last: String? = null
        repeat(30) {
            vm.onEvent(UiEvent.StudentTapped(StudentId.PIP))
            val bubble = vm.state.value.bubble!!
            assertEquals(StudentId.PIP, bubble.studentId)
            assertTrue(bubble.text in StudentCatalog.pip.chatter)
            assertNotEquals(last, bubble.text)
            last = bubble.text
        }
        vm.onEvent(UiEvent.StudentTapped(StudentId.MIRA))
        assertEquals(StudentId.MIRA, vm.state.value.bubble!!.studentId)
    }

    @Test
    fun `the bubble goes after its time`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = ClassroomViewModel(LessonRepository(), FakeLights(on = true), room())
        backgroundScope.launch { vm.state.collect {} }
        vm.onEvent(UiEvent.StudentTapped(StudentId.JUNO))
        runCurrent()
        assertTrue(vm.state.value.bubble != null)
        advanceTimeBy(ClassroomViewModel.BUBBLE_MS + 1)
        assertNull(vm.state.value.bubble)
    }

    @Test
    fun `room picks apply at once, are kept, and make the students glance once`() = runTest {
        val repository = MemoryRoom()
        val vm = ClassroomViewModel(LessonRepository(), FakeLights(on = true), room(repository))
        val effects = mutableListOf<UiEffect>()
        backgroundScope.launch { vm.effects.toList(effects) }

        vm.onEvent(UiEvent.CustomizeTapped)
        assertTrue(vm.state.value.customizing)
        vm.onEvent(UiEvent.PresetPicked(RoomPreset.SPACE_LAB))
        vm.onEvent(UiEvent.BoardPicked(BoardColor.BURGUNDY))
        vm.onEvent(UiEvent.CustomizeDismissed)
        runCurrent()

        val state = vm.state.value
        assertFalse(state.customizing)
        assertEquals(RoomPreset.SPACE_LAB, state.room.preset)
        assertEquals(BoardColor.BURGUNDY, state.room.board)
        assertTrue(state.roomCustom)
        assertEquals(state.room, repository.saved)
        assertEquals(listOf<UiEffect>(UiEffect.StudentsGlance), effects)
        // Back on a later launch as it was left.
        assertEquals(state.room, ClassroomViewModel(LessonRepository(), FakeLights(on = true), room(repository)).state.value.room)
    }
}
