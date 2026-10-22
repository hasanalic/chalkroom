package app.chalkroom.feature.pickpart

import app.chalkroom.data.pdf.Part
import app.chalkroom.shared.StudentId

object PickPartContract {
    /** [studentName]: the preselected student, whose voice the header uses. */
    data class UiState(val parts: List<Part> = emptyList(), val selected: Int = 0, val studentName: String? = null)

    sealed interface UiEvent {
        data class Selected(val index: Int) : UiEvent
        /** "This is today's lesson". */
        data object Confirm : UiEvent
        data object Back : UiEvent
    }

    sealed interface UiEffect {
        data object ChooseStudent : UiEffect
        data class Read(val studentId: StudentId) : UiEffect
        data object Back : UiEffect
    }
}

internal fun pickPartHeader(studentName: String?): String =
    if (studentName != null) {
        "That's more than I can take in one sitting. Which part are we doing?"
    } else {
        "That's more than one sitting. Which part are we doing?"
    }
