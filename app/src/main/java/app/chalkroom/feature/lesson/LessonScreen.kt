package app.chalkroom.feature.lesson

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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
import app.chalkroom.ui.components.ChalkLines
import app.chalkroom.ui.components.Ledge
import app.chalkroom.ui.components.LightCard
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.components.drawErasedArcs
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import app.chalkroom.ui.theme.Radii
import kotlinx.coroutines.delay
import kotlin.math.sin
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
    LightStatusBarIcons()
    LessonContent(state, viewModel::onEvent)
}

/** The board runs under the status bar, so its icons go light while this screen shows; put back after. */
@Composable
private fun LightStatusBarIcons() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
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

    // The board runs up under the status bar; only the sides and bottom (nav bar, keyboard) pad the screen.
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.page)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            Board(state, lesson, profile, onHistory = { historyOpen = true })

            Column(Modifier.padding(horizontal = 16.dp)) {
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

/**
 * The slate across the whole top of the screen, up under the status bar: name, exchanges pill and
 * counter chalked at its top-left, the student's stage, and their line in a chalk bubble below.
 */
@Composable
private fun Board(state: UiState, lesson: Lesson, profile: StudentProfile, onHistory: () -> Unit) {
    val colors = Chalkroom.colors
    val type = Chalkroom.type
    val count = lesson.queue.size
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.board)
            .drawBehind { drawErasedArcs(Offset.Zero, size, colors.card.copy(alpha = 0.035f)) }
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 12.dp)) {
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
            }
        }
        Stage(state, lesson, profile)

        val line = state.spokenLine ?: lesson.current?.currentLine
        AnimatedContent(
            line,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) using SizeTransform(clip = false) },
            label = "line",
        ) { text ->
            if (text != null) ChalkBubble(text, Modifier.overlapUp(BUBBLE_OVERLAP)) else Spacer(Modifier.fillMaxWidth().height(12.dp))
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

/**
 * The student on the slate board. What you taught goes up on the board's left, one chalk line
 * per belief your words cleared, written on once the clearing and the pad are done.
 */
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
            .clipToBounds(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        ChalkLines(
            lines = lesson.chalkLines.take(state.chalkShown),
            accent = profile.color,
            fitLines = lesson.totalCount,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxSize()
                .padding(start = 18.dp, top = 16.dp, bottom = 12.dp + BUBBLE_OVERLAP, end = BOARD_STUDENT_W - 12.dp),
        )
        StudentFigure(
            studentId = lesson.studentId,
            expression = state.expression,
            modifier = Modifier.padding(end = BOARD_STUDENT_END).width(BOARD_STUDENT_W),
            motes = lesson.queue.map { Mote(it.id, if (it.id == state.clearingMoteId) clearProgress else 0f) },
            activeMoteId = state.clearingMoteId ?: lesson.current?.id,
            motesStill = state.clearingStep != null,
            reactTick = state.unconvincedTick,
        )
        AnimatedVisibility(
            state.writing,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = BOARD_STUDENT_W - 50.dp).offset(y = 14.dp - BUBBLE_OVERLAP),
        ) { NotePad() }
    }
}

/** The student stands to the right so the board's left keeps room for three chalk lines. */
private val BOARD_STUDENT_W = 168.dp
private val BOARD_STUDENT_END = 6.dp

/**
 * The student's line chalked on the slate: a hand-drawn chalk outline with a notch pointing up at the
 * student (under their centre, from the same layout values), the text typed in. Wraps and grows the
 * board; never clipped. It overlaps the stage's bottom edge so the student stands behind it.
 */
