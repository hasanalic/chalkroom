package app.chalkroom.data

import android.content.SharedPreferences
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoomLightsTest {
    /** Just enough SharedPreferences for booleans; outlives every PrefsRoomLights like the real file does. */
    private class FakePrefs : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun contains(key: String) = key in values
        override fun getAll(): Map<String, *> = values
        override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?) = defValues
        override fun getInt(key: String, defValue: Int) = defValue
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) = this
            override fun putInt(key: String, value: Int) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun remove(key: String) = this
            override fun clear() = this
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                values.putAll(pending)
            }
        }
    }

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
