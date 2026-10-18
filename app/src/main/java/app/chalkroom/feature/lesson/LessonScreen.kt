package app.chalkroom.feature.lesson

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.data.ItemPhase
import app.chalkroom.data.LessonLogic
import app.chalkroom.feature.lesson.LessonContract.Phase
import app.chalkroom.feature.lesson.LessonContract.UiEffect
import app.chalkroom.feature.lesson.LessonContract.UiEvent
import app.chalkroom.feature.lesson.LessonContract.UiState
import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.MisconceptionDto
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentCatalog
import app.chalkroom.students.StudentFigure
import app.chalkroom.students.unconvinced
import app.chalkroom.ui.components.LineCard
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LessonScreen(
    studentId: StudentId,
    onClosing: () -> Unit,
    viewModel: LessonViewModel = koinViewModel { parametersOf(studentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                UiEffect.SoftHaptic -> view.softHaptic()
                UiEffect.OpenClosing -> onClosing()
            }
        }
    }
    LessonContent(state, viewModel::onEvent)
}

/** CONFIRM on API 30+, CLOCK_TICK below (spec.md > App: Clearing Choreography). */
private fun View.softHaptic() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
}

@Composable
fun LessonContent(state: UiState, onEvent: (UiEvent) -> Unit) {
    val lesson = state.lesson ?: return
    val colors = Chalkroom.colors
    val name = StudentCatalog[lesson.studentId].name
    val clearing = state.phase == Phase.CLEARING

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        Figure(state)

        Text("what's left in $name's head: ${lesson.queue.size}", style = Chalkroom.type.label, modifier = Modifier.padding(top = 10.dp))
        CounterBar(if (lesson.totalCount == 0) 0f else lesson.queue.size.toFloat() / lesson.totalCount)

        Spacer(Modifier.height(20.dp))
        val line = state.spokenLine ?: lesson.current?.currentLine
        AnimatedContent(line, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "line") { text ->
            if (text != null) LineCard(text, Modifier.fillMaxWidth())
        }

        state.lastExplanation?.let {
            Text(
                "You: $it",
                style = Chalkroom.type.body.copy(color = colors.inkSoft),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 6.dp, end = 6.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        when {
            clearing -> Spacer(Modifier.height(56.dp))
            lesson.phase == ItemPhase.REVEALED -> AnswerCard(lesson.current?.refutation.orEmpty()) { onEvent(UiEvent.ContinueAfterAnswer) }
            lesson.phase == ItemPhase.STUCK && state.phase == Phase.IDLE -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton("See the answer", { onEvent(UiEvent.SeeAnswer) })
                QuietButton("One last try", { onEvent(UiEvent.OneLastTry) })
            }
            else -> ExplainInput(
                state = state,
                name = name,
                placeholder = if (LessonLogic.startOverHint(lesson)) "start from the beginning" else "Explain it to $name…",
                onEvent = onEvent,
                locked = state.inputLocked || lesson.phase == ItemPhase.STUCK,
            )
        }
    }
}

@Composable
private fun Figure(state: UiState) {
    val lesson = state.lesson ?: return
    val headProgress by animateFloatAsState(
        targetValue = when (state.clearingStep) {
            null, ClearingStep.HOLD -> 0f
            ClearingStep.FOG_RESOLVES -> 0.5f
            else -> 1f
        },
        animationSpec = tween(if (state.clearingStep == ClearingStep.FOG_RESOLVES) 140 else 80, easing = FastOutSlowInEasing),
        label = "clear",
    )
    val pose = remember { Animatable(0f) }
    LaunchedEffect(state.unconvincedTick) {
        if (state.unconvincedTick == 0) return@LaunchedEffect
        pose.animateTo(1f, tween(180))
        pose.animateTo(0f, tween(320))
    }
    Box(Modifier.width(240.dp), contentAlignment = Alignment.Center) {
        StudentFigure(
            studentId = lesson.studentId,
            expression = state.expression,
            modifier = Modifier.width(170.dp).unconvinced(lesson.studentId, pose.value),
            motes = lesson.queue.map { Mote(it.id, if (it.id == state.clearingMoteId) headProgress else 0f) },
        )
        AnimatedVisibility(
            state.writing,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-6).dp),
        ) { NotePad() }
    }
}