@Composable
private fun ChalkBubble(text: String, modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    val chalk = colors.card
    Box(
        modifier
            .fillMaxWidth()
            .drawWithCache {
                val outline = chalkBubbleOutline(
                    size = size,
                    margin = BUBBLE_MARGIN.toPx(),
                    notchH = BUBBLE_NOTCH_H.toPx(),
                    notchW = BUBBLE_NOTCH_W.toPx(),
                    notchCx = size.width - (BOARD_STUDENT_END + BOARD_STUDENT_W / 2).toPx(),
                    radius = BUBBLE_RADIUS.toPx(),
                    bottom = BUBBLE_BOTTOM.toPx(),
                )
                val main = chalkJitter(outline, density, phase = 0f, amp = 0.9f)
                val ghost = chalkJitter(outline, density, phase = 2.3f, amp = 1.4f)
                onDrawBehind {
                    // Opaque slate first: the student stands behind the bubble, their cut-off feet hidden.
                    drawPath(main, colors.board)
                    drawPath(main, chalk.copy(alpha = 0.05f))
                    drawPath(ghost, chalk.copy(alpha = 0.35f), style = Stroke(1.3f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(main, chalk.copy(alpha = 0.88f), style = Stroke(2.4f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            .padding(start = BUBBLE_MARGIN + 20.dp, end = BUBBLE_MARGIN + 20.dp, top = BUBBLE_NOTCH_H + 14.dp, bottom = BUBBLE_BOTTOM + 16.dp),
    ) {
        TypewriterText(text, Chalkroom.type.studentLine.copy(color = chalk))
    }
}

private val BUBBLE_MARGIN = 16.dp
private val BUBBLE_NOTCH_H = 14.dp
private val BUBBLE_NOTCH_W = 26.dp
private val BUBBLE_RADIUS = 22.dp
private val BUBBLE_BOTTOM = 18.dp
private val BUBBLE_OVERLAP = 26.dp

/** Lays this out [by] higher, taking that much less room, so it sits over what's above it. */
private fun Modifier.overlapUp(by: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val up = by.roundToPx()
    layout(p.width, (p.height - up).coerceAtLeast(0)) { p.place(0, -up) }
}

/** A rounded box inset by [margin], with a notch on its top edge peaking at [notchCx]. */
private fun chalkBubbleOutline(size: Size, margin: Float, notchH: Float, notchW: Float, notchCx: Float, radius: Float, bottom: Float): Path {
    val l = margin
    val r = size.width - margin
    val t = notchH
    val b = size.height - bottom
    val cx = notchCx.coerceIn(l + radius + notchW / 2, r - radius - notchW / 2)
    return Path().apply {
        moveTo(l + radius, t)
        lineTo(cx - notchW / 2, t)
        lineTo(cx, 0f)
        lineTo(cx + notchW / 2, t)
        lineTo(r - radius, t)
        quadraticTo(r, t, r, t + radius)
        lineTo(r, b - radius)
        quadraticTo(r, b, r - radius, b)
        lineTo(l + radius, b)
        quadraticTo(l, b, l, b - radius)
        lineTo(l, t + radius)
        quadraticTo(l, t, l + radius, t)
        close()
    }
}

/** Re-traces [path] with a slow, fixed wobble across it, so the outline reads as drawn by hand in chalk. */
private fun chalkJitter(path: Path, density: Float, phase: Float, amp: Float): Path {
    val measure = PathMeasure().apply { setPath(path, true) }
    val length = measure.length
    val step = 5f * density
    return Path().apply {
        var d = 0f
        while (d <= length) {
            val p = measure.getPosition(d)
            val tan = measure.getTangent(d)
            val k = d / density
            val off = (sin(k * 0.045f + phase) * 0.8f + sin(k * 0.13f + phase * 1.7f) * 0.45f) * amp * density
            val x = p.x - tan.y * off
            val y = p.y + tan.x * off
            if (d == 0f) moveTo(x, y) else lineTo(x, y)
            d += step
        }
        close()
    }
}

/** New lines type in quickly; the full text is laid out from the start so the bubble doesn't grow. */
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
    LessonContent(UiState(lesson = LessonLogic.revealAnswer(previewLesson, StudentCatalog[previewLesson.studentId].answerLine)), {})
}
