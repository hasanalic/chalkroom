package app.chalkroom.shared

import kotlinx.serialization.Serializable

@Serializable
data class CreateDeckRequest(
    val notes: String,
    val studentId: StudentId,
)

/** An empty [misconceptions] list means the notes had nothing to misunderstand. */
@Serializable
data class CreateDeckResponse(
    val language: String,
    val topicTitle: String,
    val misconceptions: List<MisconceptionDto>,
)

/** [refutation] is judge-only context; the app renders it only on the answer card. */
@Serializable
data class MisconceptionDto(
    val id: String,
    val statement: String,
    val refutation: String,
)

@Serializable
data class TurnRequest(
    val statement: String,
    val refutation: String,
    val studentId: StudentId,
    val stage: Stage,
    val language: String,
    val explanation: String,
    val priorExchanges: List<Exchange>,
)

/** [reply] is null for a judge-only miss (turn 4 and 5). The app leaves nulls out, hence the default. */
@Serializable
data class Exchange(
    val explanation: String,
    val reply: String? = null,
)

/**
 * [chalkLine] is the user's point in 4–8 of their own words, for the board; only when [addressed],
 * and null when the model gave none.
 */
@Serializable
data class TurnResponse(
    val addressed: Boolean,
    val reply: String? = null,
    val chalkLine: String? = null,
)

@Serializable
data class ErrorBody(val code: String)

object ErrorCodes {
    const val NOTES_LENGTH = "notes_length"
    const val EXPLANATION_LENGTH = "explanation_length"
    const val TURN_LIMIT = "turn_limit"
    const val AI_UNAVAILABLE = "ai_unavailable"
    const val RATE_LIMITED = "rate_limited"
    const val BAD_REQUEST = "bad_request"
}

object NoteLimits {
    const val MIN_CHARS = 150
    const val MAX_CHARS = 8_000
}

object TurnLimits {
    const val MAX_TURNS = 5
    const val JUDGE_ONLY_FROM_TURN = 4
    const val MAX_EXPLANATION_CHARS = 2_000
}
