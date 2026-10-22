package app.chalkroom.data.pdf

import app.chalkroom.shared.StudentId

/** A PDF cut into more than one part, waiting for the user to pick today's. */
data class PartsOffer(val parts: List<Part>, val preselected: StudentId?)

/** Holds the parts in memory between Paste Notes and the part picker, never in route args. A Koin singleton. */
class PdfParts {
    var offer: PartsOffer? = null
}
