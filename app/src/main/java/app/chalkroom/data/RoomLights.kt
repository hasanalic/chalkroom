package app.chalkroom.data

import android.content.Context
import androidx.core.content.edit

/**
 * Whether the classroom lights have ever been switched on. Off only on the very first open after
 * install; clearing the app's data turns them off again.
 */
interface RoomLights {
    val on: Boolean
    fun switchOn()
}

class PrefsRoomLights(context: Context) : RoomLights {
    private val prefs = context.getSharedPreferences("room", Context.MODE_PRIVATE)

    override val on: Boolean get() = prefs.getBoolean(KEY, false)

    override fun switchOn() {
        prefs.edit { putBoolean(KEY, true) }
    }

    private companion object {
        const val KEY = "lights_on"
    }
}
