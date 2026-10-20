package app.chalkroom.feature.classroom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.chalkroom.students.Dart
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.feature.classroom.ClassroomContract.UiEffect
import app.chalkroom.feature.classroom.ClassroomContract.UiEvent
import app.chalkroom.feature.classroom.ClassroomContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Expression
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentAtDesk
import app.chalkroom.students.StudentCatalog
import app.chalkroom.students.settle
import app.chalkroom.ui.components.ChalkLines
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.drawErasedArcs
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ClassroomScreen(
    onTeachSomething: () -> Unit,
    onResumeLesson: (StudentId) -> Unit,
    viewModel: ClassroomViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var flicks by remember { mutableIntStateOf(0) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                UiEffect.OpenPasteNotes -> onTeachSomething()
                is UiEffect.OpenLesson -> onResumeLesson(it.studentId)
                UiEffect.Flicker -> flicks++
            }
        }
    }
    ClassroomContent(state, viewModel::onEvent, flicks)
}

/** Where each seat sits: x and width as shares of the screen width, bottom as a share of its height. */
private data class SeatPlace(val x: Float, val width: Float, val bottom: Float)

private val places = mapOf(
    StudentId.MIRA to SeatPlace(0.05f, 0.40f, 0.635f), // back row, left
    StudentId.JUNO to SeatPlace(0.55f, 0.40f, 0.635f), // back row, right
    StudentId.PIP to SeatPlace(0.265f, 0.47f, 0.84f), // front
)

/** Seat height / seat width (figure + desk, see StudentAtDesk). */
private const val SEAT_ASPECT = 1.34f

/** Where the light switch sits on the wall (its centre): under the shelf, beside the board, at hand height. */
private const val SWITCH_X = 0.08f
private const val SWITCH_Y = 0.325f

/** How deep the navy wash is with the lights off: dark, but the room still faintly there. */
private const val NIGHT_ALPHA = 0.8f

/** Waking order after the lights come on, one by one. */
private val wakeOrder = listOf(StudentId.JUNO, StudentId.MIRA, StudentId.PIP)

/** The entrance stagger plays once per process (cold start), not on every return to the room. */
private object Entrance {
    var played = false
}

/**
 * [flicks] counts taps on the switch with the lights already on; each plays one quick flicker.
 */
