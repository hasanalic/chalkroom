package app.chalkroom.feature.reading

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.feature.reading.ReadingContract.Phase
import app.chalkroom.feature.reading.ReadingContract.UiEffect
import app.chalkroom.feature.reading.ReadingContract.UiEvent
import app.chalkroom.feature.reading.ReadingContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentAtDesk
import app.chalkroom.students.StudentCatalog
import app.chalkroom.ui.components.LineCard
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReadingScreen(
    studentId: StudentId,
    onLesson: () -> Unit,
    onBackToNotes: () -> Unit,
    viewModel: ReadingViewModel = koinViewModel { parametersOf(studentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { onBackToNotes() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                UiEffect.OpenLesson -> onLesson()
                UiEffect.BackToNotes -> onBackToNotes()
            }
        }
    }
    ReadingContent(state, viewModel::onEvent)
}

@Composable
fun ReadingContent(state: UiState, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    val name = StudentCatalog[state.studentId].name
    Column(
        Modifier.fillMaxSize().background(colors.paper).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.5f))
        StudentAtDesk(
            studentId = state.studentId,
            expression = state.expression,
            motes = state.motes,
            look = state.look,
            modifier = Modifier.width(250.dp),
        )
        Spacer(Modifier.height(20.dp))
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "reading-phase",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { phase ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
                when (phase) {
                    Phase.READING, Phase.REVEALING -> NotesSheet(state.notesPreview)
                    Phase.COUNTED -> LineCard(ReadingContract.countLine(state.motes.size), Modifier.fillMaxWidth())
                    Phase.EMPTY -> Message(
                        text = "There was nothing in these notes for $name to misunderstand.",
                        primary = "Back to my notes" to { onEvent(UiEvent.BackToNotes) },
                    )
                    Phase.FAILED -> Message(
                        text = "$name lost the thread. Your notes are still here.",
                        primary = "Try again" to { onEvent(UiEvent.TryAgain) },
                        secondary = "Back to my notes" to { onEvent(UiEvent.BackToNotes) },
                    )
                }
            }
        }
    }
}

/** The pasted notes, lying on the desk being read. */
@Composable
private fun NotesSheet(preview: String) {
    val colors = Chalkroom.colors
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = preview,
        style = Chalkroom.type.body.copy(color = colors.inkSoft, fontSize = Chalkroom.type.body.fontSize * 0.85f),
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .rotate(-1.5f)
            .fillMaxWidth(0.88f)
            .clip(shape)
            .background(colors.chalk)
            .border(1.5.dp, colors.ink.copy(alpha = 0.5f), shape)
            .padding(16.dp),
    )
}

@Composable
private fun Message(text: String, primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>? = null) {
    Text(text, style = Chalkroom.type.body, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        secondary?.let { QuietButton(it.first, it.second) }
        PrimaryButton(primary.first, primary.second)
    }
}

@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun ReadingPreview() {
    ChalkroomTheme {
        ReadingContent(UiState(StudentId.PIP, phase = Phase.COUNTED, motes = listOf(Mote("a"), Mote("b"), Mote("c"))), onEvent = {})
    }
}