/** A small pad with squiggles being written: abstract marks, never words. */
@Composable
private fun NotePad() {
    val colors = Chalkroom.colors
    val ink = remember { Animatable(0f) }
    LaunchedEffect(Unit) { ink.animateTo(1f, tween(1_400)) }
    Canvas(Modifier.size(54.dp, 64.dp).semantics { contentDescription = "writing it down" }) {
        val w = size.width
        val h = size.height
        drawRoundRect(colors.chalk, cornerRadius = CornerRadius(6f))
        drawRoundRect(colors.ink, cornerRadius = CornerRadius(6f), style = Stroke(2.5f * density))
        val rows = 3
        for (r in 0 until rows) {
            val p = ((ink.value * rows) - r).coerceIn(0f, 1f)
            if (p <= 0f) continue
            val y = h * (0.3f + r * 0.22f)
            val path = Path().apply {
                moveTo(w * 0.16f, y)
                val end = w * (0.16f + 0.68f * p)
                var x = w * 0.16f
                var up = true
                while (x < end) {
                    val nx = (x + w * 0.09f).coerceAtMost(end)
                    quadraticTo((x + nx) / 2, y + (if (up) -1 else 1) * h * 0.05f, nx, y)
                    x = nx
                    up = !up
                }
            }
            drawPath(path, colors.ink, style = Stroke(2f * density, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun CounterBar(fraction: Float) {
    val colors = Chalkroom.colors
    val f by animateFloatAsState(fraction, tween(420, easing = FastOutSlowInEasing), label = "bar")
    Canvas(Modifier.padding(top = 8.dp).width(180.dp).height(8.dp)) {
        drawRoundRect(colors.paperDeep, cornerRadius = CornerRadius(size.height / 2))
        drawRoundRect(colors.ink, size = Size(size.width * f, size.height), cornerRadius = CornerRadius(size.height / 2), topLeft = Offset.Zero)
    }
}

@Composable
private fun ExplainInput(state: UiState, name: String, placeholder: String, locked: Boolean, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (state.phase == Phase.FAILED) {
            Text(
                "That didn't reach $name. Your words are back in the box.",
                style = Chalkroom.type.body.copy(color = colors.inkSoft),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 4.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(shape)
                .background(colors.chalk)
                .border(2.dp, colors.ink.copy(alpha = if (locked) 0.25f else 1f), shape)
                .padding(14.dp)
                .alpha(if (locked) 0.5f else 1f),
        ) {
            BasicTextField(
                value = state.input,
                onValueChange = { onEvent(UiEvent.InputChanged(it)) },
                enabled = !locked,
                textStyle = Chalkroom.type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Your explanation" },
            )
            if (state.input.isEmpty()) Text(placeholder, style = Chalkroom.type.body.copy(color = colors.inkSoft))
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = if (state.phase == Phase.FAILED) "Try again" else "Explain",
            onClick = { onEvent(UiEvent.Send) },
            enabled = !locked && state.input.isNotBlank(),
        )
    }
}

/** The app's own card, never the student's voice: what's actually true. */
@Composable
private fun AnswerCard(refutation: String, onContinue: () -> Unit) {
    val colors = Chalkroom.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.paperDeep)
            .padding(20.dp),
    ) {
        Text("What's actually true", style = Chalkroom.type.label.copy(color = colors.inkSoft))
        Spacer(Modifier.height(8.dp))
        Text(refutation, style = Chalkroom.type.body.copy(color = colors.ink))
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Continue", onContinue, Modifier.align(Alignment.End))
    }
}

private val previewLesson = LessonLogic.startLesson(
    StudentId.MIRA,
    CreateDeckResponse(
        "en",
        "Photosynthesis",
        listOf(
            MisconceptionDto("m1", "I think glucose is made from water and carbon dioxide alone.", "Light energy is required."),
            MisconceptionDto("m2", "I think the oxygen comes from the carbon dioxide.", "It comes from water."),
        ),
    ),
)

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun LessonPreview() = ChalkroomTheme {
    LessonContent(UiState(lesson = previewLesson, lastExplanation = "Plants need sunlight."), {})
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun LessonRevealedPreview() = ChalkroomTheme {
    LessonContent(UiState(lesson = LessonLogic.revealAnswer(previewLesson)), {})
}
