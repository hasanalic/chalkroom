package app.chalkroom.data

import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.MisconceptionDto
import app.chalkroom.shared.Stage
import app.chalkroom.shared.StudentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LessonLogicTest {
    private val deck = CreateDeckResponse(
        language = "en",
        topicTitle = "Photosynthesis",
        misconceptions = listOf(
            MisconceptionDto("m1", "Plants eat soil.", "Plants make their own food."),
            MisconceptionDto("m2", "Light is heat.", "Light energy is captured by chlorophyll."),
            MisconceptionDto("m3", "Oxygen goes in.", "Oxygen is released."),
        ),
    )
    private val start = LessonLogic.startLesson(StudentId.MIRA, deck)
    private val stuck = "STUCK"
    private val answer = "ANSWER"

    private fun missTimes(n: Int, from: Lesson = start) =
        (1..n).fold(from) { l, i -> LessonLogic.applyMiss(l, "try $i", "reply $i", stuck, answer) }

    @Test
    fun `student speaks first with misconception 1`() {
        assertEquals("Plants eat soil.", start.current?.currentLine)
        assertEquals(3, start.queue.size)
        assertEquals(3, start.totalCount)
    }

    @Test
    fun `stage is SIMPLIFY only for attempt 3`() {
        val stages = (0..4).map { LessonLogic.stageFor(missTimes(it, start).copy(phase = ItemPhase.TEACHING)) }
        assertEquals(listOf(Stage.NORMAL, Stage.NORMAL, Stage.SIMPLIFY, Stage.NORMAL, Stage.NORMAL), stages)
    }

    @Test
    fun `a miss on turns 1 to 3 keeps the counter and makes the reply the line`() {
        val l = missTimes(1)
        assertEquals(3, l.queue.size)
        assertEquals(1, l.tries)
        assertEquals("reply 1", l.current?.currentLine)
        assertEquals("try 1", l.exchanges.single().explanation)
        assertFalse(LessonLogic.startOverHint(missTimes(2)))
        assertTrue(LessonLogic.startOverHint(missTimes(3)))
    }

    @Test
    fun `missed 4th try is stuck with the fixed line and no send allowed`() {
        val l = missTimes(4)
        assertEquals(ItemPhase.STUCK, l.phase)
        assertEquals(stuck, l.current?.currentLine)
        assertEquals(null, l.exchanges.last().reply)
        assertFalse(LessonLogic.canSend(l))
    }

    @Test
    fun `one last try opens attempt 5 and its miss reveals the answer`() {
        val last = LessonLogic.chooseLastTry(missTimes(4))
        assertTrue(LessonLogic.canSend(last))
        val revealed = missTimes(1, last)
        assertEquals(ItemPhase.REVEALED, revealed.phase)
        assertEquals(5, revealed.tries)
        assertFalse(LessonLogic.canSend(revealed))
        assertEquals(answer, revealed.current?.currentLine)
    }

    @Test
    fun `see the answer replaces the stuck line`() {
        val revealed = LessonLogic.revealAnswer(missTimes(4), answer)
        assertEquals(ItemPhase.REVEALED, revealed.phase)
        assertEquals(answer, revealed.current?.currentLine)
    }

    @Test
    fun `there is never a 6th try`() {
        val five = missTimes(1, LessonLogic.chooseLastTry(missTimes(4)))
        assertFalse(LessonLogic.canSend(LessonLogic.chooseLastTry(five)))
    }

    @Test
    fun `clearCurrent drops the counter by one, resets the item and records your sentence`() {
        val l = LessonLogic.clearCurrent(missTimes(2), "They make their own food.")
        assertEquals(2, l.queue.size)
        assertEquals("Light is heat.", l.current?.currentLine)
        assertEquals(0, l.tries)
        assertTrue(l.exchanges.isEmpty())
        assertEquals(ClearedItem("Plants eat soil.", "They make their own food."), l.cleared.single())
    }

    @Test
    fun `a cleared belief adds its chalk line, a blank or missing one adds none`() {
        var l = LessonLogic.clearCurrent(start, "They make their own food.", "plants make their own food")
        l = LessonLogic.clearCurrent(l, "Chlorophyll catches it.", " ")
        l = LessonLogic.clearCurrent(l, "It goes out.")
        assertEquals(listOf("plants make their own food"), l.chalkLines)
    }

    @Test
    fun `a revealed belief writes nothing on the board`() {
        val l = LessonLogic.clearRevealed(LessonLogic.revealAnswer(missTimes(4), answer))
        assertTrue(l.chalkLines.isEmpty())
    }

    @Test
    fun `clearRevealed drops the counter but stays out of What stuck`() {
        val l = LessonLogic.clearRevealed(LessonLogic.revealAnswer(missTimes(4), answer))
        assertEquals(2, l.queue.size)
        assertTrue(l.cleared.isEmpty())
        assertEquals(listOf("m1"), l.revealedIds)
        assertEquals(ItemPhase.TEACHING, l.phase)
    }

    @Test
    fun `counter only goes down across a whole lesson`() {
        var l = start
        val counts = mutableListOf(l.queue.size)
        l = missTimes(2, l); counts += l.queue.size
        l = LessonLogic.clearCurrent(l, "a"); counts += l.queue.size
        l = LessonLogic.clearRevealed(LessonLogic.revealAnswer(l, answer)); counts += l.queue.size
        l = LessonLogic.clearCurrent(missTimes(1, l), "c"); counts += l.queue.size
        assertEquals(listOf(3, 3, 2, 1, 0), counts)
        assertTrue(LessonLogic.isFinished(l))
        assertEquals(listOf("a", "c"), l.cleared.map { it.yourSentence })
    }
}
