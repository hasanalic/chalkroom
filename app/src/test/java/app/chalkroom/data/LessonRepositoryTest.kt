package app.chalkroom.data

import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.MisconceptionDto
import app.chalkroom.shared.StudentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LessonRepositoryTest {
    private val deck = CreateDeckResponse(
        language = "en",
        topicTitle = "Photosynthesis",
        misconceptions = listOf(
            MisconceptionDto("m1", "Plants eat soil.", "Plants make their own food."),
            MisconceptionDto("m2", "Light is heat.", "Light energy is captured by chlorophyll."),
        ),
    )

    @Test
    fun `pause keeps the lesson exactly and puts it on the board`() {
        val repo = LessonRepository()
        repo.startLesson(StudentId.MIRA, deck)
        repo.update(StudentId.MIRA) { LessonLogic.applyMiss(it, "vague", "huh?", "STUCK") }
        val before = repo.lesson(StudentId.MIRA)!!
        repo.pause(StudentId.MIRA)
        val paused = repo.lesson(StudentId.MIRA)!!
        assertTrue(paused.paused)
        assertEquals(before.copy(paused = true), paused)
        assertEquals(StudentId.MIRA, repo.state.value.lastPausedStudentId)
    }

    @Test
    fun `several paused lessons, board falls back when the latest resumes`() {
        val repo = LessonRepository()
        repo.startLesson(StudentId.MIRA, deck)
        repo.pause(StudentId.MIRA)
        repo.startLesson(StudentId.PIP, deck)
        repo.pause(StudentId.PIP)
        assertEquals(StudentId.PIP, repo.state.value.lastPausedStudentId)

        repo.resume(StudentId.PIP)
        assertFalse(repo.lesson(StudentId.PIP)!!.paused)
        assertTrue(repo.lesson(StudentId.MIRA)!!.paused)
        assertEquals(StudentId.MIRA, repo.state.value.lastPausedStudentId)

        repo.pause(StudentId.PIP)
        repo.pause(StudentId.MIRA) // pausing again moves Mira to most recent
        assertEquals(StudentId.MIRA, repo.state.value.lastPausedStudentId)
    }

    @Test
    fun `finish removes the lesson and its board entry`() {
        val repo = LessonRepository()
        repo.startLesson(StudentId.JUNO, deck)
        repo.pause(StudentId.JUNO)
        repo.finish(StudentId.JUNO)
        assertNull(repo.lesson(StudentId.JUNO))
        assertNull(repo.state.value.lastPausedStudentId)
        repo.pause(StudentId.JUNO) // the lesson screen closing afterwards is a no-op
        assertNull(repo.state.value.lastPausedStudentId)
    }

    @Test
    fun `a new lesson for a paused student replaces it and leaves the board`() {
        val repo = LessonRepository()
        repo.startLesson(StudentId.MIRA, deck)
        repo.pause(StudentId.MIRA)
        repo.startLesson(StudentId.MIRA, deck)
        assertFalse(repo.lesson(StudentId.MIRA)!!.paused)
        assertNull(repo.state.value.lastPausedStudentId)
    }
}
