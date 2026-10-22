package app.chalkroom.feature.classroom.room

import android.os.SystemClock
import app.chalkroom.data.RoomStyleRepository

/** One pick in the Room sheet. */
sealed interface RoomChange {
    data class Preset(val preset: RoomPreset) : RoomChange
    data class Board(val board: BoardColor) : RoomChange
    data class Wall(val wall: WallColor) : RoomChange
    data class Desks(val desks: DeskStyle) : RoomChange
    data class Light(val light: LightColor) : RoomChange
    data class Motto(val text: String) : RoomChange
    data object Reset : RoomChange
}

/**
 * The rules of the Room sheet: what each pick does to the room, when it counts as Custom, and when
 * the students glance at the board. Every change is saved at once; there is no Save button.
 */
class CustomizeRoom(
    private val repository: RoomStyleRepository,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    /** [glance]: the students should glance at the board for this change. */
    data class Outcome(val config: RoomConfig, val glance: Boolean)

    private var lastGlanceAt: Long? = null

    fun load(): RoomConfig = repository.load()

    fun apply(current: RoomConfig, change: RoomChange): Outcome {
        val next = when (change) {
            // A preset sets every value it has and keeps the motto.
            is RoomChange.Preset -> current.copy(preset = change.preset, values = change.preset.values)
            is RoomChange.Board -> current.copy(values = current.values.copy(board = change.board))
            is RoomChange.Wall -> current.copy(values = current.values.copy(wall = change.wall))
            is RoomChange.Desks -> current.copy(values = current.values.copy(desks = change.desks))
            is RoomChange.Light -> current.copy(values = current.values.copy(light = change.light))
            is RoomChange.Motto -> current.copy(motto = cleanMotto(change.text))
            RoomChange.Reset -> RoomConfig()
        }
        if (next == current) return Outcome(current, glance = false)
        repository.save(next)
        val seen = next.board != current.board || next.light != current.light || next.preset != current.preset
        return Outcome(next, glance = seen && glanceAllowed())
    }

    /** A glance at most once per [GLANCE_GAP_MS]: changes in quick succession don't restack it. */
    private fun glanceAllowed(): Boolean {
        val t = now()
        val last = lastGlanceAt
        if (last != null && t - last < GLANCE_GAP_MS) return false
        lastGlanceAt = t
        return true
    }

    companion object {
        const val MOTTO_MAX = 40
        const val GLANCE_GAP_MS = 1_500L

        /** One line, at most [MOTTO_MAX] characters. */
        fun cleanMotto(text: String): String = text.replace(Regex("[\\r\\n]+"), " ").take(MOTTO_MAX)

        /** Any of the four fine-tune values differs from the picked preset's. The motto doesn't count. */
        fun isCustom(config: RoomConfig): Boolean = config.values != config.preset.values

        /** The board swatch the sheet shows as picked. */
        fun shownBoard(config: RoomConfig): BoardColor =
            if (config.board.swatch) config.board else config.preset.nearestBoard

        /** The light the sheet shows as picked. */
        fun shownLight(config: RoomConfig): LightColor =
            if (config.light.swatch) config.light else config.preset.nearestLight
    }
}
