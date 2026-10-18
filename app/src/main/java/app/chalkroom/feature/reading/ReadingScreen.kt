package app.chalkroom.feature.reading

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
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
        Modifier.fillMaxSize().background(colors.page).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.4f))
        ReadingStage(state)
        Spacer(Modifier.height(20.dp))
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "reading-phase",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { phase ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
                when (phase) {
                    Phase.READING, Phase.REVEALING -> Unit // no spinner, no percentage: the student is reading
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

private val STAGE_H = 360.dp
private val DESK_W = 250.dp

/**
 * The student at a desk with the notes drifting faintly behind them. When the deck arrives, a word
 * from the notes floats to the head for each mote that condenses.
 */
@Composable
private fun ReadingStage(state: UiState) {
    val colors = Chalkroom.colors
    val reduced = Chalkroom.reducedMotion
    val drift = if (reduced) 0f else rememberInfiniteTransition(label = "notes").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(6_000, easing = EaseInOutSine), RepeatMode.Reverse), label = "notes",
    ).value
    val words = remember(state.notesPreview) {
        state.notesPreview.split(Regex("""[^\p{L}\p{N}'-]+""")).filter { it.length >= 5 }.distinct().ifEmpty { listOf("notes") }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(STAGE_H)) {
        val w = maxWidth
        Text(
            state.notesPreview,
            style = Chalkroom.type.body.copy(color = colors.ink.copy(alpha = 0.10f)),
            maxLines = 11,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .graphicsLayer {
                    translationX = drift * 6.dp.toPx()
                    translationY = drift * -4.dp.toPx()
                    rotationZ = -1.5f
                },
        )
        StudentAtDesk(
            studentId = state.studentId,
            expression = state.expression,
            motes = state.motes,
            look = state.look,
            nodTick = state.nodTick,
            showName = false,
            modifier = Modifier.align(Alignment.BottomCenter).width(DESK_W),
        )
        // Over the head, where the motes gather.
        val head = Offset(w.value / 2, (STAGE_H - DESK_W * 1.34f).value + DESK_W.value * 0.3f)
        state.motes.forEachIndexed { i, mote ->
            key(mote.id) {
                val word = words[(i * 7 + 3) % words.size]
                val start = Offset(w.value * (0.15f + 0.7f * ((i * 0.37f + 0.2f) % 1f)), STAGE_H.value * (0.08f + 0.12f * (i % 3)))
                WordFlight(word, start, head, reduced)
            }
        }
    }
}

/** One word lifting off the notes and condensing into the head. */
@Composable
private fun WordFlight(word: String, from: Offset, to: Offset, reduced: Boolean) {
    val t = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(560, easing = EaseInOutSine)) }
    val p = t.value
    if (p >= 1f) return
    Text(
        word,
        style = Chalkroom.type.label.copy(color = Chalkroom.colors.ink),
        modifier = Modifier
            .offset(x = (from.x + (to.x - from.x) * p).dp, y = (from.y + (to.y - from.y) * p).dp)
            .graphicsLayer {
                translationX = -size.width / 2
                alpha = (if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f) * 0.7f
                scaleX = 1f - 0.5f * p
                scaleY = 1f - 0.5f * p
            },
    )
}

/** Calm, in the student's voice, with the way forward. */
@Composable
private fun Message(text: String, primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>? = null) {
    LineCard(text, Modifier.fillMaxWidth())
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton(primary.first, primary.second, Modifier.weight(1f))
        secondary?.let { QuietButton(it.first, it.second, Modifier.weight(1f)) }
    }
}

@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun ReadingPreview() {
    ChalkroomTheme {
        ReadingContent(UiState(StudentId.PIP, phase = Phase.COUNTED, motes = listOf(Mote("a"), Mote("b"), Mote("c"))), onEvent = {})
    }
}
