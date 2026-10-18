package app.chalkroom.feature.pastenotes

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEffect
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEvent
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiState
import app.chalkroom.shared.NoteLimits
import app.chalkroom.shared.StudentId
import app.chalkroom.ui.components.Ledge
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import java.text.NumberFormat
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
    val type = Chalkroom.type
    val lineHeight = type.body.lineHeight
    Column(
        Modifier.fillMaxSize().background(colors.page).safeDrawingPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("What are you teaching?", style = type.headline, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Paste your notes on one topic. The students will read them.",
            style = type.body.copy(color = colors.muted),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        // One sheet of paper: white, faint ruled lines, a slight ledge under it.
        Ledge(
            shape = RoundedCornerShape(10.dp),
            color = colors.card,
            ledgeColor = colors.line,
            border = colors.line,
            borderWidth = 1.5.dp,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentModifier = Modifier.fillMaxSize(),
        ) {
            BasicTextField(
                value = state.notes,
                onValueChange = { onEvent(UiEvent.NotesChanged(it)) },
                textStyle = type.body,
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val step = lineHeight.toPx()
                        var y = SHEET_PAD.toPx() + step
                        while (y < size.height - 8.dp.toPx()) {
                            drawLine(colors.lavender2.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            y += step
                        }
                        drawLine(colors.coral.copy(alpha = 0.35f), Offset(SHEET_PAD.toPx() - 6.dp.toPx(), 0f), Offset(SHEET_PAD.toPx() - 6.dp.toPx(), size.height), 1.dp.toPx())
                    }
                    .padding(start = SHEET_PAD + 4.dp, end = 16.dp, top = SHEET_PAD, bottom = 12.dp)
                    .testTag("notes"),
                decorationBox = { inner ->
                    if (state.notes.isEmpty()) {
                        Text("Paste your notes here…", style = type.body.copy(color = colors.muted))
                    }
                    inner()
                },
            )
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp).height(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when {
                    state.tooLong -> "A little too much for one go. Trim it to continue."
                    state.tooShort && state.notes.isNotBlank() -> "A little more, please. Notes need about ${NoteLimits.MIN_CHARS} characters to learn from."
                    state.notes.isBlank() -> " "
                    else -> amountLine(state.length)
                },
                style = type.label,
                textAlign = TextAlign.Center,
            )
            if (state.notes.isNotBlank()) {
                Text("${count(state.length)} / ${count(NoteLimits.MAX_CHARS)} characters", style = type.small, modifier = Modifier.padding(top = 2.dp))
            }
        }
        PrimaryButton(
            text = "Continue",
            onClick = { onEvent(UiEvent.Continue) },
            enabled = state.canContinue,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}

private val SHEET_PAD = 22.dp

/** How much is there, as a fact about a person reading it. */
private fun amountLine(chars: Int): String = when {
    chars <= 700 -> "A two-minute read"
    chars <= 2_500 -> "About one sitting"
    chars <= 5_000 -> "A long sitting"
    else -> "An evening's reading"
}

private fun count(n: Int) = NumberFormat.getIntegerInstance(java.util.Locale.US).format(n)

@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun PasteNotesPreview() {
    ChalkroomTheme { PasteNotesContent(UiState(notes = "Photosynthesis happens in the chloroplasts."), onEvent = {}) }
}