@Composable
fun ClassroomContent(state: UiState, onEvent: (UiEvent) -> Unit, flicks: Int = 0) {
    val reduced = Chalkroom.reducedMotion
    val colors = Chalkroom.colors
    val haptics = LocalHapticFeedback.current

    // Lights off (first open ever): the room sits in the dark, students asleep, until the switch.
    val darkAtOpen = remember { !state.lightsOn && !reduced }
    val dark = remember { Animatable(if (darkAtOpen) 1f else 0f) }
    val warm = remember { Animatable(0f) }
    val flick = remember { Animatable(1f) }
    val toggle = remember { Animatable(if (darkAtOpen) 0f else 1f) }
    var awake by remember { mutableStateOf(if (darkAtOpen) emptySet() else StudentId.entries.toSet()) }
    var buttonShown by remember { mutableStateOf(!darkAtOpen) }
    val buttonIn = remember { Animatable(if (darkAtOpen) 0f else 1f) }
    var settled by remember { mutableStateOf(!darkAtOpen) }
    var glancing by remember { mutableStateOf(false) }
    LaunchedEffect(state.lightsOn) {
        if (!state.lightsOn || dark.value == 0f) return@LaunchedEffect
        // ~600 ms warm-up with two soft dips, then the students wake one by one, then the button.
        launch { toggle.animateTo(1f, tween(110, easing = Dart)) }
        launch {
            dark.animateTo(
                0f,
                keyframes {
                    durationMillis = 600
                    1f at 0 using EaseOutCubic
                    0.3f at 130 using EaseInOutSine
                    0.6f at 220 using EaseInOutSine
                    0.15f at 340 using EaseInOutSine
                    0.38f at 420 using EaseOutCubic
                },
            )
        }
        launch {
            warm.animateTo(1f, tween(450))
            warm.animateTo(0f, tween(1_100, easing = EaseInOutSine))
        }
        delay(650)
        wakeOrder.forEach {
            awake = awake + it
            delay(WAKE_GAP_MS)
        }
        delay(BUTTON_AT_MS - 650 - wakeOrder.size * WAKE_GAP_MS)
        buttonShown = true
        buttonIn.animateTo(1f, settle())
        delay(1_000)
        settled = true
    }
    LaunchedEffect(flicks) {
        if (flicks == 0 || reduced) return@LaunchedEffect
        launch {
            toggle.animateTo(0.25f, tween(70))
            toggle.animateTo(1f, tween(120, easing = Dart))
        }
        launch {
            flick.snapTo(0f)
            flick.animateTo(1f, tween(240))
        }
        glancing = true
        delay(900)
        glancing = false
    }

    val life = rememberRoomLife(enabled = !reduced && settled)

    // Entering: board, then desks, then motes. Not after the dark: the room is already there.
    if (darkAtOpen) Entrance.played = true
    val stagger = remember { !Entrance.played && !reduced }
    val boardIn = remember { Animatable(if (stagger) 0f else 1f) }
    val desksIn = remember { StudentId.entries.associateWith { Animatable(if (stagger) 0f else 1f) } }
    var motesShown by remember { mutableStateOf(!stagger) }
    LaunchedEffect(Unit) {
        if (!stagger) return@LaunchedEffect
        Entrance.played = true
        launch { boardIn.animateTo(1f, settle()) }
        delay(160)
        listOf(StudentId.MIRA, StudentId.JUNO, StudentId.PIP).forEach {
            launch { desksIn.getValue(it).animateTo(1f, settle()) }
            delay(110)
        }
        delay(260)
        motesShown = true
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Chalkroom.colors.page)) {
        val w = maxWidth
        val h = maxHeight
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Room(Modifier.fillMaxSize())

        Board(
            board = state.board,
            learned = state.learned,
            reduced = reduced,
            onTap = { onEvent(UiEvent.BoardTapped) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = top + 20.dp)
                .width(w * 0.64f)
                .height(h * 0.275f)
                .graphicsLayer {
                    alpha = boardIn.value
                    translationY = -14.dp.toPx() * (1f - boardIn.value)
                },
        )

        // Back row first, front desk last, so the front sits over the back.
        listOf(StudentId.MIRA, StudentId.JUNO, StudentId.PIP).forEach { id ->
            val seat = state.seats.first { it.studentId == id }
            val place = places.getValue(id)
            val seatW = w * place.width
            val enter = desksIn.getValue(id).value
            Seat(
                seat = seat.copy(motes = if (motesShown && id in awake) seat.motes else emptyList()),
                life = life.forStudent(id),
                asleep = id !in awake,
                glanceAtSwitch = glancing,
                doodleStrokes = if (id == StudentId.MIRA) life.doodles else 0,
                onTap = { onEvent(UiEvent.StudentTapped(id)) },
                modifier = Modifier
                    .offset(x = w * place.x, y = h * place.bottom - seatW * SEAT_ASPECT)
                    .width(seatW)
                    .graphicsLayer {
                        alpha = enter
                        translationY = 18.dp.toPx() * (1f - enter)
                    },
            )
        }

        // The night: a deep navy wash over the room (it takes taps while the lights are off), and a
        // warm flush as they come on. A later flicker is one quick soft dip.
        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (state.lightsOn) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown() } }
                    },
                ),
        ) {
            val dip = if (flick.value < 1f) 0.45f * sin(PI.toFloat() * flick.value) else 0f
            val night = (dark.value + dip).coerceIn(0f, 1f)
            if (night > 0f) drawRect(colors.night.copy(alpha = NIGHT_ALPHA * night))
            if (warm.value > 0f) drawRect(colors.switchGlow.copy(alpha = 0.08f * warm.value))
        }

        val switchSize = 64.dp
        LightSwitch(
            on = toggle.asState(),
            dark = dark.asState(),
            glowing = darkAtOpen && !state.lightsOn,
            onTap = {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onEvent(UiEvent.SwitchTapped)
            },
            modifier = Modifier
                .offset(x = w * SWITCH_X - switchSize / 2, y = h * SWITCH_Y - switchSize / 2)
                .size(switchSize),
        )

        if (buttonShown) {
            PrimaryButton(
                "Teach something",
                onClick = { onEvent(UiEvent.TeachSomething) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottom + 20.dp)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = buttonIn.value.coerceIn(0f, 1f)
                        translationY = 28.dp.toPx() * (1f - buttonIn.value)
                    },
            )
        }
    }
}

private const val WAKE_GAP_MS = 260L
private const val SWITCH_SCALE = 1.4f
private const val BUTTON_AT_MS = 2_500L

