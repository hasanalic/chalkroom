package app.chalkroom.feature.lesson

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.data.ItemPhase
import app.chalkroom.data.Lesson
import app.chalkroom.data.LessonLogic
import app.chalkroom.feature.lesson.LessonContract.Phase
import app.chalkroom.feature.lesson.LessonContract.UiEffect
import app.chalkroom.feature.lesson.LessonContract.UiEvent
import app.chalkroom.feature.lesson.LessonContract.UiState
import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.Exchange
import app.chalkroom.shared.MisconceptionDto
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentCatalog
import app.chalkroom.students.StudentFigure
import app.chalkroom.students.StudentProfile
import app.chalkroom.ui.components.CardShape
import app.chalkroom.ui.components.Ledge
import app.chalkroom.ui.components.LightCard
import app.chalkroom.ui.components.LineCard
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import app.chalkroom.ui.theme.Radii
import kotlinx.coroutines.delay
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
    val profile = StudentCatalog[lesson.studentId]
    val clearing = state.phase == Phase.CLEARING
    val stuckChoice = lesson.phase == ItemPhase.STUCK && state.phase == Phase.IDLE
    val revealed = lesson.phase == ItemPhase.REVEALED && !clearing
    var historyOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.page)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 8.dp),
        ) {
            StudentCard(state, lesson, profile, onHistory = { historyOpen = true })

            Spacer(Modifier.height(14.dp))
            val line = state.spokenLine ?: lesson.current?.currentLine
            AnimatedContent(
                line,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) using SizeTransform(clip = false) },
                label = "line",
            ) { text ->
                if (text != null) LineCard(text, Modifier.fillMaxWidth()) { TypewriterText(text, Chalkroom.type.studentLine) }
            }

            if (stuckChoice) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("See the answer", { onEvent(UiEvent.SeeAnswer) }, Modifier.weight(1f))
                    QuietButton("One last try", { onEvent(UiEvent.OneLastTry) }, Modifier.weight(1f))
                }
            }
            if (revealed) {
                Spacer(Modifier.height(16.dp))
                AnswerCard(lesson.current?.refutation.orEmpty()) { onEvent(UiEvent.ContinueAfterAnswer) }
            }

            state.lastExplanation?.let {
                Spacer(Modifier.height(16.dp))
                YouCard(it)
            }
        }

        if (!stuckChoice && !revealed) {
            ExplainInput(
                state = state,
                name = profile.name,
                placeholder = if (LessonLogic.startOverHint(lesson)) "start from the beginning" else "Explain it to ${profile.name}…",
                locked = state.inputLocked || lesson.phase == ItemPhase.STUCK,
                onEvent = onEvent,
            )
        }
    }

    if (historyOpen && lesson.exchanges.isNotEmpty()) {
        HistorySheet(profile.name, lesson.exchanges) { historyOpen = false }
    }
}

/** Header strip (name, exchanges pill, counter, drain bar) over the student's stage. */
@Composable
private fun StudentCard(state: UiState, lesson: Lesson, profile: StudentProfile, onHistory: () -> Unit) {
    val colors = Chalkroom.colors
    val type = Chalkroom.type
    val count = lesson.queue.size
    Ledge(shape = CardShape, color = colors.ink, ledgeColor = profile.color, modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = type.title.copy(color = colors.card), modifier = Modifier.weight(1f))
                    val n = lesson.exchanges.size
                    AnimatedVisibility(n > 0, enter = fadeIn(), exit = fadeOut()) {
                        ExchangesPill(if (n == 1) "1 exchange" else "$n exchanges", onHistory)
                    }
                }
                // The counter never shows 0: at the end it is removed.
                if (count > 0) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("what's left in ${profile.name}'s head: ", style = type.label.copy(color = colors.card.copy(alpha = 0.85f)))
                        CounterDigit(count)
                    }
                    DrainBar(if (lesson.totalCount == 0) 0f else count.toFloat() / lesson.totalCount)
                }
            }
            Stage(state, lesson, profile)
        }
    }
}

@Composable
private fun ExchangesPill(text: String, onClick: () -> Unit) {
    val colors = Chalkroom.colors
    Ledge(
        shape = RoundedCornerShape(50),
        color = colors.gold,
        ledgeColor = colors.goldLedge,
        depth = 2.dp,
        contentModifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, style = Chalkroom.type.small.copy(color = colors.ink, fontWeight = Chalkroom.type.label.fontWeight))
    }
}

/** The digit doesn't tick: the old one falls out of frame with weight, the new one drops in from above. */
@Composable
private fun CounterDigit(count: Int) {
    val colors = Chalkroom.colors
    AnimatedContent(
        count,
        transitionSpec = {
            (slideInVertically(spring(dampingRatio = 0.6f, stiffness = 500f)) { -it } + fadeIn(tween(120))) togetherWith
                (slideOutVertically(tween(260, easing = EaseIn)) { it } + fadeOut(tween(260, easing = EaseIn)))
        },
        modifier = Modifier.clipToBounds(),
        label = "digit",
    ) { n ->
        Text("$n", style = Chalkroom.type.label.copy(color = colors.card))
    }
}

/** White on an ink2 track; drains a touch slower than the digit. */
@Composable
private fun DrainBar(fraction: Float) {
    val colors = Chalkroom.colors
    val f by animateFloatAsState(fraction, tween(700, easing = EaseInOutSine), label = "bar")
    Canvas(Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp)) {
        drawRoundRect(colors.ink2, cornerRadius = CornerRadius(size.height / 2))
        drawRoundRect(colors.card, size = Size(size.width * f, size.height), cornerRadius = CornerRadius(size.height / 2))
    }
}

