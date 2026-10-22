package app.chalkroom.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.chalkroom.feature.classroom.room.RoomConfig
import app.chalkroom.feature.classroom.room.RoomPreset
import app.chalkroom.feature.classroom.room.RoomValues

/** How the classroom is dressed, across app restarts. Classic until something else is picked. */
interface RoomStyleRepository {
    fun load(): RoomConfig
    fun save(config: RoomConfig)
}

/** Kept beside the light switch in the "room" prefs; a missing or unknown value falls back to Classic's. */
class PrefsRoomStyleRepository(private val prefs: SharedPreferences) : RoomStyleRepository {
    constructor(context: Context) : this(context.getSharedPreferences("room", Context.MODE_PRIVATE))

    override fun load(): RoomConfig {
        val classic = RoomPreset.CLASSIC.values
        return RoomConfig(
            preset = read(PRESET, RoomPreset.CLASSIC),
            values = RoomValues(
                board = read(BOARD, classic.board),
                wall = read(WALL, classic.wall),
                desks = read(DESKS, classic.desks),
                light = read(LIGHT, classic.light),
            ),
            motto = prefs.getString(MOTTO, null).orEmpty(),
        )
    }

    override fun save(config: RoomConfig) = prefs.edit {
        putString(PRESET, config.preset.name)
        putString(BOARD, config.board.name)
        putString(WALL, config.wall.name)
        putString(DESKS, config.desks.name)
        putString(LIGHT, config.light.name)
        putString(MOTTO, config.motto)
    }

    private inline fun <reified E : Enum<E>> read(key: String, fallback: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

    private companion object {
        const val PRESET = "style_preset"
        const val BOARD = "style_board"
        const val WALL = "style_wall"
        const val DESKS = "style_desks"
        const val LIGHT = "style_light"
        const val MOTTO = "style_motto"
    }
}

