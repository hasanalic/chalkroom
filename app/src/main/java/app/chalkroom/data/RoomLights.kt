package app.chalkroom.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Whether the classroom lights are on, as the switch on the wall last left them, across app
 * restarts. Off on the very first open after install; clearing the app's data turns them off again.
 */
interface RoomLights {
    var on: Boolean
}

class PrefsRoomLights(private val prefs: SharedPreferences) : RoomLights {
    constructor(context: Context) : this(context.getSharedPreferences("room", Context.MODE_PRIVATE))

    override var on: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(value) = prefs.edit { putBoolean(KEY, value) }

    private companion object {
        const val KEY = "lights_on"
    }
}
