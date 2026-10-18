package app.chalkroom.feature.pastenotes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEffect
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEvent
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiState
import app.chalkroom.shared.NoteLimits
import app.chalkroom.shared.StudentId
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.NumberFormat

@Composable
fun PasteNotesScreen(
    preselected: StudentId?,
    onChooseStudent: () -> Unit,
    onRead: (StudentId) -> Unit,
    viewModel: PasteNotesViewModel = koinViewModel { parametersOf(preselected) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                UiEffect.ChooseStudent -> onChooseStudent()
                is UiEffect.Read -> onRead(it.studentId)
            }
        }
    }
    PasteNotesContent(state, viewModel::onEvent)
}

@Composable
fun PasteNotesContent(state: UiState, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxSize().background(colors.paper).safeDrawingPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("What are you teaching?", style = Chalkroom.type.title, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Paste your notes on one topic. The students will read them.",
            style = Chalkroom.type.body.copy(color = colors.inkSoft),
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        BasicTextField(
            value = state.notes,
            onValueChange = { onEvent(UiEvent.NotesChanged(it)) },
            textStyle = Chalkroom.type.body,
            cursorBrush = SolidColor(colors.ink),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape)
                .background(colors.paperDeep)
                .border(2.dp, colors.ink, shape)
                .padding(16.dp)
                .testTag("notes"),
            decorationBox = { inner ->
                if (state.notes.isEmpty()) {
                    Text("Paste your notes here…", style = Chalkroom.type.body.copy(color = colors.inkSoft))
                }
                inner()
            },
        )
        Text(
            text = when {
                state.tooLong -> "${count(state.length)} / ${count(NoteLimits.MAX_CHARS)} characters. Trim a little to continue."
                state.tooShort && state.notes.isNotBlank() -> "A little more, please. Notes need about ${NoteLimits.MIN_CHARS} characters to learn from."
                else -> " "
            },
            style = Chalkroom.type.body.copy(color = colors.inkSoft),
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).height(50.dp),
        )
        PrimaryButton(
            text = "Continue",
            onClick = { onEvent(UiEvent.Continue) },
            enabled = state.canContinue,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun count(n: Int) = NumberFormat.getIntegerInstance(java.util.Locale.US).format(n)

@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun PasteNotesPreview() {
    ChalkroomTheme { PasteNotesContent(UiState(notes = "Photosynthesis happens in the chloroplasts."), onEvent = {}) }
}
