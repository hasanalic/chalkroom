package app.chalkroom.feature.classroom

import app.chalkroom.shared.StudentId
import app.chalkroom.shared.StudentId.JUNO
import app.chalkroom.shared.StudentId.MIRA
import app.chalkroom.shared.StudentId.PIP

/** How a student looks while they say a line; the classroom maps each to the closest face the student component has. */
enum class Face { HAPPY, SLEEPY, STARTLED, EMBARRASSED, PUZZLED, SCEPTICAL, MISCHIEVOUS, EXCITED, AWKWARD, CALM, THINKING, CURIOUS, PROUD }

/** Where the speaker looks. */
sealed interface Gaze {
    /** At another student, who turns back to the speaker. */
    data class At(val studentId: StudentId) : Gaze

    /** Out of the screen, at the user. */
    data object You : Gaze
    data object Up : Gaze

    /** Down at their own desk and its prop (Mira's doodle, Pip's notebook, Juno's plant). */
    data object Desk : Gaze

    /** Down at a paused lesson's open notebook in the middle of the desk. */
    data object Notebook : Gaze
}

/**
 * One beat of a scene: [speaker] says [text] (null: a silent beat, only the face and the look)
 * with [face], looking at [gaze]. The student looked at, if any, turns to the speaker and shows
 * [reaction] when it is given. A line ending in "—" is cut off.
 */
data class Beat(
    val speaker: StudentId,
    val face: Face,
    val gaze: Gaze,
    val text: String?,
    val reaction: Face? = null,
) {
    val cutOff: Boolean get() = text?.endsWith("—") == true
}

/** ENTRY plays as you walk in, AMBIENT now and then, PAUSED only while [Scene.needsPaused] has a paused lesson. */
enum class SceneKind { ENTRY, AMBIENT, PAUSED }

data class Scene(val id: String, val kind: SceneKind, val beats: List<Beat>, val needsPaused: StudentId? = null)

/** Every scripted scene in the classroom. */
object Scenes {
    private fun entry(id: String, vararg beats: Beat) = Scene(id, SceneKind.ENTRY, beats.toList())
    private fun ambient(id: String, vararg beats: Beat) = Scene(id, SceneKind.AMBIENT, beats.toList())
    private fun paused(id: String, student: StudentId, vararg beats: Beat) = Scene(id, SceneKind.PAUSED, beats.toList(), needsPaused = student)

    val all: List<Scene> = listOf(
        entry(
            "E1",
            Beat(PIP, Face.SLEEPY, Gaze.Desk, "Mm… five more minu—"),
            Beat(PIP, Face.STARTLED, Gaze.You, "Awake. Very awake. Always."),
        ),
        entry(
            "E2",
            Beat(JUNO, Face.HAPPY, Gaze.Desk, "…and that's why you're my favourite pla—"),
            Beat(JUNO, Face.EMBARRASSED, Gaze.You, "We were discussing sunlight."),
        ),
        entry("E3", Beat(MIRA, Face.HAPPY, Gaze.You, "Hi! I practised waving.")),
        entry("E4", Beat(MIRA, Face.EMBARRASSED, Gaze.Desk, "Oh! I wasn't drawing you.")),
        entry("E5", Beat(MIRA, Face.PUZZLED, Gaze.You, "Hi. I had a question… it'll come back to me.")),

        ambient(
            "A1",
            Beat(MIRA, Face.PROUD, Gaze.Desk, "It's definitely a sheep now."),
            Beat(PIP, Face.SCEPTICAL, Gaze.At(MIRA), "That sheep has a lightning bolt."),
            Beat(MIRA, Face.EMBARRASSED, Gaze.At(PIP), "It's an upset sheep."),
        ),
        ambient(
            "A2",
            Beat(PIP, Face.MISCHIEVOUS, Gaze.At(MIRA), "Psst. Mira."),
            Beat(MIRA, Face.EXCITED, Gaze.At(PIP), "WHAT?"),
            Beat(PIP, Face.AWKWARD, Gaze.Desk, "Never mind. Everyone heard."),
        ),
        ambient(
            "A3",
            Beat(PIP, Face.PUZZLED, Gaze.At(JUNO), "Why is my pencil in your plant?"),
            Beat(JUNO, Face.CALM, Gaze.At(PIP), "It looked lonely.", reaction = Face.PUZZLED),
        ),
        ambient(
            "A4",
            Beat(JUNO, Face.CALM, Gaze.At(PIP), "I'm early because the clock is slow."),
            Beat(PIP, Face.PUZZLED, Gaze.At(JUNO), "Isn't that backwards?"),
            Beat(JUNO, Face.THINKING, Gaze.Up, "Only if you start at the front."),
        ),
        ambient(
            "A5",
            Beat(JUNO, Face.THINKING, Gaze.Up, "Mornings start when the birds wake up."),
            Beat(MIRA, Face.CURIOUS, Gaze.At(JUNO), "Not the other way round?"),
            Beat(JUNO, Face.PUZZLED, Gaze.Up, "…now I'm not sure about mornings."),
        ),
        ambient(
            "A6",
            Beat(PIP, Face.PUZZLED, Gaze.At(JUNO), "Are you reading upside down?"),
            Beat(JUNO, Face.CALM, Gaze.Desk, "No. The book is."),
        ),
        ambient(
            "A7",
            Beat(JUNO, Face.THINKING, Gaze.You, "Sometimes I think the desk is thinking too."),
            Beat(MIRA, Face.AWKWARD, Gaze.At(JUNO), null),
            Beat(JUNO, Face.CALM, Gaze.Desk, "It agrees with me, mostly."),
        ),
        ambient(
            "A8",
            Beat(MIRA, Face.CURIOUS, Gaze.At(PIP), "How do you spin the pencil like that?"),
            Beat(PIP, Face.PROUD, Gaze.At(MIRA), "No idea. Don't ask it to stop."),
        ),
        ambient(
            "A9",
            Beat(MIRA, Face.MISCHIEVOUS, Gaze.Desk, "If I tap very quietly, nobody will kn—"),
            Beat(MIRA, Face.EMBARRASSED, Gaze.You, "…you heard."),
        ),
        ambient(
            "A10",
            Beat(MIRA, Face.CURIOUS, Gaze.At(JUNO), "What are you thinking about?"),
            Beat(JUNO, Face.THINKING, Gaze.Up, "Whether I understand what I'm thinking about."),
        ),

        paused(
            "P1",
            PIP,
            Beat(JUNO, Face.SCEPTICAL, Gaze.At(PIP), "Your notebook's still open."),
            Beat(PIP, Face.PROUD, Gaze.At(JUNO), "It's taking a break. So am I."),
        ),
        paused("P2", MIRA, Beat(MIRA, Face.HAPPY, Gaze.Notebook, "My notebook's still here. I checked.")),
        paused(
            "P3",
            JUNO,
            Beat(MIRA, Face.CURIOUS, Gaze.At(JUNO), "Are you going back to that one?"),
            Beat(JUNO, Face.CALM, Gaze.Notebook, "Eventually. It's resting."),
        ),
    )
}
