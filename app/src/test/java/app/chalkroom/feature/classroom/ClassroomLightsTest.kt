package app.chalkroom.feature.classroom

import app.chalkroom.data.LessonRepository
import app.chalkroom.data.RoomLights
import app.chalkroom.feature.classroom.ClassroomContract.UiEffect
import app.chalkroom.feature.classroom.ClassroomContract.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClassroomLightsTest {
    private class FakeLights(override var on: Boolean) : RoomLights {
        override fun switchOn() {
            on = true
        }
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `first open is dark and the switch turns the lights on for good`() = runTest {
        val lights = FakeLights(on = false)
        val vm = ClassroomViewModel(LessonRepository(), lights)
        assertFalse(vm.state.value.lightsOn)

        vm.onEvent(UiEvent.SwitchTapped)

        assertTrue(vm.state.value.lightsOn)
        assertTrue(lights.on)
        assertTrue(ClassroomViewModel(LessonRepository(), lights).state.value.lightsOn)
    }

    @Test
    fun `the switch with the lights on only flickers`() = runTest {
        val vm = ClassroomViewModel(LessonRepository(), FakeLights(on = true))

        vm.onEvent(UiEvent.SwitchTapped)

        assertEquals(UiEffect.Flicker, vm.effects.first())
        assertTrue(vm.state.value.lightsOn)
    }
}