/**
 * A small light switch on the wall: a cream plate with two screws and a toggle, up for on. [on]
 * runs 0 (down) → 1 (up). With the lights off ([dark] 1) the plate is dimmed and, while [glowing],
 * a faint warm glow slowly breathes around it.
 */
@Composable
private fun LightSwitch(on: State<Float>, dark: State<Float>, glowing: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    val pulse = if (glowing) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_400, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "glow",
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    Canvas(
        modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onTap)
            .semantics { contentDescription = "Light switch" },
    ) {
        val dp = density * SWITCH_SCALE
        val d = dark.value
        val p = pulse.value
        val c = center
        if (d > 0f) {
            val r = (32f + 7f * p) * dp
            drawCircle(
                Brush.radialGradient(listOf(colors.switchGlow.copy(alpha = (0.44f + 0.26f * p) * d), Color.Transparent), c, r),
                r,
                c,
            )
        }
        val plate = Size(20f * dp, 30f * dp)
        val tl = Offset(c.x - plate.width / 2, c.y - plate.height / 2)
        val r = CornerRadius(3f * dp)
        drawRoundRect(colors.ink.copy(alpha = 0.14f), tl + Offset(2f * dp, 3f * dp), plate, r)
        drawRoundRect(colors.card, tl, plate, r)
        drawRoundRect(colors.ink, tl, plate, r, style = Stroke(2f * dp))
        listOf(-1, 1).forEach { side -> drawCircle(colors.ink.copy(alpha = 0.45f), 1.5f * dp, Offset(c.x, c.y + side * 11.5f * dp)) }
        // The recess and the toggle in it.
        val slot = Size(8f * dp, 14f * dp)
        val slotTl = Offset(c.x - slot.width / 2, c.y - slot.height / 2)
        drawRoundRect(colors.line, slotTl, slot, CornerRadius(2f * dp))
        drawRoundRect(colors.ink, slotTl, slot, CornerRadius(2f * dp), style = Stroke(1.2f * dp))
        val lever = Size(8f * dp, 7f * dp)
        val leverTl = Offset(slotTl.x, slotTl.y + (slot.height - lever.height) * (1f - on.value))
        drawRoundRect(colors.card, leverTl, lever, CornerRadius(2f * dp))
        drawLine(colors.ink.copy(alpha = 0.3f), Offset(leverTl.x + 1.5f * dp, leverTl.y + lever.height - 1.8f * dp), Offset(leverTl.x + lever.width - 1.5f * dp, leverTl.y + lever.height - 1.8f * dp), 1.2f * dp)
        drawRoundRect(colors.ink, leverTl, lever, CornerRadius(2f * dp), style = Stroke(1.2f * dp))
        // In the dark: dimmed, with a warm rim catching the glow.
        if (d > 0f) {
            drawRoundRect(colors.night.copy(alpha = 0.35f * d), tl, plate, r)
            drawRoundRect(colors.switchGlow.copy(alpha = (0.6f + 0.35f * p) * d), tl, plate, r, style = Stroke(1.5f * dp))
        }
    }
}

/**
 * The board on the wall. A tap puffs a little chalk dust where the finger lands; when a lesson is
 * paused the board also gives a little and opens it. Otherwise it keeps what the last finished
 * lesson taught.
 */
