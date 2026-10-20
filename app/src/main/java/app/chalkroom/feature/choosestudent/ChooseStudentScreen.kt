package app.chalkroom.feature.choosestudent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentAtDesk
import app.chalkroom.students.StudentCatalog
import app.chalkroom.ui.components.BUTTON_LEDGE_DP
import app.chalkroom.ui.components.CardShape
import app.chalkroom.ui.components.Ledge
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
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
        Modifier.fillMaxSize().background(Chalkroom.colors.page).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Who should learn this?", style = Chalkroom.type.headline, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        state.students.forEach { choice ->
            ChoiceCard(choice, onClick = { onEvent(UiEvent.Picked(choice.studentId)) })
        }
    }
    state.confirm?.let { ConfirmSheet(it, onEvent) }
}

/** The student already has a paused lesson: say so, and let you pick which lesson to have. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(confirm: ChooseStudentContract.Confirm, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    val name = StudentCatalog[confirm.studentId].name
    ModalBottomSheet(onDismissRequest = { onEvent(UiEvent.DismissConfirm) }, containerColor = colors.page) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$name is still on ${confirm.pausedTopic}", style = Chalkroom.type.headline)
            Text(
                "${stillOpenLine(confirm.stillOpen)} Starting your new notes ends that lesson.",
                style = Chalkroom.type.body,
            )
            PrimaryButton("Teach the new notes", { onEvent(UiEvent.StartNew) }, Modifier.fillMaxWidth().padding(top = 4.dp))
            QuietButton("Continue ${confirm.pausedTopic}", { onEvent(UiEvent.ContinuePaused) }, Modifier.fillMaxWidth())
        }
    }
}

private fun stillOpenLine(n: Int) = if (n == 1) "1 thing still doesn't click." else "$n things still don't click."

/** Each student on their own card with a ledge in their colour; pressing it pushes it down onto the ledge. */
@Composable
private fun ChoiceCard(choice: ChooseStudentContract.Choice, onClick: () -> Unit) {
    val colors = Chalkroom.colors
    val profile = StudentCatalog[choice.studentId]
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Ledge(
        shape = CardShape,
        color = colors.card,
        ledgeColor = profile.color,
        depth = BUTTON_LEDGE_DP.dp,
        pressed = pressed,
        border = colors.ink,
        modifier = Modifier.fillMaxWidth(),
        contentModifier = Modifier
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StudentAtDesk(
                choice.studentId, Expression.IDLE, Modifier.width(112.dp),
                motes = choice.motes, notebookOpen = choice.paused, showName = false,
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = Chalkroom.type.headline)
                    if (choice.paused) {
                        Text(
                            "Continue",
                            style = Chalkroom.type.small.copy(color = colors.ink, fontWeight = Chalkroom.type.label.fontWeight),
                            modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(50)).background(colors.gold)
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(profile.intro, style = Chalkroom.type.body, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun ChooseStudentPreview() {
    ChalkroomTheme {
        ChooseStudentContent(
            UiState(StudentId.entries.map { ChooseStudentContract.Choice(
                    it, paused = it == StudentId.JUNO,
                    motes = if (it == StudentId.JUNO) listOf(Mote("a"), Mote("b")) else emptyList(),
                ) }),
            onEvent = {},
        )
    }
}
