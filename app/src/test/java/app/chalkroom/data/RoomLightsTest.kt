package app.chalkroom.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoomLightsTest {
    @Test
    fun `the lights are off on the very first open`() {
        assertFalse(PrefsRoomLights(FakePrefs()).on)
    }

    @Test
    fun `the lights stay as they were left across restarts`() {
        val prefs = FakePrefs()

        PrefsRoomLights(prefs).on = true
        assertTrue(PrefsRoomLights(prefs).on)

        PrefsRoomLights(prefs).on = false
        assertFalse(PrefsRoomLights(prefs).on)
        assertTrue(prefs.contains("lights_on"))

        PrefsRoomLights(prefs).on = true
        assertTrue(PrefsRoomLights(prefs).on)
    }
}
