package app.chalkroom.feature.pastenotes

import app.chalkroom.shared.NoteLimits
import app.chalkroom.shared.StudentId

object PasteNotesContract {
    data class UiState(val notes: String = "", val preselected: StudentId? = null) {
        /** The server trims too, so limits count the trimmed text. */
        val length: Int get() = notes.trim().length
        val tooShort: Boolean get() = length < NoteLimits.MIN_CHARS
        val tooLong: Boolean get() = length > NoteLimits.MAX_CHARS
        val canContinue: Boolean get() = !tooShort && !tooLong
    }

    sealed interface UiEvent {
        data class NotesChanged(val notes: String) : UiEvent
        data object Continue : UiEvent
    }

    sealed interface UiEffect {
        data object ChooseStudent : UiEffect
        data class Read(val studentId: StudentId) : UiEffect
    }
}
