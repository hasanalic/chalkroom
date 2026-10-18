package app.chalkroom.data

import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.StudentId

/** Pure lesson rules. No Android, no I/O. */
object LessonLogic {

    /** A fresh lesson from a deck: the student's first line is misconception 1. */
    fun startLesson(studentId: StudentId, deck: CreateDeckResponse): Lesson = Lesson(
        studentId = studentId,
        language = deck.language,
        topicTitle = deck.topicTitle,
        totalCount = deck.misconceptions.size,
        queue = deck.misconceptions.map { MoteItem(it.id, it.statement, it.refutation, currentLine = it.statement) },
        tries = 0,
        exchanges = emptyList(),
        phase = ItemPhase.TEACHING,
        cleared = emptyList(),
        revealedIds = emptyList(),
        paused = false,
    )
}
