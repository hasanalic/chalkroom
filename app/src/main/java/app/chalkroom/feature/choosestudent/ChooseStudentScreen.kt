package app.chalkroom.feature.choosestudent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiEffect
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiEvent
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Expression
import app.chalkroom.students.StudentCatalog
import app.chalkroom.students.StudentFigure
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChooseStudentScreen(
    onRead: (StudentId) -> Unit,
    onResumeLesson: (StudentId) -> Unit,
    viewModel: ChooseStudentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                is UiEffect.Read -> onRead(it.studentId)
                is UiEffect.ResumeLesson -> onResumeLesson(it.studentId)
            }
        }
    }
    ChooseStudentContent(state, viewModel::onEvent)
}

@Composable
fun ChooseStudentContent(state: UiState, onEvent: (UiEvent) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Chalkroom.colors.paper).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Who do you want to teach?", style = Chalkroom.type.title, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        state.students.forEach { choice ->
            ChoiceRow(choice, onClick = { onEvent(UiEvent.Picked(choice.studentId)) })
        }
    }
}

@Composable
private fun ChoiceRow(choice: ChooseStudentContract.Choice, onClick: () -> Unit) {
    val colors = Chalkroom.colors
    val profile = StudentCatalog[choice.studentId]
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.paperDeep)
            .border(2.dp, colors.ink.copy(alpha = 0.8f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudentFigure(choice.studentId, Expression.IDLE, Modifier.width(92.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = Chalkroom.type.title.copy(fontSize = Chalkroom.type.studentLine.fontSize))
                if (choice.paused) {
                    Text(
                        "Continue",
                        style = Chalkroom.type.label.copy(color = colors.paper),
                        modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(50)).background(colors.ink)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            Text("“${profile.intro}”", style = Chalkroom.type.body, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun ChooseStudentPreview() {
    ChalkroomTheme {
        ChooseStudentContent(
            UiState(StudentId.entries.map { ChooseStudentContract.Choice(it, paused = it == StudentId.JUNO) }),
            onEvent = {},
        )
    }
}
