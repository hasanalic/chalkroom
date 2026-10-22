package app.chalkroom.feature.reading

import app.chalkroom.shared.StudentId
import app.chalkroom.students.Expression
import app.chalkroom.students.Look
import app.chalkroom.students.Mote

object ReadingContract {
    /**
     * READING: eyes scan the notes while /decks runs. REVEALING: motes condense one by one.
     * COUNTED: the count is said and the student looks up. EMPTY / FAILED: calm messages, notes kept.
     */
    enum class Phase { READING, REVEALING, COUNTED, EMPTY, FAILED }

    data class UiState(
        val studentId: StudentId,
        /** The full notes: the rain behind the student is made of their words. */
        val notes: String = "",
        val phase: Phase = Phase.READING,
        val motes: List<Mote> = emptyList(),
        val expression: Expression = Expression.IDLE,
        val look: Look = Look.READING,
        /** Bumps for a small, misguided nod while reading. */
        val nodTick: Int = 0,
    )

    sealed interface UiEvent {
        data object TryAgain : UiEvent
        data object BackToNotes : UiEvent
    }

    sealed interface UiEffect {
        data object OpenLesson : UiEffect
        data object BackToNotes : UiEffect
    }

    fun countLine(n: Int): String = if (n == 1) "1 thing doesn't click yet." else "$n things don't click yet."
}
