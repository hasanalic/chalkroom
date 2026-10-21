package app.chalkroom.feature.classroom

import app.chalkroom.shared.StudentId
import kotlin.random.Random

/**
 * Which scene the classroom plays next, kept for the session (memory only). A visit opens with an
 * entry scene, unless it starts within [ENTRY_AGAIN_MS] of the last visit ending, so hopping in and
 * out doesn't replay one. After that come ambient scenes, plus a paused-lesson scene while its
 * student has a paused lesson. Never the same scene twice in a row, and every eligible scene not
 * yet played this session comes before any repeat.
 */
class ScenePicker(private val scenes: List<Scene> = Scenes.all, private val random: Random = Random.Default) {
    private val played = mutableSetOf<String>()
    private var last: String? = null
    private var leftAt = Long.MIN_VALUE / 2

    /** The Classroom came on screen at [now]: its entry scene, or null when it's a quick return. */
    fun visitStarted(now: Long): Scene? {
        if (now - leftAt < ENTRY_AGAIN_MS) return null
        return pick(scenes.filter { it.kind == SceneKind.ENTRY })
    }

    /** The Classroom left the screen at [now]. */
    fun visitEnded(now: Long) {
        leftAt = now
    }

    /** The next scene for a room whose [paused] students have a paused lesson. */
    fun nextAmbient(paused: Set<StudentId>): Scene = pick(
        scenes.filter { it.kind == SceneKind.AMBIENT || (it.kind == SceneKind.PAUSED && it.needsPaused in paused) },
    )

    private fun pick(eligible: List<Scene>): Scene {
        val fresh = eligible.filter { it.id != last }
        val unplayed = fresh.filter { it.id !in played }
        val scene = unplayed.ifEmpty { fresh.ifEmpty { eligible } }.random(random)
        played += scene.id
        last = scene.id
        return scene
    }

    companion object {
        const val ENTRY_AGAIN_MS = 25_000L

        /** The one picker for this process. */
        val session = ScenePicker()
    }
}