@Composable
private fun Board(board: ClassroomContract.Board?, learned: ClassroomContract.Learned?, reduced: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val give by animateFloatAsState(if (pressed && board != null) 0.97f else 1f, settle(), label = "give")
    val trayH = 14.dp
    var downAt by remember { mutableStateOf(Offset.Zero) }
    var puffAt by remember { mutableStateOf(Offset.Zero) }
    val puff = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .graphicsLayer {
                scaleX = give
                scaleY = give
            }
            .pointerInput(Unit) {
                awaitEachGesture { downAt = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position }
            }
            .clickable(interaction, indication = null, role = Role.Button) {
                if (!reduced) {
                    puffAt = downAt
                    scope.launch {
                        puff.snapTo(0f)
                        puff.animateTo(1f, tween(900, easing = EaseOutCubic))
                    }
                }
                if (board != null) onTap()
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val dp = density
            val tray = trayH.toPx()
            val frame = Size(size.width, size.height - tray)
            val r = CornerRadius(8f * dp)
            // Flat drop shadow, wooden frame, slate.
            drawRoundRect(colors.ink.copy(alpha = 0.14f), Offset(5f * dp, 7f * dp), frame, r)
            drawRoundRect(colors.woodDark, size = frame, cornerRadius = r)
            drawRoundRect(colors.ink, size = frame, cornerRadius = r, style = Stroke(2.5f * dp))
            val inset = 10f * dp
            val slateTl = Offset(inset, inset)
            val slate = Size(frame.width - 2 * inset, frame.height - 2 * inset)
            drawRoundRect(colors.board, slateTl, slate, CornerRadius(4f * dp))
            drawRoundRect(colors.ink.copy(alpha = 0.6f), slateTl, slate, CornerRadius(4f * dp), style = Stroke(1.5f * dp))
            drawErasedArcs(slateTl, slate, colors.card.copy(alpha = 0.06f))
            // The ledge under it, with a stick of chalk and an eraser.
            val ledgeTl = Offset(-6f * dp, frame.height - 4f * dp)
            val ledge = Size(size.width + 12f * dp, tray)
            drawRoundRect(colors.woodDeep, ledgeTl, ledge, CornerRadius(3f * dp))
            drawRoundRect(colors.ink, ledgeTl, ledge, CornerRadius(3f * dp), style = Stroke(2f * dp))
            drawRoundRect(colors.card, Offset(size.width * 0.18f, ledgeTl.y - 5f * dp), Size(22f * dp, 6f * dp), CornerRadius(3f * dp))
            drawRoundRect(colors.ink, Offset(size.width * 0.18f, ledgeTl.y - 5f * dp), Size(22f * dp, 6f * dp), CornerRadius(3f * dp), style = Stroke(1.2f * dp))
            val eraserTl = Offset(size.width * 0.68f, ledgeTl.y - 9f * dp)
            drawRoundRect(colors.ink2, eraserTl, Size(34f * dp, 10f * dp), CornerRadius(2f * dp))
            drawRect(colors.lavender2, Offset(eraserTl.x, eraserTl.y + 6f * dp), Size(34f * dp, 4f * dp))
            drawRoundRect(colors.ink, eraserTl, Size(34f * dp, 10f * dp), CornerRadius(2f * dp), style = Stroke(1.2f * dp))
        }
        if (board != null) {
            val color = StudentCatalog[board.studentId].color
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(bottom = trayH)
                    .graphicsLayer { rotationZ = -1.5f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${board.name} left this here.", style = Chalkroom.type.chalk, textAlign = TextAlign.Center)
                Text(
                    when (board.remaining) {
                        0 -> "Nothing left that doesn't click."
                        1 -> "1 thing still doesn't click."
                        else -> "${board.remaining} things still don't click."
                    },
                    style = Chalkroom.type.chalk.copy(color = colors.card.copy(alpha = 0.8f)),
                    textAlign = TextAlign.Center,
                )
                // A short chalk stroke in the student's colour.
                Canvas(Modifier.padding(top = 6.dp).width(64.dp).height(8.dp)) {
                    val p = Path().apply {
                        moveTo(0f, size.height * 0.6f)
                        cubicTo(size.width * 0.3f, 0f, size.width * 0.6f, size.height, size.width, size.height * 0.3f)
                    }
                    drawPath(p, color, style = Stroke(3.5f * density, cap = StrokeCap.Round))
                }
            }
        } else if (learned != null) {
            val color = StudentCatalog[learned.studentId].color
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 22.dp, top = 16.dp, end = 18.dp, bottom = trayH + 14.dp),
            ) {
                Text(
                    "${learned.name} learned:",
                    style = Chalkroom.type.chalkHand.copy(color = color, fontSize = 17.sp, lineHeight = 19.sp),
                    modifier = Modifier.graphicsLayer { rotationZ = -1.5f },
                )
                ChalkLines(
                    lines = learned.lines,
                    accent = color,
                    fitLines = learned.lines.size,
                    spacing = 2.dp,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp, start = 4.dp),
                )
            }
        }
        // Chalk dust: a few soft specks spreading out and drifting up as they fade.
        if (puff.value < 1f) {
            Canvas(Modifier.matchParentSize()) {
                val t = puff.value
                repeat(9) { i ->
                    val a = (i * 40f + 15f) * PI.toFloat() / 180f
                    val reach = (22f + 10f * (i % 3)) * density * t
                    val at = puffAt + Offset(cos(a) * reach, sin(a) * reach * 0.7f - 18f * density * t)
                    drawCircle(colors.card.copy(alpha = 0.6f * (1f - t)), (3f + 6f * t + 1.5f * (i % 2)) * density, at)
                }
            }
        }
    }
}

