package app.chalkroom.feature.pastenotes

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import app.chalkroom.feature.pastenotes.PasteNotesContract.Sheet
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEffect
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEvent
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiState
import app.chalkroom.shared.NoteLimits
import app.chalkroom.shared.StudentId
import app.chalkroom.ui.components.Ledge
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.components.TextButton
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
    onPickPart: () -> Unit,
    viewModel: PasteNotesViewModel = koinViewModel { parametersOf(preselected) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                UiEffect.ChooseStudent -> onChooseStudent()
                is UiEffect.Read -> onRead(it.studentId)
                UiEffect.PickPart -> onPickPart()
            }
        }
    }
    // The system document picker; no storage permission. Backing out of it changes nothing.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onEvent(UiEvent.PdfPicked(uri.toString()))
    }
    // While a file is opening or reading, Back drops it instead of leaving.
    BackHandler(enabled = state.busy) { viewModel.onEvent(UiEvent.DropFile) }
    PasteNotesContent(state, viewModel::onEvent, onChoosePdf = { picker.launch(arrayOf("application/pdf")) })
}

@Composable
fun PasteNotesContent(state: UiState, onEvent: (UiEvent) -> Unit, onChoosePdf: () -> Unit = {}) {
    val colors = Chalkroom.colors
    val type = Chalkroom.type
    val lineHeight = type.body.lineHeight
    Column(
        Modifier.fillMaxSize().background(colors.page).safeDrawingPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("What are you teaching?", style = type.headline, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Paste your notes on one topic, or choose a PDF. The students will read them.",
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
            when (val sheet = state.sheet) {
                is Sheet.WithFile -> PdfOnSheet(sheet, state.studentName, onEvent, onChoosePdf, lineHeight)
                Sheet.Typing -> BasicTextField(
                    value = state.notes,
                    onValueChange = { onEvent(UiEvent.NotesChanged(it)) },
                    textStyle = type.body,
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier
                        .fillMaxSize()
                        .ruledPaper(lineHeight)
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
        }
        (state.sheet as? Sheet.AskingPages)?.let { asking ->
            PagesDialog(
                pageCount = asking.pageCount,
                studentName = state.studentName,
                onRead = { from, to -> onEvent(UiEvent.ReadPages(from, to)) },
                onNeverMind = { onEvent(UiEvent.DropFile) },
            )
        }
        // Grows with its lines instead of a fixed height, so the count never slides under the button.
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp).heightIn(min = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val ready = state.sheet as? Sheet.Ready
            when {
                ready != null -> {
                    val parts = ready.parts
                    if (parts.size > 1) {
                        Text("In ${parts.size} parts — you'll pick one", style = type.label, textAlign = TextAlign.Center)
                    } else {
                        val sent = parts.single().text.length
                        Text(amountLine(sent), style = type.label, textAlign = TextAlign.Center)
                        Text("${count(sent)} / ${count(NoteLimits.MAX_CHARS)} characters", style = type.small, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                !state.typing -> Text(" ", style = type.label)
                state.offerPdf -> {
                    if (state.offerLastNotes) {
                        QuietButton(
                            text = "Use your notes on ${state.lastTopic} again",
                            onClick = { onEvent(UiEvent.UseLastNotes) },
                            modifier = Modifier.testTag("useLastNotes"),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    TextButton("Or choose a PDF", onChoosePdf, Modifier.testTag("choosePdf"))
                }
                else -> {
                    Text(
                        text = when {
                            state.tooLong -> "A little too much for one go. Trim it to continue."
                            state.tooShort -> "A little more, please. Notes need about ${NoteLimits.MIN_CHARS} characters to learn from."
                            else -> amountLine(state.length)
                        },
                        style = type.label,
                        textAlign = TextAlign.Center,
                    )
                    Text("${count(state.length)} / ${count(NoteLimits.MAX_CHARS)} characters", style = type.small, modifier = Modifier.padding(top = 2.dp))
                }
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

/** How much is there, as reading time for a person: about [CHARS_PER_MINUTE] characters a minute. */
internal fun amountLine(chars: Int): String = when (val minutes = (chars + CHARS_PER_MINUTE / 2) / CHARS_PER_MINUTE) {
    0 -> "Under a minute to read"
    1 -> "About a minute to read"
    else -> "About $minutes minutes to read"
}

private const val CHARS_PER_MINUTE = 1_100

internal fun count(n: Int) = NumberFormat.getIntegerInstance(java.util.Locale.US).format(n)

@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun PasteNotesPreview() {
    ChalkroomTheme { PasteNotesContent(UiState(notes = "Photosynthesis happens in the chloroplasts."), onEvent = {}) }
}
