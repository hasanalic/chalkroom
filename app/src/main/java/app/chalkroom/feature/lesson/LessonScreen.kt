package app.chalkroom.feature.lesson

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.data.Lesson
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Expression
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentCatalog
import app.chalkroom.students.StudentFigure
import app.chalkroom.ui.components.LineCard
import app.chalkroom.ui.theme.Chalkroom
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LessonScreen(studentId: StudentId, viewModel: LessonViewModel = koinViewModel { parametersOf(studentId) }) {
    val lesson by viewModel.lesson.collectAsStateWithLifecycle()
    lesson?.let { LessonContent(it) }
}

@Composable
fun LessonContent(lesson: Lesson) {
    val colors = Chalkroom.colors
    val name = StudentCatalog[lesson.studentId].name
    Column(
        Modifier.fillMaxSize().background(colors.paper).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        StudentFigure(lesson.studentId, Expression.PUZZLED, Modifier.width(190.dp), motes = lesson.queue.map { Mote(it.id) })
        Text("what's left in $name's head: ${lesson.queue.size}", style = Chalkroom.type.label, modifier = Modifier.padding(top = 10.dp))
        val fraction = if (lesson.totalCount == 0) 0f else lesson.queue.size.toFloat() / lesson.totalCount
        Canvas(Modifier.padding(top = 8.dp).width(180.dp).height(8.dp)) {
            drawRoundRect(colors.paperDeep, cornerRadius = CornerRadius(size.height / 2))
            drawRoundRect(colors.ink, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
        }
        Spacer(Modifier.height(22.dp))
        lesson.current?.let { LineCard(it.currentLine, Modifier.fillMaxWidth()) }
    }
}
