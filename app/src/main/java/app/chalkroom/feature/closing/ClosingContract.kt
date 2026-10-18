package app.chalkroom.feature.closing

import app.chalkroom.data.ClearedItem
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Expression

object ClosingContract {
    /** Beats in order: header → relief line → What stuck → notebook closes (haptic) → the two options. */
    enum class Beat { HEADER, RELIEF, WHAT_STUCK, NOTEBOOK_CLOSED, OPTIONS }

    data class UiState(
        val studentId: StudentId,
        val topicTitle: String = "",
        val whatStuck: List<ClearedItem> = emptyList(),
        val beat: Beat = Beat.HEADER,
        val expression: Expression = Expression.GOT_IT,
        val openIndex: Int? = null,
    ) {
        fun reached(b: Beat) = beat.ordinal >= b.ordinal
    }

    sealed interface UiEvent {
        data class ToggleItem(val index: Int) : UiEvent
        data object TeachSomethingElse : UiEvent
        data object BackToClass : UiEvent
    }

    sealed interface UiEffect {
        data object SoftHaptic : UiEffect
        data class TeachAgain(val studentId: StudentId) : UiEffect
        data object BackToClass : UiEffect
    }
}
