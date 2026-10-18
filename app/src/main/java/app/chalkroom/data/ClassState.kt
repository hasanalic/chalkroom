package app.chalkroom.data

import app.chalkroom.shared.Exchange
import app.chalkroom.shared.StudentId

/** All app state. In memory only: gone when the process dies (prd.md > States and Boundaries > Persistence). */
data class ClassState(
    val lastNotes: String = "",
    val lessons: Map<StudentId, Lesson> = emptyMap(), // at most one per student
    val lastPausedStudentId: StudentId? = null, // drives the board
)

data class Lesson(
    val studentId: StudentId,
    val language: String,
    val topicTitle: String,
    val totalCount: Int, // motes at start; counter = queue.size
    val queue: List<MoteItem>, // head = current misconception
    val tries: Int, // failed tries on the current item (= your messages sent)
    val exchanges: List<Exchange>, // current item only; sent as priorExchanges
    val phase: ItemPhase,
    val cleared: List<ClearedItem>, // cleared by your words → What stuck
    val revealedIds: List<String>, // cleared via the answer card → not in What stuck
    val paused: Boolean,
) {
    val current: MoteItem? get() = queue.firstOrNull()
}

enum class ItemPhase { TEACHING, STUCK, REVEALED }

/** [refutation] is rendered only on the answer card. */
data class MoteItem(val id: String, val statement: String, val refutation: String, val currentLine: String)

data class ClearedItem(val statement: String, val yourSentence: String)