/** The student large with their motes, on their colour washed over cream. */
@Composable
private fun Stage(state: UiState, lesson: Lesson, profile: StudentProfile) {
    val colors = Chalkroom.colors
    val clearProgress by animateFloatAsState(
        targetValue = when (state.clearingStep) {
            null, ClearingStep.HOLD -> 0f
            ClearingStep.FOG_RESOLVES -> 0.5f
            else -> 1f
        },
        animationSpec = tween(if (state.clearingStep == ClearingStep.FOG_RESOLVES) 140 else 80, easing = EaseInOutSine),
        label = "clear",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(272.dp)
            .background(profile.color.copy(alpha = 0.18f).compositeOver(colors.page)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        StudentFigure(
            studentId = lesson.studentId,
            expression = state.expression,
            modifier = Modifier.width(225.dp),
            motes = lesson.queue.map { Mote(it.id, if (it.id == state.clearingMoteId) clearProgress else 0f) },
            activeMoteId = state.clearingMoteId ?: lesson.current?.id,
            motesStill = state.clearingStep != null,
            reactTick = state.unconvincedTick,
        )
        AnimatedVisibility(
            state.writing,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 14.dp),
        ) { NotePad() }
    }
}

/** New lines type in quickly; the full text is laid out from the start so the card doesn't grow. */
@Composable
private fun TypewriterText(text: String, style: TextStyle) {
    val reduced = Chalkroom.reducedMotion
    var shown by remember(text) { mutableIntStateOf(if (reduced) text.length else 0) }
    LaunchedEffect(text) {
        while (shown < text.length) {
            delay(TYPE_MS_PER_CHAR)
            shown++
        }
    }
    Text(
        buildAnnotatedString {
            append(text.substring(0, shown))
            withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring(shown)) }
        },
        style = style,
        modifier = Modifier.semantics { contentDescription = text },
    )
}

private const val TYPE_MS_PER_CHAR = 20L

/** Your last explanation, quieter than the student's line. */
@Composable
private fun YouCard(text: String) {
    LightCard(Modifier.fillMaxWidth()) {
        Column {
            Text("You", style = Chalkroom.type.small)
            Spacer(Modifier.height(4.dp))
            Text(text, style = Chalkroom.type.quote, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A small pad with squiggles being written: abstract marks, never words. */
@Composable
private fun NotePad() {
    val colors = Chalkroom.colors
    val ink = remember { Animatable(0f) }
    LaunchedEffect(Unit) { ink.animateTo(1f, tween(1_100, easing = EaseInOutSine)) }
    Canvas(Modifier.size(48.dp, 58.dp).semantics { contentDescription = "writing it down" }) {
        val w = size.width
        val h = size.height
        rotate(-6f) {
            drawRoundRect(colors.card, cornerRadius = CornerRadius(6f * density))
            drawRoundRect(colors.ink, cornerRadius = CornerRadius(6f * density), style = Stroke(2.5f * density))
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
}

@Composable
private fun ExplainInput(state: UiState, name: String, placeholder: String, locked: Boolean, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    val shape = RoundedCornerShape(Radii.button)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.page)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        if (state.phase == Phase.FAILED) {
            Text(
                "That didn't reach $name. Your words are back in the box.",
                style = Chalkroom.type.small,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .padding(bottom = 4.dp) // lines up with the button's ledge
                    .heightIn(min = 56.dp, max = 150.dp)
                    .clip(shape)
                    .background(colors.card)
                    .border(2.dp, if (locked) colors.line else colors.ink, shape)
                    .padding(horizontal = 16.dp, vertical = 15.dp)
                    .alpha(if (locked) 0.55f else 1f),
            ) {
                BasicTextField(
                    value = state.input,
                    onValueChange = { onEvent(UiEvent.InputChanged(it)) },
                    enabled = !locked,
                    textStyle = Chalkroom.type.body.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Your explanation" },
                )
                if (state.input.isEmpty()) Text(placeholder, style = Chalkroom.type.body.copy(color = colors.muted))
            }
            PrimaryButton(
                text = if (state.phase == Phase.FAILED) "Try again" else "Explain",
                onClick = { onEvent(UiEvent.Send) },
                enabled = !locked && state.input.isNotBlank(),
            )
        }
    }
}

/** The app's own card, never the student's voice: what's actually true. */
@Composable
private fun AnswerCard(refutation: String, onContinue: () -> Unit) {
    val colors = Chalkroom.colors
    LightCard(Modifier.fillMaxWidth(), ledgeColor = colors.line, border = colors.line) {
        Column(Modifier.fillMaxWidth()) {
            Text("What's actually true", style = Chalkroom.type.small)
            Spacer(Modifier.height(6.dp))
            Text(refutation, style = Chalkroom.type.body)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Continue", onContinue, Modifier.align(Alignment.End))
        }
    }
}

/** What you and the student said about this belief so far. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(name: String, exchanges: List<Exchange>, onDismiss: () -> Unit) {
    val colors = Chalkroom.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.page) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("This one so far", style = Chalkroom.type.headline)
            exchanges.forEach { ex ->
                Column {
                    Text("You", style = Chalkroom.type.small)
                    Text(ex.explanation, style = Chalkroom.type.quote)
                }
                ex.reply?.let {
                    Column {
                        Text(name, style = Chalkroom.type.small)
                        Text(it, style = Chalkroom.type.body)
                    }
                }
            }
        }
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