/** Where the eyes go when the switch flickers: up and to the left, towards it. */
private val towardSwitch = Offset(-0.05f, -0.04f)

@Composable
private fun Seat(
    seat: ClassroomContract.Seat,
    life: RoomLife?,
    doodleStrokes: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    asleep: Boolean = false,
    glanceAtSwitch: Boolean = false,
) {
    val kind = life?.kind
    val propMotion by animateFloatAsState(
        if (kind == LifeKind.MIRA_DOODLE || kind == LifeKind.PIP_FIDGET || kind == LifeKind.JUNO_PLANT) 1f else 0f,
        tween(if (kind == null) 500 else 900, easing = EaseInOutSine),
        label = "prop",
    )
    StudentAtDesk(
        studentId = seat.studentId,
        expression = Expression.IDLE,
        motes = seat.motes,
        notebookOpen = seat.paused,
        lookAt = if (glanceAtSwitch) towardSwitch else kind?.lookAt,
        hopTick = if (kind == LifeKind.PIP_BOUNCE) life?.tick ?: 0 else 0,
        propMotion = propMotion,
        doodleStrokes = doodleStrokes,
        asleep = asleep,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onTap,
        ),
    )
}

/** One small thing one student does; looks are listed twice so faces change more often than bodies. */
private enum class LifeKind(val student: StudentId, val lookAt: Offset?) {
    MIRA_LOOK_UP(StudentId.MIRA, Offset(0.01f, -0.055f)),
    MIRA_DOODLE(StudentId.MIRA, Offset(-0.045f, 0.045f)),
    PIP_BOUNCE(StudentId.PIP, null),
    PIP_FIDGET(StudentId.PIP, Offset(-0.04f, 0.045f)),
    JUNO_GAZE_UP(StudentId.JUNO, Offset(0.02f, -0.05f)),
    JUNO_PLANT(StudentId.JUNO, Offset(-0.05f, 0.035f)),
}

private val lifeBag = listOf(
    LifeKind.MIRA_LOOK_UP, LifeKind.MIRA_LOOK_UP, LifeKind.MIRA_DOODLE,
    LifeKind.PIP_BOUNCE, LifeKind.PIP_FIDGET, LifeKind.PIP_FIDGET,
    LifeKind.JUNO_GAZE_UP, LifeKind.JUNO_GAZE_UP, LifeKind.JUNO_PLANT,
)

@Immutable
private data class RoomLife(val kind: LifeKind, val tick: Int, val doodles: Int)

/**
 * About every 12–20 s one student (never two at once) does one small thing of their own, never the
 * same thing twice running. Off when animations are removed.
 */
@Composable
private fun rememberRoomLife(enabled: Boolean): RoomLifeState {
    val state = remember { RoomLifeState() }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var last: LifeKind? = null
        while (true) {
            delay(Random.nextLong(12_000, 20_000))
            val kind = lifeBag.filter { it != last }.random()
            last = kind
            state.start(kind)
            delay(LIFE_MS)
            state.stop()
        }
    }
    return state
}

private const val LIFE_MS = 1_800L

private class RoomLifeState {
    private var current by mutableStateOf<LifeKind?>(null)
    private var ticks by mutableIntStateOf(0)
    var doodles by mutableIntStateOf(0)
        private set

    fun start(kind: LifeKind) {
        ticks++
        if (kind == LifeKind.MIRA_DOODLE) doodles = (doodles + 1).coerceAtMost(4)
        current = kind
    }

    fun stop() {
        current = null
    }

    /** What [id] is doing right now, if anything. */
    fun forStudent(id: StudentId): RoomLife? = current?.takeIf { it.student == id }?.let { RoomLife(it, ticks, doodles) }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun ClassroomLearnedPreview() {
    ChalkroomTheme {
        ClassroomContent(
            UiState(
                learned = ClassroomContract.Learned(
                    StudentId.MIRA,
                    "Mira",
                    listOf("chlorophyll catches sunlight that powers making glucose", "splitting water molecules with light energy", "chlorophyll catches light energy to split water"),
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ClassroomPreview() {
    ChalkroomTheme {
        ClassroomContent(
            UiState(
                board = ClassroomContract.Board(StudentId.MIRA, "Mira", 3),
                seats = StudentId.entries.map {
                    ClassroomContract.Seat(it, paused = it == StudentId.MIRA, motes = if (it == StudentId.MIRA) listOf(Mote("a"), Mote("b"), Mote("c")) else emptyList())
                },
            ),
            onEvent = {},
        )
    }
}
