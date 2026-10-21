package app.chalkroom.students

import androidx.compose.ui.graphics.Color
import app.chalkroom.shared.StudentId

enum class DeskProp { DOODLE, NOTEBOOK, PLANT }

/**
 * Everything fixed about a student. The intro, stuck, relief and chatter lines are the only student
 * lines not written by the AI (scope.md > Explicitly Cut). They're English, written once in each voice,
 * and never grade the user.
 */
data class StudentProfile(
    val id: StudentId,
    val name: String,
    val color: Color,
    /** A darker shade of [color]: mote rings, ledges, accents. */
    val shade: Color,
    val intro: String,
    /** The intro cut to two short lines for the Choose card, same voice. */
    val cardIntro: String,
    val stuckLine: String,
    /** Said while the answer card shows. */
    val answerLine: String,
    val reliefLine: String,
    val prop: DeskProp,
    /** Small talk when tapped in the classroom, in their own voice. Never about lesson content. */
    val chatter: List<String>,
)

object StudentCatalog {
    val mira = StudentProfile(
        id = StudentId.MIRA,
        name = "Mira",
        color = Color(0xFFD7F13D),
        shade = Color(0xFF7F9410),
        intro = "Hi, I'm Mira. I catch the big idea fast, but I tend to skip a step or a condition on the way.",
        cardIntro = "I get the big idea fast, but skip steps.",
        stuckLine = "I keep going round in circles on this one. We could look at the answer together, or you could try me once more.",
        answerLine = "Okay, let's read it together. I'll follow every step this time.",
        reliefLine = "Oh, that feels lighter. It all fits together now.",
        prop = DeskProp.DOODLE,
        chatter = listOf(
            "Do clouds ever get tired?",
            "Oh! Hi. I was somewhere else.",
            "I think the clock is humming.",
            "Mm… what were we doing?",
            "I like the light in here.",
        ),
    )
    val pip = StudentProfile(
        id = StudentId.PIP,
        name = "Pip",
        color = Color(0xFFFA4B7C),
        shade = Color(0xFFB81E4E),
        intro = "Pip here! I'm quick, maybe too quick. I mix up things that look alike.",
        cardIntro = "Quick, too quick. I mix up look-alikes.",
        stuckLine = "Okay, my head's all tangled. Show me the answer, or give it one more go, your call!",
        answerLine = "Ooh, the answer! Reading it slowly. Slowly, Pip.",
        reliefLine = "Whoa, my head's quiet for once! Everything's where it belongs.",
        prop = DeskProp.NOTEBOOK,
        chatter = listOf(
            "Hi! Hi. What now?",
            "Ready! Are we going?",
            "I already know it. Probably!",
            "I've been waiting ages!",
            "Pick me next!",
        ),
    )
    val juno = StudentProfile(
        id = StudentId.JUNO,
        name = "Juno",
        color = Color(0xFFEF582A),
        shade = Color(0xFFA8361A),
        intro = "I'm Juno. I always want to know why, though sometimes I pin it on the wrong reason.",
        cardIntro = "I ask why, then pin the wrong reason.",
        stuckLine = "I can't untangle this one yet. We could look at the answer, or you could try one last way.",
        answerLine = "Let's look at it, then. I want to see what the real reason is.",
        reliefLine = "Hm. Every why has its real reason now. That's a calm feeling.",
        prop = DeskProp.PLANT,
        chatter = listOf(
            "Hello… there.",
            "I'm… thinking about it.",
            "No rush. None at all.",
            "Hm. Yes. Hello again.",
            "One thing… at a time.",
        ),
    )

    val all = listOf(mira, pip, juno)

    operator fun get(id: StudentId): StudentProfile = when (id) {
        StudentId.MIRA -> mira
        StudentId.PIP -> pip
        StudentId.JUNO -> juno
    }
}
