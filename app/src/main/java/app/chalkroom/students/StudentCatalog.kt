package app.chalkroom.students

import androidx.compose.ui.graphics.Color
import app.chalkroom.shared.StudentId

enum class DeskProp { DOODLE, NOTEBOOK, PLANT }

/**
 * Everything fixed about a student. The intro, stuck and relief lines are the only student lines
 * not written by the AI (scope.md > Explicitly Cut). They're English, written once in each voice,
 * and never grade the user.
 */
data class StudentProfile(
    val id: StudentId,
    val name: String,
    val color: Color,
    /** A darker shade of [color]: mote rings, ledges, accents. */
    val shade: Color,
    val intro: String,
    val stuckLine: String,
    val reliefLine: String,
    val prop: DeskProp,
)

object StudentCatalog {
    val mira = StudentProfile(
        id = StudentId.MIRA,
        name = "Mira",
        color = Color(0xFFD7F13D),
        shade = Color(0xFF7F9410),
        intro = "Hi, I'm Mira. I catch the big idea fast, but I tend to skip a step or a condition on the way.",
        stuckLine = "I keep going round in circles on this one. We could look at the answer together, or you could try me once more.",
        reliefLine = "Oh, that feels lighter. It all fits together now.",
        prop = DeskProp.DOODLE,
    )
    val pip = StudentProfile(
        id = StudentId.PIP,
        name = "Pip",
        color = Color(0xFFFA4B7C),
        shade = Color(0xFFB81E4E),
        intro = "Pip here! I'm quick, maybe too quick. I mix up things that look alike.",
        stuckLine = "Okay, my head's all tangled. Show me the answer, or give it one more go, your call!",
        reliefLine = "Whoa, my head's quiet for once! Everything's where it belongs.",
        prop = DeskProp.NOTEBOOK,
    )
    val juno = StudentProfile(
        id = StudentId.JUNO,
        name = "Juno",
        color = Color(0xFFEF582A),
        shade = Color(0xFFA8361A),
        intro = "I'm Juno. I like to ask why, though sometimes I get what causes what backwards.",
        stuckLine = "I can't untangle this one yet. We could look at the answer, or you could try one last way.",
        reliefLine = "Hm. Now everything points the right way. That's a calm feeling.",
        prop = DeskProp.PLANT,
    )

    val all = listOf(mira, pip, juno)

    operator fun get(id: StudentId): StudentProfile = when (id) {
        StudentId.MIRA -> mira
        StudentId.PIP -> pip
        StudentId.JUNO -> juno
    }
}
