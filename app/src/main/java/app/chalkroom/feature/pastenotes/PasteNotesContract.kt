package app.chalkroom.feature.pastenotes

import app.chalkroom.data.pdf.Part
import app.chalkroom.data.pdf.PdfLimits
import app.chalkroom.shared.NoteLimits
import app.chalkroom.shared.StudentId

object PasteNotesContract {
    /**
     * [notes]: what was typed; it stays whatever happens to a file. [lastTopic]: the topic of the notes
     * the latest lesson started from, offered while the sheet is empty. [studentName]: the preselected
     * student, whose voice the PDF lines use.
     */
    data class UiState(
        val notes: String = "",
        val preselected: StudentId? = null,
        val lastTopic: String? = null,
        val studentName: String? = null,
        val sheet: Sheet = Sheet.Typing,
    ) {
        /** The server trims too, so limits count the trimmed text. */
        val length: Int get() = notes.trim().length
        val tooShort: Boolean get() = length < NoteLimits.MIN_CHARS
        val tooLong: Boolean get() = length > NoteLimits.MAX_CHARS
        val typing: Boolean get() = sheet == Sheet.Typing
        val canContinue: Boolean get() = when (sheet) {
            Sheet.Typing -> !tooShort && !tooLong
            is Sheet.Ready -> true
            else -> false
        }
        val offerLastNotes: Boolean get() = typing && lastTopic != null && notes.isBlank()
        val offerPdf: Boolean get() = typing && notes.isBlank()

        /** A file is being opened or read: Back and Stop drop it instead of leaving. */
        val busy: Boolean get() = sheet is Sheet.Opening || sheet is Sheet.Reading || sheet is Sheet.AskingPages
    }

    /** [title] comes from the file name; null when the system gave none. */
    data class PdfFile(val uri: String, val title: String?)

    /** What the sheet of paper shows. */
    sealed interface Sheet {
        data object Typing : Sheet
        sealed interface WithFile : Sheet { val file: PdfFile }
        data class Opening(override val file: PdfFile) : WithFile
        /** Too many pages: nothing is read until a range is picked. */
        data class AskingPages(override val file: PdfFile, val pageCount: Int) : WithFile
        /** [attempt] tells this read's progress apart from a stopped one's. */
        data class Reading(override val file: PdfFile, val done: Int, val total: Int, val attempt: Int = 0) : WithFile
        data class Ready(override val file: PdfFile, val pageCount: Int, val parts: List<Part>) : WithFile
        data class Failed(override val file: PdfFile, val problem: PdfProblem) : WithFile
    }

    enum class PdfProblem(val retry: Boolean) {
        UNREADABLE(retry = true),
        LOCKED(retry = false),
        TOO_BIG(retry = false),
        NO_WORDS(retry = false),
    }

    sealed interface UiEvent {
        data class NotesChanged(val notes: String) : UiEvent
        data object UseLastNotes : UiEvent
        data object Continue : UiEvent
        /** A PDF came back from the system picker; replaces any file already on the sheet. */
        data class PdfPicked(val uri: String) : UiEvent
        /** Stop, system Back while reading, and "Paste text instead": drop the file, back to the editor. */
        data object DropFile : UiEvent
        data object Retry : UiEvent
        data class ReadPages(val from: Int, val to: Int) : UiEvent
    }

    sealed interface UiEffect {
        data object ChooseStudent : UiEffect
        data class Read(val studentId: StudentId) : UiEffect
        data object PickPart : UiEffect
    }
}

/** 1 ≤ from ≤ to ≤ total, at most [PdfLimits.MAX_PAGES] pages. */
internal fun validRange(from: Int?, to: Int?, total: Int): Boolean =
    from != null && to != null && from in 1..to && to <= total && to - from + 1 <= PdfLimits.MAX_PAGES

/** Calm lines, in the student's voice when one was preselected. */
internal fun problemLine(problem: PasteNotesContract.PdfProblem, name: String?): String = when (problem) {
    PasteNotesContract.PdfProblem.UNREADABLE -> if (name != null) "$name couldn't read this file." else "Couldn't read this file."
    PasteNotesContract.PdfProblem.LOCKED ->
        if (name != null) "That file is locked. $name needs a copy without a password." else "That file is locked. Try a copy without a password."
    PasteNotesContract.PdfProblem.TOO_BIG ->
        if (name != null) "That file is too heavy for $name to carry. Try a smaller one." else "That file is too heavy. Try a smaller one."
    PasteNotesContract.PdfProblem.NO_WORDS ->
        if (name != null) {
            "$name can't find any words in there. It may be a scan; paste the text in instead."
        } else {
            "There are no words to find in there. It may be a scan; paste the text in instead."
        }
}
