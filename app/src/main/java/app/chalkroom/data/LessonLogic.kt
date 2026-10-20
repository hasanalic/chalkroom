package app.chalkroom.data

import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.Exchange
import app.chalkroom.shared.Stage
import app.chalkroom.shared.StudentId
import app.chalkroom.shared.TurnLimits

/** Pure lesson rules. No Android, no I/O. */
object LessonLogic {

    /** Attempt 3 asks the student to have it explained from the very start. */
    const val SIMPLIFY_ATTEMPT = 3

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

    /** The attempt about to be sent, 1-based. */
    fun attempt(lesson: Lesson): Int = lesson.tries + 1

    fun stageFor(lesson: Lesson): Stage = if (attempt(lesson) == SIMPLIFY_ATTEMPT) Stage.SIMPLIFY else Stage.NORMAL

    /** The input may take another explanation: teaching, and fewer than MAX_TURNS sent. */
    fun canSend(lesson: Lesson): Boolean =
        lesson.phase == ItemPhase.TEACHING && lesson.current != null && lesson.tries < TurnLimits.MAX_TURNS

    /** "start from the beginning" shows after the reply to attempt 3, from the 4th try on. */
    fun startOverHint(lesson: Lesson): Boolean = lesson.tries >= SIMPLIFY_ATTEMPT

    /** A missed try (turns 1–3): the reply becomes the student's line. */
    fun recordTry(lesson: Lesson, explanation: String, reply: String?): Lesson {
        val head = lesson.current ?: return lesson
        return lesson.copy(
            tries = lesson.tries + 1,
            exchanges = lesson.exchanges + Exchange(explanation, reply),
            queue = listOf(head.copy(currentLine = reply ?: head.currentLine)) + lesson.queue.drop(1),
        )
    }

    /** Attempt 4 missed: the fixed stuck line becomes the line and the two buttons show. */
    fun markStuck(lesson: Lesson, explanation: String, stuckLine: String): Lesson =
        recordTry(lesson, explanation, reply = null).let { l ->
            l.copy(phase = ItemPhase.STUCK, queue = listOf(l.queue.first().copy(currentLine = stuckLine)) + l.queue.drop(1))
        }

    /** "One last try": the input opens for attempt 5. */
    fun chooseLastTry(lesson: Lesson): Lesson =
        if (lesson.phase == ItemPhase.STUCK) lesson.copy(phase = ItemPhase.TEACHING) else lesson

    /**
     * See the answer, attempt 5 missed, or a 409: the answer card shows and [answerLine] replaces the
     * stuck line. [explanation] is the missed try, if any.
     */
    fun revealAnswer(lesson: Lesson, answerLine: String, explanation: String? = null): Lesson =
        (if (explanation != null) recordTry(lesson, explanation, reply = null) else lesson).let { l ->
            val head = l.current ?: return@let l
            l.copy(phase = ItemPhase.REVEALED, queue = listOf(head.copy(currentLine = answerLine)) + l.queue.drop(1))
        }

    /** A miss, routed by attempt: 1–3 → reply line, 4 → stuck, 5 → answer card. */
    fun applyMiss(lesson: Lesson, explanation: String, reply: String?, stuckLine: String, answerLine: String): Lesson = when (attempt(lesson)) {
        TurnLimits.JUDGE_ONLY_FROM_TURN -> markStuck(lesson, explanation, stuckLine)
        TurnLimits.MAX_TURNS -> revealAnswer(lesson, answerLine, explanation)
        else -> recordTry(lesson, explanation, reply)
    }

    /** Your words cleared the head item: it goes into What stuck, and [chalkLine] onto the board. */
    fun clearCurrent(lesson: Lesson, sentence: String, chalkLine: String? = null): Lesson {
        val head = lesson.current ?: return lesson
        return nextItem(lesson).copy(
            cleared = lesson.cleared + ClearedItem(head.statement, sentence),
            chalkLines = lesson.chalkLines + listOfNotNull(chalkLine?.takeIf { it.isNotBlank() }),
        )
    }

    /** Continue on the answer card: cleared, but not in What stuck and nothing on the board. */
    fun clearRevealed(lesson: Lesson): Lesson {
        val head = lesson.current ?: return lesson
        return nextItem(lesson).copy(revealedIds = lesson.revealedIds + head.id)
    }

    fun pause(lesson: Lesson): Lesson = lesson.copy(paused = true)

    fun resume(lesson: Lesson): Lesson = lesson.copy(paused = false)

    fun isFinished(lesson: Lesson): Boolean = lesson.queue.isEmpty()

    private fun nextItem(lesson: Lesson) =
        lesson.copy(queue = lesson.queue.drop(1), tries = 0, exchanges = emptyList(), phase = ItemPhase.TEACHING)
}
