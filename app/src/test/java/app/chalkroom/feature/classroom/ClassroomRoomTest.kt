package app.chalkroom.feature.classroom

import app.chalkroom.data.LessonRepository
import app.chalkroom.data.RoomLights
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

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `each tap on the switch flips the lights and remembers them`() = runTest {
        val lights = FakeLights(on = false)
        val vm = ClassroomViewModel(LessonRepository(), lights)
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
        ClassroomViewModel(LessonRepository(), lights).onEvent(UiEvent.SwitchTapped)
        assertFalse(ClassroomViewModel(LessonRepository(), lights).state.value.lightsOn)

        ClassroomViewModel(LessonRepository(), lights).onEvent(UiEvent.SwitchTapped)
        assertTrue(ClassroomViewModel(LessonRepository(), lights).state.value.lightsOn)
    }

    @Test
    fun `switching off ends a tap bubble at once`() = runTest {
        val vm = ClassroomViewModel(LessonRepository(), FakeLights(on = true))
        vm.onEvent(UiEvent.StudentTapped(StudentId.MIRA))
        assertTrue(vm.state.value.bubble != null)

        vm.onEvent(UiEvent.SwitchTapped)

        assertNull(vm.state.value.bubble)
    }

    @Test
    fun `tapping a student without a paused lesson shows one of their lines, never the same twice running`() = runTest {
        val vm = ClassroomViewModel(LessonRepository(), FakeLights(on = true))
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
        val vm = ClassroomViewModel(LessonRepository(), FakeLights(on = true))
        backgroundScope.launch { vm.state.collect {} }
        vm.onEvent(UiEvent.StudentTapped(StudentId.JUNO))
        runCurrent()
        assertTrue(vm.state.value.bubble != null)
        advanceTimeBy(ClassroomViewModel.BUBBLE_MS + 1)
        assertNull(vm.state.value.bubble)
    }
}
