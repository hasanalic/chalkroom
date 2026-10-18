package app.chalkroom.feature.classroom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.random.Random

@Composable
fun ClassroomScreen(
    onTeachSomething: () -> Unit,
    onResumeLesson: (StudentId) -> Unit,
    viewModel: ClassroomViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                UiEffect.OpenPasteNotes -> onTeachSomething()
                is UiEffect.OpenLesson -> onResumeLesson(it.studentId)
            }
        }
    }
    ClassroomContent(state, viewModel::onEvent)
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

/** The entrance stagger plays once per process (cold start), not on every return to the room. */
private object Entrance {
    var played = false
}

@Composable
fun ClassroomContent(state: UiState, onEvent: (UiEvent) -> Unit) {
    val reduced = Chalkroom.reducedMotion
    val life = rememberRoomLife(enabled = !reduced)

    // Entering: board, then desks, then motes.
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
            onTap = { onEvent(UiEvent.BoardTapped) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = top + 20.dp)
                .width(w * 0.64f)
                .height(h * 0.2f)
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
                seat = seat.copy(motes = if (motesShown) seat.motes else emptyList()),
                life = life.forStudent(id),
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

        PrimaryButton(
            "Teach something",
            onClick = { onEvent(UiEvent.TeachSomething) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom + 20.dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
        )
    }
}

/** The board on the wall; tappable (gives a little under a finger) when a lesson is paused. */
@Composable
private fun Board(board: ClassroomContract.Board?, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val give by animateFloatAsState(if (pressed && board != null) 0.97f else 1f, settle(), label = "give")
    val trayH = 14.dp
    Box(
        modifier
            .graphicsLayer {
                scaleX = give
                scaleY = give
            }
            .clickable(interaction, indication = null, enabled = board != null, role = Role.Button, onClick = onTap),
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
            // Faint erased arcs.
            val erased = colors.card.copy(alpha = 0.06f)
            clipRect(slateTl.x, slateTl.y, slateTl.x + slate.width, slateTl.y + slate.height) {
            drawArc(erased, 200f, 110f, false, Offset(slateTl.x + slate.width * 0.08f, slateTl.y + slate.height * 0.2f), Size(slate.width * 0.5f, slate.height * 0.9f), style = Stroke(14f * dp, cap = StrokeCap.Round))
            drawArc(erased, 160f, 90f, false, Offset(slateTl.x + slate.width * 0.45f, slateTl.y + slate.height * 0.35f), Size(slate.width * 0.45f, slate.height * 0.7f), style = Stroke(10f * dp, cap = StrokeCap.Round))
            drawArc(erased, 230f, 70f, false, Offset(slateTl.x + slate.width * 0.3f, slateTl.y - slate.height * 0.1f), Size(slate.width * 0.4f, slate.height * 0.6f), style = Stroke(8f * dp, cap = StrokeCap.Round))
            }
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
        }
    }
}

@Composable
private fun Seat(seat: ClassroomContract.Seat, life: RoomLife?, doodleStrokes: Int, onTap: () -> Unit, modifier: Modifier = Modifier) {
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
        lookAt = kind?.lookAt,
        hopTick = if (kind == LifeKind.PIP_BOUNCE) life?.tick ?: 0 else 0,
        propMotion = propMotion,
        doodleStrokes = doodleStrokes,
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
