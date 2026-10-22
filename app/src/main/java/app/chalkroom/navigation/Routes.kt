package app.chalkroom.navigation

import app.chalkroom.shared.StudentId
import kotlinx.serialization.Serializable

// Routes carry no lesson data: each screen reads LessonRepository by studentId.

@Serializable
data object Classroom

@Serializable
data class PasteNotes(val preselected: StudentId? = null)

/** Parts live in PdfParts, not in the route. */
@Serializable
data object PickPart

@Serializable
data object ChooseStudent

@Serializable
data class Reading(val studentId: StudentId)

@Serializable
data class Lesson(val studentId: StudentId)

@Serializable
data class Closing(val studentId: StudentId)
