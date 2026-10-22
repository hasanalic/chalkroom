package app.chalkroom.feature.reading

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.feature.reading.ReadingContract.Phase
import app.chalkroom.feature.reading.ReadingContract.UiEffect
import app.chalkroom.feature.reading.ReadingContract.UiEvent
import app.chalkroom.feature.reading.ReadingContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.FIGURE_ASPECT
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentCatalog
import app.chalkroom.students.StudentFigure
import app.chalkroom.students.StudentShapes
import app.chalkroom.ui.components.LightStatusBarIcons
import app.chalkroom.ui.components.LineCard
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun ReadingScreen(
    studentId: StudentId,
    onLesson: () -> Unit,
    onBackToNotes: () -> Unit,
    viewModel: ReadingViewModel = koinViewModel { parametersOf(studentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { onBackToNotes() }
    LightStatusBarIcons(navigationBar = true)
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
    val profile = StudentCatalog[state.studentId]
    val name = profile.name
    val rainText = remember(state.notes) { RainText.from(state.notes) }
    val studentArea = remember { mutableStateOf(Rect.Zero) }
    // The slate runs edge to edge, up under the status bar; the rain fills all of it.
    Box(Modifier.fillMaxSize().background(colors.ink)) {
        NotesRainBackground(
            text = rainText,
            highlight = profile.color,
            calm = state.phase == Phase.COUNTED || state.phase == Phase.EMPTY || state.phase == Phase.FAILED,
            focus = { studentArea.value },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.4f))
            ReadingStage(state, rainText, onStudentArea = { studentArea.value = it })
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
                        Phase.COUNTED -> ReadingLineCard(ReadingContract.countLine(state.motes.size))
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
}

private val FIGURE_W = 224.dp
private val FIGURE_H = FIGURE_W * FIGURE_ASPECT
/** Room above the figure where the words lift out of the rain. */
private val FLIGHT_ROOM = 56.dp
private val STAGE_H = FIGURE_H + FLIGHT_ROOM
/** Margin around the figure for the motes' wobble and the last mote's bigger size. */
private val MOTES_MARGIN = 12.dp
/** How many rain rows the bust's base takes to dissolve: all of them below the eyes. */
private const val DISSOLVE_ROWS = 4

/**
 * The student alone over the notes rain, the base of the bust dissolving into it. When the deck
 * arrives, a highlighted word from the notes floats to the head, in the student's colour, for each
 * mote that condenses.
 */
@Composable
private fun ReadingStage(state: UiState, rainText: RainText, onStudentArea: (Rect) -> Unit) {
    val reduced = Chalkroom.reducedMotion
    val color = StudentCatalog[state.studentId].color
    val words = remember(rainText) {
        rainText.keyWords.ifEmpty { rainText.words.filter { it.length >= 5 }.distinct() }.ifEmpty { listOf("notes") }
    }
    val density = LocalDensity.current
    val margin = with(density) { MOTES_MARGIN.toPx() }
    // The rain comes back up into the dissolving base, so the calm area stops halfway into it.
    val dissolveH = with(density) { (GLYPH_SP * GLYPH_H).sp.toPx() } * DISSOLVE_ROWS
    BoxWithConstraints(Modifier.fillMaxWidth().height(STAGE_H)) {
        val w = maxWidth
        StudentFigure(
            studentId = state.studentId,
            expression = state.expression,
            motes = state.motes,
            look = state.look,
            nodTick = state.nodTick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(FIGURE_W)
                .dissolveBase(DISSOLVE_ROWS)
                .onGloballyPositioned {
                    val b = it.boundsInRoot()
                    onStudentArea(Rect(b.left - margin, b.top - margin, b.right + margin, b.bottom - dissolveH / 2))
                },
        )
        // The head, on the figure as it is drawn: the face's centre, a little above the eyes.
        val face = StudentShapes.face(state.studentId)
        val head = Offset(
            (w - FIGURE_W).value / 2 + face.center.x * FIGURE_W.value,
            FLIGHT_ROOM.value + (face.center.y - HEAD_ABOVE_EYES) * FIGURE_W.value,
        )
        state.motes.forEachIndexed { i, mote ->
            key(mote.id) {
                val word = words[(i * 7 + 3) % words.size]
                val start = Offset(w.value * (0.15f + 0.7f * ((i * 0.37f + 0.2f) % 1f)), STAGE_H.value * (0.08f + 0.12f * (i % 3)))
                WordFlight(word, start, head, color, reduced)
            }
        }
    }
}

/** From the eyes up to the middle of the head, in figure widths. */
private const val HEAD_ABOVE_EYES = 0.12f

/**
 * The bottom [rows] rows of the rain grid dissolve the bust cell by cell, the way a stream's trail
 * fades: each row up keeps a quarter more of the bust, some columns a step behind their
 * neighbours (seeded) so the edge isn't a line. Alpha only: every cell is one flat value, no
 * gradient and no blur. Cells are snapped to whole pixels so no seams show between them.
 */
private fun Modifier.dissolveBase(rows: Int): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val cellH = (GLYPH_SP * GLYPH_H).sp.toPx()
            val cellW = COLUMN_W.toPx()
            val cols = ceil(size.width / cellW).toInt()
            val random = Random(7)
            for (c in 0 until cols) {
                val lag = random.nextInt(2)
                val left = (c * cellW).roundToInt().toFloat()
                val right = minOf(((c + 1) * cellW).roundToInt().toFloat(), size.width)
                for (r in 0 until rows) {
                    val keep = ((r + lag).toFloat() / rows).coerceAtMost(1f)
                    if (keep >= 1f) continue
                    val bottom = (size.height - r * cellH).roundToInt().toFloat()
                    val top = (size.height - (r + 1) * cellH).roundToInt().toFloat()
                    drawRect(
                        Color.Black.copy(alpha = 1f - keep),
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
        }

/** One word lifting out of the rain and condensing into the head. */
@Composable
private fun WordFlight(word: String, from: Offset, to: Offset, color: Color, reduced: Boolean) {
    val t = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(560, easing = EaseInOutSine)) }
    val p = t.value
    if (p >= 1f) return
    Text(
        word,
        style = Chalkroom.type.label.copy(color = color),
        modifier = Modifier
            .offset(x = (from.x + (to.x - from.x) * p).dp, y = (from.y + (to.y - from.y) * p).dp)
            .graphicsLayer {
                translationX = -size.width / 2
                alpha = (if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f) * 0.95f
                scaleX = 1f - 0.5f * p
                scaleY = 1f - 0.5f * p
            },
    )
}

/** The student's line on the dark slate: a lighter surface with a chalk outline, so the card reads. */
@Composable
private fun ReadingLineCard(text: String) {
    LineCard(text, Modifier.fillMaxWidth(), surface = Chalkroom.colors.ink2, outline = Chalkroom.colors.card)
}

/** Calm, in the student's voice, with the way forward. */
@Composable
private fun Message(text: String, primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>? = null) {
    ReadingLineCard(text)
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
        ReadingContent(UiState(StudentId.PIP, notes = "Photosynthesis turns light into sugar. Chlorophyll absorbs light in the chloroplasts.", phase = Phase.COUNTED, motes = listOf(Mote("a"), Mote("b"), Mote("c"))), onEvent = {})
    }
}
