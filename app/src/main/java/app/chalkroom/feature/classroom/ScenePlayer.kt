package app.chalkroom.feature.classroom

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import app.chalkroom.feature.classroom.ClassroomContract.Bubble
import app.chalkroom.shared.StudentId
import app.chalkroom.shared.StudentId.JUNO
import app.chalkroom.shared.StudentId.MIRA
import app.chalkroom.shared.StudentId.PIP
import app.chalkroom.students.Expression
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How a student in a scene holds themselves right now: their [face], and where they look ([lookAt] null: the face's own look). */
data class ScenePose(val face: Face, val lookAt: Offset?)

/** The closest face the student component has: eyes, lids and tilt only, no mouths. */
val Face.expression: Expression
    get() = when (this) {
        Face.HAPPY, Face.EMBARRASSED, Face.MISCHIEVOUS -> Expression.RELAXED
        Face.STARTLED, Face.EXCITED, Face.CURIOUS -> Expression.GOT_IT
        Face.PUZZLED, Face.SCEPTICAL -> Expression.PUZZLED
        Face.THINKING -> Expression.THINKING
        Face.SLEEPY, Face.AWKWARD, Face.CALM, Face.PROUD -> Expression.IDLE
    }

/** Sits up with smiling eyes. */
val Face.proud: Boolean get() = this == Face.PROUD

/** Eyes shut, head drooped: the sleep pose. */
val Face.asleep: Boolean get() = this == Face.SLEEPY

/**
 * Plays one scene in the classroom: each line in the speech bubble over its speaker, the speaker
 * turning where the line says and the student talked to turning back. Only drives what the room
 * already has: [bubble], each participant's [poses] and small [hops] and [nods]. One scene at a
 * time; cancelling [play] ends it at once, the bubble gone and everyone back to idling.
 */
@Stable
class ScenePlayer {
    var bubble by mutableStateOf<Bubble?>(null)
        private set
    var poses by mutableStateOf(emptyMap<StudentId, ScenePose>())
        private set
    var hops by mutableStateOf(StudentId.entries.associateWith { 0 })
        private set
    var nods by mutableStateOf(StudentId.entries.associateWith { 0 })
        private set

    // Negative, so a scene's bubble never equals the ViewModel's tap bubble.
    private var serial = 0

    /** With [reduced] motion the lines and faces still play, with no hops or nods. */
    suspend fun play(scene: Scene, reduced: Boolean) {
        try {
            coroutineScope {
                scene.beats.forEachIndexed { i, beat ->
                    val speaker = beat.speaker
                    val wasAsleep = poses[speaker]?.face?.asleep == true
                    pose(speaker, beat.face, gazeAt(speaker, beat.gaze, beat.face))
                    if (!reduced && !wasAsleep) {
                        when (beat.face) {
                            Face.STARTLED, Face.EXCITED -> hop(speaker)
                            Face.EMBARRASSED, Face.AWKWARD -> nod(speaker)
                            else -> Unit
                        }
                    }
                    // The one talked to turns to the speaker a moment later, with their reaction.
                    (beat.gaze as? Gaze.At)?.studentId?.let { listener ->
                        launch {
                            delay(listener.turnMs())
                            val face = beat.reaction ?: poses[listener]?.face ?: Face.CALM
                            pose(listener, face, gazeAt(listener, Gaze.At(speaker), face))
                        }
                    }
                    // Nodding off takes a moment: the sleepy line comes once the lids are down.
                    if (beat.face.asleep && !wasAsleep && !reduced) delay(DOZE_LEAD_MS)
                    val text = beat.text
                    if (text == null) {
                        delay(SILENT_MS)
                        return@forEachIndexed
                    }
                    bubble = Bubble(speaker, text, --serial)
                    if (beat.cutOff) {
                        // Caught: a short hold, a jolt, a beat, and the next line replaces it straight away.
                        delay(CUT_HOLD_MS)
                        if (!reduced) hop(speaker)
                        delay(CAUGHT_MS)
                    } else {
                        delay(lineMs(text))
                        bubble = null
                        if (i < scene.beats.lastIndex) delay(LINE_GAP_MS)
                    }
                }
            }
        } finally {
            bubble = null
            poses = emptyMap()
        }
    }

    private fun pose(id: StudentId, face: Face, lookAt: Offset?) {
        poses = poses + (id to ScenePose(face, lookAt))
    }

    private fun hop(id: StudentId) {
        hops = hops + (id to hops.getValue(id) + 1)
    }

    private fun nod(id: StudentId) {
        nods = nods + (id to nods.getValue(id) + 1)
    }

    companion object {
        const val LINE_GAP_MS = 200L
        const val CUT_HOLD_MS = 400L
        const val CAUGHT_MS = 500L
        const val SILENT_MS = 1_200L
        const val DOZE_LEAD_MS = 1_000L

        /** 1.8 s plus 45 ms a character, 2–4 s. */
        fun lineMs(text: String): Long = (1_800L + 45L * text.length).coerceIn(2_000L, 4_000L)
    }
}

/** How quickly each student turns to whoever talks to them: Pip first, Juno last. */
private fun StudentId.turnMs(): Long = when (this) {
    PIP -> 120L
    MIRA -> 250L
    JUNO -> 450L
}

/** Where [id]'s eyes go for [gaze] (fractions of the figure width), from their seat. */
private fun gazeAt(id: StudentId, gaze: Gaze, face: Face): Offset? = when (gaze) {
    Gaze.You -> Offset.Zero
    // Thinking has its own look up and to the side.
    Gaze.Up -> if (face == Face.THINKING) null else Offset(0.01f, -0.055f)
    // The prop on the left third of the desk (the same looks as the room-life prop moves).
    Gaze.Desk -> when (id) {
        MIRA -> Offset(-0.045f, 0.045f)
        PIP -> Offset(-0.04f, 0.045f)
        JUNO -> Offset(-0.05f, 0.035f)
    }
    Gaze.Notebook -> Offset(0f, 0.05f)
    is Gaze.At -> toward.getValue(id to gaze.studentId)
}

/** Mira and Juno sit side by side in the back row; Pip sits in front, between and below them. */
private val toward = mapOf(
    (MIRA to JUNO) to Offset(0.06f, -0.005f),
    (JUNO to MIRA) to Offset(-0.06f, -0.005f),
    (MIRA to PIP) to Offset(0.035f, 0.04f),
    (JUNO to PIP) to Offset(-0.035f, 0.04f),
    (PIP to MIRA) to Offset(-0.05f, -0.035f),
    (PIP to JUNO) to Offset(0.05f, -0.035f),
)
