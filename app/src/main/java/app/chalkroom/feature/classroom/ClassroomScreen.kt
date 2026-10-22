package app.chalkroom.feature.classroom

import androidx.compose.animation.core.Animatable
import android.os.SystemClock
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.flow.first
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
import app.chalkroom.students.FIGURE_ASPECT
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import app.chalkroom.ui.theme.ChalkroomColors
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.CompositionLocalProvider
import app.chalkroom.feature.classroom.room.RoomPalette
import app.chalkroom.feature.classroom.room.RoomSheet
import app.chalkroom.feature.classroom.room.WallColor
import app.chalkroom.ui.theme.LocalChalkroomColors
import app.chalkroom.ui.components.LightStatusBarIcons

@Composable
fun ClassroomScreen(
    onTeachSomething: () -> Unit,
    onResumeLesson: (StudentId) -> Unit,
    viewModel: ClassroomViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var glanceTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                UiEffect.OpenPasteNotes -> onTeachSomething()
                is UiEffect.OpenLesson -> onResumeLesson(it.studentId)
                UiEffect.StudentsGlance -> glanceTick++
            }
        }
    }
    ClassroomContent(state, viewModel::onEvent, glanceTick)
}

/**
 * Where each seat sits: x and width as shares of the screen width, bottom as a share of its height.
 * [rise] and [scale] stage the student at it (see StudentAtDesk).
 */
private data class SeatPlace(
    val x: Float,
    val width: Float,
    val bottom: Float,
    val rise: Float = 0f,
    val scale: Float = 1f,
) {
    /** Seat height / seat width. */
    val aspect: Float get() = SEAT_ASPECT + rise

    /** How far (seat widths) a bigger student's head pokes above the seat, with room for a hop: the tap area reaches up to cover it. */
    val headroom: Float get() = if (scale > 1f) FIGURE_W * FIGURE_ASPECT * (scale - 1f) + FIGURE_W * scale * 0.06f else 0f
}

/** Figure width / seat width (see StudentAtDesk). */
private const val FIGURE_W = 0.8f

private val places = mapOf(
    StudentId.MIRA to SeatPlace(0.05f, 0.40f, 0.62f, rise = 0.07f), // back row, left, up higher
    StudentId.JUNO to SeatPlace(0.55f, 0.40f, 0.62f), // back row, right
    StudentId.PIP to SeatPlace(0.27f, 0.46f, 0.845f, rise = 0.07f, scale = 1.12f), // front, big, up high behind his desk
)

/** Resting poses, which every other move starts from and returns to. */
private const val MIRA_REST_TILT = 8f
private const val JUNO_HAT_TILT = -7f

/** Seat height / seat width (figure + desk, see StudentAtDesk). */
private const val SEAT_ASPECT = 1.34f

/** Where the light switch sits on the wall (its centre): under the shelf, beside the board, at hand height. */
private const val SWITCH_X = 0.08f
private const val SWITCH_Y = 0.325f

/** How deep the navy wash is with the lights off: dark, but the room still faintly there. */
private const val NIGHT_ALPHA = 0.8f

/** Waking order after the lights come on, one by one; they doze off the other way round. */
private val wakeOrder = listOf(StudentId.JUNO, StudentId.MIRA, StudentId.PIP)

/** The entrance stagger plays once per process (cold start), not on every return to the room. */
private object Entrance {
    var played = false
}

/**
 * Whether the button still waits for the lights: the app opened on a dark room and they haven't
 * been on since. Switching off later, or coming back to a dark room, keeps the button.
 */
private object ButtonReveal {
    var pending: Boolean? = null
}

/**
 * When the students last turned to look at whoever walked in (elapsed realtime), across visits, so
 * quick back-and-forth doesn't repeat it. The lights-off wake-up counts as one.
 */
private object Notice {
    var lastAt = Long.MIN_VALUE / 2
}

/** The last finished lesson's board the classmates have read, so each one is read once. */
private object BoardRead {
    var last: ClassroomContract.Learned? = null
}

private const val NOTICE_GAP_MS = 120L
private const val NOTICE_AGAIN_MS = 20_000L
private val noticeOrder = listOf(StudentId.MIRA, StudentId.JUNO, StudentId.PIP)

/**
 * [glanceTick]: each bump is the room changing in a way the students notice (board colour, light or
 * preset): they glance at the board with a brief wordless face, unless a scene or the arrival runs.
 */
@Composable
fun ClassroomContent(state: UiState, onEvent: (UiEvent) -> Unit, glanceTick: Int = 0) {
    val reduced = Chalkroom.reducedMotion
    val colors = Chalkroom.colors
    val room = state.room
    val roomColors = remember(room) { RoomPalette.roomColors(room) }
    val deskColors = remember(room) { RoomPalette.deskColors(room) }
    val slate = RoomPalette.board(room.board)
    if (RoomPalette.darkWall(room.wall)) LightStatusBarIcons()
    val haptics = LocalHapticFeedback.current

    // Lights off (the first open ever, or as the switch was left): the room sits in the dark,
    // students asleep, until the switch. If the app opened dark, there's no button until then.
    val darkAtOpen = remember { !state.lightsOn }
    if (ButtonReveal.pending == null) ButtonReveal.pending = darkAtOpen
    val dark = remember { Animatable(if (darkAtOpen) 1f else 0f) }
    val warm = remember { Animatable(0f) }
    val toggle = remember { Animatable(if (darkAtOpen) 0f else 1f) }
    var awake by remember { mutableStateOf(if (darkAtOpen) emptySet() else StudentId.entries.toSet()) }
    var buttonShown by remember { mutableStateOf(ButtonReveal.pending != true) }
    val buttonIn = remember { Animatable(if (buttonShown) 1f else 0f) }
    var settled by remember { mutableStateOf(!darkAtOpen) }
    // Each bump is Pip's wave at you: when the room first appears (walking in, or the lights coming on).
    var pipWaves by remember { mutableIntStateOf(0) }
    // When Pip may next throw paper at the bin (elapsed realtime): a few seconds after the entering
    // look, the board read or a wake-up. Dropped if it's too soon after the last throw.
    var throwAt by remember { mutableStateOf<Long?>(null) }
    // Scripted scenes between the students: one at a time, the next ambient one due 10 s after
    // the last scene (or tap bubble) ends; null while one plays or before the visit's entry scene.
    val player = remember { ScenePlayer() }
    var sceneJob by remember { mutableStateOf<Job?>(null) }
    var entryJob by remember { mutableStateOf<Job?>(null) }
    var ambientDue by remember { mutableStateOf<Long?>(null) }
    // Each tap on the switch flips it. A new tap cancels whatever sequence is still running and
    // starts the other one from wherever the room is, so the latest tap always wins.
    LaunchedEffect(state.lightsOn) {
        if (!state.lightsOn) {
            // Lights off: the room goes quiet at once, dims, and the students doze off one by one.
            settled = false
            entryJob?.cancel()
            sceneJob?.cancel()
            ambientDue = null
            throwAt = null
            warm.snapTo(0f)
            // A button caught rising in finishes; one that never came stays away.
            if (buttonShown) buttonIn.snapTo(1f)
            if (reduced) {
                toggle.snapTo(0f)
                dark.snapTo(1f)
                awake = emptySet()
                return@LaunchedEffect
            }
            launch { toggle.animateTo(0f, tween(110, easing = Dart)) }
            launch { dark.animateTo(1f, tween(DIM_MS, easing = EaseInOutSine)) }
            delay(DOZE_AFTER_MS)
            wakeOrder.reversed().forEach {
                awake = awake - it
                delay(DOZE_GAP_MS)
            }
            return@LaunchedEffect
        }
        if (dark.value == 0f && settled) return@LaunchedEffect
        if (reduced) {
            toggle.snapTo(1f)
            dark.snapTo(0f)
            awake = StudentId.entries.toSet()
            buttonShown = true
            ButtonReveal.pending = false
            buttonIn.snapTo(1f)
            settled = true
            ambientDue = SystemClock.elapsedRealtime() + AMBIENT_AFTER_MS
            return@LaunchedEffect
        }
        // ~600 ms warm-up with two soft dips, then the students wake one by one, then the button.
        launch { toggle.animateTo(1f, tween(110, easing = Dart)) }
        launch {
            val from = dark.value
            dark.animateTo(
                0f,
                keyframes {
                    durationMillis = 600
                    from at 0 using EaseOutCubic
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
        Notice.lastAt = SystemClock.elapsedRealtime()
        delay(650)
        wakeOrder.forEach {
            awake = awake + it
            delay(WAKE_GAP_MS)
        }
        delay(BUTTON_AT_MS - 650 - wakeOrder.size * WAKE_GAP_MS)
        buttonShown = true
        ButtonReveal.pending = false
        buttonIn.animateTo(1f, settle())
        delay(1_000)
        settled = true
        pipWaves++
        throwAt = SystemClock.elapsedRealtime() + THROW_AFTER_MS
        // No entry scene after the wake-up: the ambient clock starts here.
        ambientDue = SystemClock.elapsedRealtime() + AMBIENT_AFTER_MS
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

    // In view: this screen is resumed (back from a lesson, the app in the foreground). That's a visit.
    val inView by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = inView.isAtLeast(Lifecycle.State.RESUMED)
    val lightsOn by rememberUpdatedState(state.lightsOn)
    val bubbleNow by rememberUpdatedState(state.bubble)

    /** Plays [scene] as a child job a tap on a student can cancel; returns when it ends either way. */
    suspend fun playScene(scene: Scene) = coroutineScope {
        val job = launch { player.play(scene, reduced) }
        sceneJob = job
        job.join()
        sceneJob = null
    }

    // Walking in: an entry scene you catch them in, unless you were here moments ago; then all
    // three turn to look at you, a little apart, and back to their idle. With the lights off,
    // neither: the wake-up starts the room instead.
    var noticeTicks by remember { mutableStateOf(StudentId.entries.associateWith { 0 }) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        try {
            if (lightsOn && settled) entryJob = launch {
                ambientDue = null
                life.quiet = true
                try {
                    delay(if (stagger) ENTRY_AFTER_STAGGER_MS else ENTRY_AFTER_MS)
                    ScenePicker.session.visitStarted(SystemClock.elapsedRealtime())?.let { entry ->
                        snapshotFlow { bubbleNow != null || life.busy }.first { !it }
                        playScene(entry)
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (!reduced && now - Notice.lastAt >= NOTICE_AGAIN_MS) {
                        Notice.lastAt = now
                        delay(250)
                        noticeOrder.forEach {
                            noticeTicks = noticeTicks + (it to noticeTicks.getValue(it) + 1)
                            if (it == StudentId.PIP) pipWaves++
                            delay(NOTICE_GAP_MS)
                        }
                        delay(WAVE_SWAY_MS + 400L)
                        throwAt = SystemClock.elapsedRealtime() + THROW_AFTER_MS
                    }
                } finally {
                    life.quiet = false
                }
                ambientDue = SystemClock.elapsedRealtime() + AMBIENT_AFTER_MS
            }
            awaitCancellation()
        } finally {
            ScenePicker.session.visitEnded(SystemClock.elapsedRealtime())
        }
    }

    // A paused lesson calls: every 8–12 s its student hops and leans toward you, while the room is
    // otherwise still, until you tap the board or them.
    val caller = state.board?.studentId
    var calling by remember(caller) { mutableStateOf(caller != null) }
    var callTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(caller, calling, visible, settled) {
        if (caller == null || !calling || !visible || !settled || reduced) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(8_000, 12_000))
            snapshotFlow { life.busy || life.quiet }.first { !it }
            life.quiet = true
            try {
                callTick++
                delay(CALL_MS)
            } finally {
                life.quiet = false
            }
        }
    }

    // Classroom life, one thing at a time once the room is lit and settled: the classmates read a new
    // board, Pip throws paper at the bin, and every 10 s after the last scene the students play another.
    val learnedNow by rememberUpdatedState(state.learned)
    val seatsNow by rememberUpdatedState(state.seats)
    var reading by remember { mutableStateOf(emptyMap<StudentId, Offset>()) }
    var slowNods by remember { mutableStateOf(StudentId.entries.associateWith { 0 }) }
    var proudId by remember { mutableStateOf<StudentId?>(null) }
    var throwHops by remember { mutableIntStateOf(0) }
    var pipWatching by remember { mutableStateOf(false) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var floorBalls by remember { mutableStateOf(PaperBin.session.onFloor) }

    // A tap bubble going away counts as a scene ending: the next one is 10 s after it.
    LaunchedEffect(state.bubble) {
        if (state.bubble == null && ambientDue != null) ambientDue = SystemClock.elapsedRealtime() + AMBIENT_AFTER_MS
    }

    LaunchedEffect(visible, settled, state.lightsOn) {
        if (!visible || !settled || !state.lightsOn) return@LaunchedEffect
        delay(400)
        while (true) {
            snapshotFlow { life.quiet || life.busy || bubbleNow != null }.first { !it }
            val now = SystemClock.elapsedRealtime()
            val learned = learnedNow
            val due = throwAt
            val sceneDue = ambientDue
            when {
                // B: the other two read what was just taught; the one who learned it sits up.
                !reduced && learned != null && learned != BoardRead.last -> {
                    BoardRead.last = learned
                    life.quiet = true
                    try {
                        val readers = noticeOrder.filter { it != learned.studentId }
                        proudId = learned.studentId
                        coroutineScope {
                            readers.forEachIndexed { i, id ->
                                launch {
                                    val base = boardLook.getValue(id)
                                    reading = reading + (id to base)
                                    delay(READ_TURN_MS + i * 160L)
                                    val lines = learned.lines.size.coerceIn(1, 4)
                                    val fixation = READ_SCAN_MS / lines / 3
                                    repeat(lines) { line ->
                                        listOf(-1f, 0f, 1f).forEach { along ->
                                            reading = reading + (id to base + Offset(along * 0.034f, line * 0.014f))
                                            delay(fixation)
                                        }
                                    }
                                }
                            }
                        }
                        val nodder = readers.random()
                        slowNods = slowNods + (nodder to slowNods.getValue(nodder) + 1)
                        delay(1_000)
                        reading = emptyMap()
                        proudId = null
                        delay(400)
                    } finally {
                        reading = emptyMap()
                        proudId = null
                        life.quiet = false
                    }
                    throwAt = SystemClock.elapsedRealtime() + THROW_AFTER_MS
                }
                // C: Pip and the bin.
                !reduced && due != null && now >= due -> {
                    throwAt = null
                    if (PaperBin.session.canThrow(now)) {
                        val toss = PaperBin.session.toss(now)
                        life.quiet = true
                        try {
                            pipWatching = true
                            throwHops++
                            delay(110)
                            val f = Flight(toss)
                            flight = f
                            f.progress.animateTo(1f, tween(FLIGHT_MS, easing = LinearEasing))
                            if (toss.goesIn) {
                                flight = null
                                delay(160)
                                proudId = StudentId.PIP
                                throwHops++
                                delay(280)
                                throwHops++
                                delay(700)
                                proudId = null
                            } else {
                                f.bounce.animateTo(1f, tween(300, easing = EaseOutCubic))
                                PaperBin.session.landed(toss)
                                floorBalls = PaperBin.session.onFloor
                                flight = null
                                delay(500)
                            }
                            delay(300)
                        } finally {
                            if (!toss.goesIn) {
                                PaperBin.session.landed(toss)
                                floorBalls = PaperBin.session.onFloor
                            }
                            flight = null
                            pipWatching = false
                            proudId = null
                            life.quiet = false
                        }
                    }
                }
                // Scenes: the next ambient one, 10 s after the last scene ended.
                sceneDue != null && now >= sceneDue -> {
                    ambientDue = null
                    val paused = seatsNow.filter { it.paused }.map { it.studentId }.toSet()
                    life.quiet = true
                    try {
                        playScene(ScenePicker.session.nextAmbient(paused))
                    } finally {
                        life.quiet = false
                    }
                    ambientDue = SystemClock.elapsedRealtime() + AMBIENT_AFTER_MS
                }
                else -> delay(200)
            }
        }
    }

    // The room changed under them: each glances at the board, a little apart, with a brief face, then
    // back to idling. Never over a scene, the arrival or the dark; a quick run of changes glances once.
    var glancePoses by remember { mutableStateOf(emptyMap<StudentId, ScenePose>()) }
    LaunchedEffect(glanceTick) {
        if (glanceTick == 0 || !settled || !lightsOn || sceneJob != null || life.quiet || player.bubble != null) return@LaunchedEffect
        life.quiet = true
        try {
            noticeOrder.forEach { id ->
                glancePoses = glancePoses + (id to ScenePose(glanceFace.getValue(id), boardLook.getValue(id)))
                delay(NOTICE_GAP_MS)
            }
            delay(GLANCE_HOLD_MS)
        } finally {
            glancePoses = emptyMap()
            life.quiet = false
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Chalkroom.colors.page),
    ) {
        val w = maxWidth
        val h = maxHeight
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        CompositionLocalProvider(LocalChalkroomColors provides roomColors) {
            Room(Modifier.fillMaxSize(), shelves = room.wall == WallColor.SHELVES)
        }
        FloorBalls(floorBalls, Modifier.fillMaxSize())

        CompositionLocalProvider(LocalChalkroomColors provides RoomPalette.boardColors(slate)) {
            Board(
                board = state.board,
                learned = state.learned,
                slate = slate,
                stars = room.board.stars,
                motto = room.motto.trim(),
                reduced = reduced,
                onTap = {
                    calling = false
                    onEvent(UiEvent.BoardTapped)
                },
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
        }

        CustomizeButton(
            onTap = { onEvent(UiEvent.CustomizeTapped) },
            modifier = Modifier
                .offset(x = w * 0.18f - CUSTOMIZE_SIZE - 4.dp, y = top + 20.dp + h * 0.275f * 0.18f)
                .size(CUSTOMIZE_SIZE)
                .graphicsLayer { alpha = boardIn.value },
        )

        // Back row first, front desk last, so the front sits over the back.
        listOf(StudentId.MIRA, StudentId.JUNO, StudentId.PIP).forEach { id ->
            val seat = state.seats.first { it.studentId == id }
            val place = places.getValue(id)
            val seatW = w * place.width
            val enter = desksIn.getValue(id).value
            Seat(
                seat = seat.copy(motes = if (motesShown && id in awake) seat.motes else emptyList()),
                place = place,
                deskColors = deskColors,
                waveTick = if (id == StudentId.PIP) pipWaves else 0,
                life = life.forStudent(id),
                asleep = id !in awake,
                pose = player.poses[id] ?: glancePoses[id],
                sceneHops = player.hops.getValue(id),
                sceneNods = player.nods.getValue(id),
                startle = settled,
                readAt = reading[id] ?: if (id == StudentId.PIP && pipWatching) towardBin else null,
                headX = reading[id]?.let { boardLook.getValue(id).x * 2.5f },
                slowNodTick = slowNods.getValue(id),
                proud = id == proudId,
                extraHops = if (id == StudentId.PIP) throwHops else 0,
                headroom = seatW * place.headroom,
                doodleStrokes = if (id == StudentId.MIRA) life.doodles else 0,
                noticeTick = noticeTicks.getValue(id),
                callTick = if (id == caller) callTick else 0,
                onTap = {
                    // A tap ends a scene at once; the tapped student's own line takes over.
                    sceneJob?.cancel()
                    if (id == caller) calling = false
                    onEvent(UiEvent.StudentTapped(id))
                },
                modifier = Modifier
                    .offset(x = w * place.x, y = h * place.bottom - seatW * (place.aspect + place.headroom))
                    .width(seatW)
                    .graphicsLayer {
                        alpha = enter
                        translationY = 18.dp.toPx() * (1f - enter)
                    },
            )
            if (room.light.lampsOn) {
                DeskLamp(
                    Modifier
                        .offset(x = w * place.x + seatW * (LAMP_X - LAMP_W / 2), y = h * place.bottom - seatW * (LAMP_BASE + LAMP_W * LAMP_ASPECT))
                        .size(seatW * LAMP_W, seatW * LAMP_W * LAMP_ASPECT)
                        .graphicsLayer { alpha = enter },
                )
            }
        }

        flight?.let { PaperFlight(it, Modifier.fillMaxSize()) }

        // The light of the day over the room, and with the lamps on a warm pool around each one.
        val tint = RoomPalette.light(room.light)
        if (tint.alpha > 0f || room.light.lampsOn) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(tint)
                if (room.light.lampsOn) {
                    places.values.forEach { place ->
                        val seatW = size.width * place.width
                        val c = Offset(size.width * place.x + seatW * LAMP_X, size.height * place.bottom - seatW * (LAMP_BASE + 0.05f))
                        val r = seatW * 0.6f
                        drawCircle(Brush.radialGradient(listOf(RoomPalette.lampGlow.copy(alpha = 0.34f), Color.Transparent), c, r), r, c)
                    }
                }
            }
        }

        SpeechBubble(
            bubble = player.bubble ?: state.bubble,
            reduced = reduced,
            avoid = { id ->
                buildList {
                    // The board (its chalk lines) and every other student's face.
                    add(Rect(Offset((w * 0.18f).toPx(), (top + 20.dp).toPx()), Size((w * 0.64f).toPx(), (h * 0.275f).toPx())))
                    StudentId.entries.filter { it != id }.forEach { add(faceRect(it, w.toPx(), h.toPx())) }
                }
            },
            anchor = { id -> faceRect(id, w.toPx(), h.toPx()) },
            safeTop = { top.toPx() },
            safeBottom = { bottom.toPx() },
            modifier = Modifier.fillMaxSize(),
        )

        // The night: a deep navy wash over the room (it takes taps while the lights are off), and a
        // warm flush as they come on.
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
            if (dark.value > 0f) drawRect(colors.night.copy(alpha = NIGHT_ALPHA * dark.value))
            if (warm.value > 0f) drawRect(colors.switchGlow.copy(alpha = 0.08f * warm.value))
        }

        val switchSize = 64.dp
        LightSwitch(
            on = toggle.asState(),
            dark = dark.asState(),
            glowing = !state.lightsOn && !reduced,
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

    if (state.customizing) RoomSheet(state.room, state.roomCustom, onEvent)
}

private val CUSTOMIZE_SIZE = 48.dp
private const val GLANCE_HOLD_MS = 1_100L

/** The brief face each student makes at a changed room. */
private val glanceFace = mapOf(StudentId.MIRA to Face.CURIOUS, StudentId.JUNO to Face.SCEPTICAL, StudentId.PIP to Face.EXCITED)

/** The desk lamp on each desk's back-right corner, in seat widths: its centre x, width, base above the seat bottom. */
private const val LAMP_X = 0.84f
private const val LAMP_W = 0.2f
private const val LAMP_ASPECT = 1.25f
private const val LAMP_BASE = 0.4f

/**
 * A paint roller drawn in code, for opening the Room sheet: a gold roller on an ink frame with a
 * dark grip, on a small card disc, inside a 48dp touch target.
 */
@Composable
private fun CustomizeButton(onTap: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    Canvas(
        modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onTap)
            .semantics { contentDescription = "Customize classroom" },
    ) {
        val dp = density
        val c = center
        val ink = Stroke(2f * dp, join = StrokeJoin.Round, cap = StrokeCap.Round)
        // A card disc behind it so it reads on any wall.
        drawCircle(colors.ink.copy(alpha = 0.14f), 20f * dp, c + Offset(1.5f * dp, 2.5f * dp))
        drawCircle(colors.card, 20f * dp, c)
        drawCircle(colors.ink, 20f * dp, c, style = Stroke(2f * dp))
        // Roller across the top, the frame bending down from its right end to the grip.
        val rollerTl = Offset(c.x - 12f * dp, c.y - 11f * dp)
        val roller = Size(19f * dp, 8f * dp)
        drawRoundRect(colors.gold, rollerTl, roller, CornerRadius(3f * dp))
        drawLine(colors.goldLedge, Offset(rollerTl.x + 3f * dp, rollerTl.y + 5.5f * dp), Offset(rollerTl.x + roller.width - 3f * dp, rollerTl.y + 5.5f * dp), 1.2f * dp)
        drawRoundRect(colors.ink, rollerTl, roller, CornerRadius(3f * dp), style = ink)
        val frame = Path().apply {
            moveTo(rollerTl.x + roller.width, c.y - 7f * dp)
            lineTo(c.x + 11f * dp, c.y - 7f * dp)
            lineTo(c.x + 11f * dp, c.y - 1f * dp)
            lineTo(c.x, c.y + 1f * dp)
            lineTo(c.x, c.y + 5f * dp)
        }
        drawPath(frame, colors.ink, style = ink)
        val grip = Offset(c.x - 3f * dp, c.y + 5f * dp)
        drawRoundRect(colors.ink2, grip, Size(6f * dp, 10f * dp), CornerRadius(2.5f * dp))
        drawRoundRect(colors.ink, grip, Size(6f * dp, 10f * dp), CornerRadius(2.5f * dp), style = Stroke(1.5f * dp))
    }
}

/** A small desk lamp: a round foot, a bent arm and a shade with the bulb glowing under it. */
@Composable
private fun DeskLamp(modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ink = Stroke(0.06f * w, join = StrokeJoin.Round, cap = StrokeCap.Round)
        // Foot.
        drawOval(colors.ink2, Offset(0.2f * w, h - 0.16f * w), Size(0.6f * w, 0.16f * w))
        drawOval(colors.ink, Offset(0.2f * w, h - 0.16f * w), Size(0.6f * w, 0.16f * w), style = ink)
        // Arm: up, then leaning left to the shade.
        val elbow = Offset(0.62f * w, h * 0.42f)
        drawLine(colors.ink, Offset(0.5f * w, h - 0.08f * w), elbow, 0.07f * w, StrokeCap.Round)
        drawLine(colors.ink, elbow, Offset(0.42f * w, h * 0.2f), 0.07f * w, StrokeCap.Round)
        // Bulb glow, then the shade over it.
        drawCircle(RoomPalette.lampGlow, 0.14f * w, Offset(0.32f * w, h * 0.33f))
        val shade = Path().apply {
            moveTo(0.3f * w, h * 0.04f)
            lineTo(0.56f * w, h * 0.16f)
            lineTo(0.42f * w, h * 0.42f)
            lineTo(0.02f * w, h * 0.24f)
            close()
        }
        drawPath(shade, colors.coral)
        drawPath(shade, colors.ink, style = ink)
    }
}

private const val WAKE_GAP_MS = 260L
private const val DIM_MS = 450
private const val DOZE_AFTER_MS = 250L
private const val DOZE_GAP_MS = 600L
private const val AMBIENT_AFTER_MS = 10_000L
private const val ENTRY_AFTER_MS = 500L
private const val ENTRY_AFTER_STAGGER_MS = 1_100L
private const val READ_TURN_MS = 380L
private const val READ_SCAN_MS = 1_500L
private const val THROW_AFTER_MS = 3_000L
private const val FLIGHT_MS = 950

/** Where each student looks to read the board (fractions of the figure width), from their seat. */
private val boardLook = mapOf(
    StudentId.MIRA to Offset(0.03f, -0.055f),
    // Juno's eyes stay a little lower, clear of her hat brim.
    StudentId.JUNO to Offset(-0.03f, -0.04f),
    StudentId.PIP to Offset(0f, -0.06f),
)

/** Pip watching the bin, up and to the right. */
private val towardBin = Offset(0.05f, -0.045f)

/** A paper ball in the air: [progress] 0 → 1 from Pip's desk to the bin or the floor, then [bounce] on landing. */
private class Flight(val toss: PaperBin.Toss) {
    val progress = Animatable(0f)
    val bounce = Animatable(0f)
}

/** Where missed balls lie on the floor, beside the one already there: (x, y) in bin widths from the bin's bottom centre. */
private val floorSpots = listOf(
    Offset(-1.3f, 0.06f),
    Offset(-0.5f, 0.2f),
    Offset(-1.08f, 0.22f),
    Offset(-1.7f, 0.1f),
    Offset(-0.15f, 0.24f),
)

private fun floorSpot(i: Int, w: Float, h: Float): Pair<Offset, Float> {
    val bw = w * BIN_W
    val s = floorSpots[i]
    // Nearer (lower) balls look a touch bigger.
    return Offset(w * BIN_X + s.x * bw, h * BIN_BOTTOM - 0.07f * bw + s.y * bw) to bw * 0.14f * (1f + 0.4f * s.y)
}

/** The session's missed throws, lying on the floor by the bin (behind the desks). */
@Composable
private fun FloorBalls(spots: List<Int>, modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    if (spots.isEmpty()) return
    Canvas(modifier) {
        spots.forEach { i ->
            val (c, r) = floorSpot(i, size.width, size.height)
            drawBall(colors, c, r, spin = 70f * i)
        }
    }
}

/** The ball on its way: it pops up from Pip's desk and arcs to the bin's rim (dropping in) or to its floor spot. */
@Composable
private fun PaperFlight(f: Flight, modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pip = places.getValue(StudentId.PIP)
        val start = Offset(w * (pip.x + pip.width * 0.72f), h * pip.bottom - w * pip.width * 0.48f)
        val rimY = h * BIN_BOTTOM - w * BIN_W * 1.05f
        val (end, endR) = if (f.toss.goesIn) {
            Offset(w * BIN_X, rimY + w * 0.012f) to w * BIN_W * 0.14f
        } else {
            floorSpot(f.toss.spot, w, h)
        }
        val control = Offset((start.x + end.x) / 2, minOf(start.y, end.y) - h * 0.3f)
        val t = f.progress.value
        val u = 1f - t
        var at = Offset(
            u * u * start.x + 2 * u * t * control.x + t * t * end.x,
            u * u * start.y + 2 * u * t * control.y + t * t * end.y,
        )
        // Landing: one small hop where it falls.
        if (f.bounce.value > 0f) at += Offset(w * 0.01f * f.bounce.value, -h * 0.012f * sin(PI.toFloat() * f.bounce.value))
        // Further away it gets smaller.
        val r = w * 0.028f + (endR - w * 0.028f) * t
        val spin = 600f * t + 90f * f.bounce.value
        if (f.toss.goesIn && t > 0.8f) {
            // Into the bin: hidden as it drops below the rim.
            clipRect(bottom = rimY) { drawBall(colors, at, r, spin) }
        } else {
            drawBall(colors, at, r, spin)
        }
    }
}
private const val CALL_MS = 1_400L

/** Where [id]'s head and face sit on screen, in px: top of the head down to the desk top. */
private fun faceRect(id: StudentId, w: Float, h: Float): Rect {
    val place = places.getValue(id)
    val seatW = w * place.width
    val seatTop = h * place.bottom - seatW * place.aspect
    // The figure's base sits where an unscaled one's would (see StudentAtDesk), so a bigger one reaches higher.
    val figW = seatW * FIGURE_W * place.scale
    val figTop = seatTop + seatW * FIGURE_W * FIGURE_ASPECT - figW * FIGURE_ASPECT
    val left = w * place.x + (seatW - figW) / 2f
    return Rect(left + figW * 0.125f, figTop + figW * 0.3375f, left + figW * 0.875f, figTop + figW * 1.075f)
}

/**
 * The tapped student's line in a speech bubble, cream with an ink outline and a tail toward them.
 * It goes above their head, or else beside them, wherever it stays fully on screen and clear of
 * [avoid] (the board, the other faces). It fades in and out; with animations removed it just
 * appears and goes. One at a time: a new line replaces the last.
 */
@Composable
private fun SpeechBubble(
    bubble: ClassroomContract.Bubble?,
    reduced: Boolean,
    avoid: Density.(StudentId) -> List<Rect>,
    anchor: Density.(StudentId) -> Rect,
    safeTop: Density.() -> Float,
    safeBottom: Density.() -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = Chalkroom.colors
    val style = Chalkroom.type.label.copy(color = colors.ink, fontSize = 15.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, lineBreak = LineBreak.Heading)
    val measurer = rememberTextMeasurer()
    var shown by remember { mutableStateOf(bubble) }
    val alpha = remember { Animatable(if (bubble != null) 1f else 0f) }
    LaunchedEffect(bubble) {
        if (bubble != null) {
            shown = bubble
            if (reduced) alpha.snapTo(1f) else {
                alpha.snapTo(0f)
                alpha.animateTo(1f, tween(160))
            }
        } else {
            if (reduced) alpha.snapTo(0f) else alpha.animateTo(0f, tween(320, easing = EaseInOutSine))
            shown = null
        }
    }
    val b = shown ?: return
    val name = StudentCatalog[b.studentId].name
    Canvas(
        modifier
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = if (reduced) 0f else 4.dp.toPx() * (1f - alpha.value)
            }
            .semantics { contentDescription = "$name says: ${b.text}"; liveRegion = LiveRegionMode.Polite },
    ) {
        val dp = density
        val margin = 10f * dp
        val tail = 10f * dp
        val text = measurer.measure(b.text, style, constraints = Constraints(maxWidth = (190f * dp).toInt()))
        val body = Size(text.size.width + 28f * dp, text.size.height + 16f * dp)
        val head = anchor(b.studentId)
        val blocked = avoid(b.studentId)
        val screen = Rect(margin, safeTop() + margin, size.width - margin, size.height - safeBottom() - margin)
        fun fits(r: Rect) = r.left >= screen.left && r.right <= screen.right && r.top >= screen.top && r.bottom <= screen.bottom &&
            blocked.none { it.overlaps(r) }

        // Above the head, tail down; else to the right or left of the face, tail sideways.
        val aboveLeft = (head.center.x - body.width / 2).coerceIn(screen.left, screen.right - body.width)
        val above = Rect(Offset(aboveLeft, head.top - 2f * dp - tail - body.height), body)
        val right = Rect(Offset(head.right + tail, head.center.y - body.height / 2), body)
        val left = Rect(Offset(head.left - tail - body.width, head.center.y - body.height / 2), body)
        val (r, side) = listOf(above to 0, right to 1, left to -1).firstOrNull { fits(it.first) } ?: (above to 0)

        val shape = Path().apply {
            addRoundRect(RoundRect(r, CornerRadius(14f * dp)))
            val tip = Path().apply {
                when (side) {
                    0 -> {
                        val x = head.center.x.coerceIn(r.left + 20f * dp, r.right - 20f * dp)
                        moveTo(x - 8f * dp, r.bottom - 1f * dp)
                        lineTo(x + 2f * dp, r.bottom + tail)
                        lineTo(x + 6f * dp, r.bottom - 1f * dp)
                    }
                    1 -> {
                        moveTo(r.left + 1f * dp, r.center.y - 7f * dp)
                        lineTo(r.left - tail, r.center.y + 2f * dp)
                        lineTo(r.left + 1f * dp, r.center.y + 6f * dp)
                    }
                    else -> {
                        moveTo(r.right - 1f * dp, r.center.y - 7f * dp)
                        lineTo(r.right + tail, r.center.y + 2f * dp)
                        lineTo(r.right - 1f * dp, r.center.y + 6f * dp)
                    }
                }
                close()
            }
            op(this, tip, PathOperation.Union)
        }
        translate(2f * dp, 3f * dp) { drawPath(shape, colors.ink.copy(alpha = 0.14f)) }
        drawPath(shape, colors.card)
        drawPath(shape, colors.ink, style = Stroke(2f * dp, join = StrokeJoin.Round))
        drawText(text, topLeft = Offset(r.left + 14f * dp, r.top + 8f * dp))
    }
}
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
 * lesson taught, and with neither it says "Class is in!". Its corners always carry a few faint doodles.
 */
@Composable
private fun Board(
    board: ClassroomContract.Board?,
    learned: ClassroomContract.Learned?,
    slate: Color,
    stars: Boolean,
    motto: String,
    reduced: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Chalkroom.colors
    // A new slate colour: a chalk eraser sweeps across left to right, leaving it behind. Instant with
    // animations removed.
    var lastSlate by remember { mutableStateOf(slate) }
    var wipeFrom by remember { mutableStateOf<Color?>(null) }
    val wipe = remember { Animatable(1f) }
    LaunchedEffect(slate) {
        if (slate == lastSlate) return@LaunchedEffect
        val old = lastSlate
        lastSlate = slate
        if (reduced) {
            wipeFrom = null
            return@LaunchedEffect
        }
        wipeFrom = old
        wipe.snapTo(0f)
        wipe.animateTo(1f, tween(WIPE_MS, easing = EaseInOutSine))
        wipeFrom = null
    }
    val mottoPad = if (motto.isNotEmpty()) MOTTO_STRIP else 0.dp
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
            val slateSize = Size(frame.width - 2 * inset, frame.height - 2 * inset)
            val from = wipeFrom
            if (from != null) {
                drawRoundRect(from, slateTl, slateSize, CornerRadius(4f * dp))
                clipRect(right = slateTl.x + slateSize.width * wipe.value) { drawRoundRect(slate, slateTl, slateSize, CornerRadius(4f * dp)) }
            } else {
                drawRoundRect(slate, slateTl, slateSize, CornerRadius(4f * dp))
            }
            if (stars) drawStarSpeckle(slateTl, slateSize, colors.card)
            drawRoundRect(colors.ink.copy(alpha = 0.6f), slateTl, slateSize, CornerRadius(4f * dp), style = Stroke(1.5f * dp))
            drawErasedArcs(slateTl, slateSize, colors.card.copy(alpha = 0.06f))
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
        // The slate's corners: faint stars, scribbles and unfinished sums, under everything else.
        BoardDoodles(
            Modifier.matchParentSize().padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = trayH + 6.dp),
            leftBusy = board == null && learned != null,
            bottomBusy = motto.isNotEmpty(),
        )
        if (board == null && learned == null) {
            ChalkHeadline("Class is in!", Modifier.matchParentSize().padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = trayH + 6.dp + mottoPad))
        } else if (board != null) {
            val color = StudentCatalog[board.studentId].color
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(bottom = trayH + mottoPad)
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
                    .padding(start = 22.dp, top = 16.dp, end = 18.dp, bottom = trayH + 14.dp + mottoPad),
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
        if (motto.isNotEmpty()) {
            BasicText(
                motto,
                style = Chalkroom.type.chalkHand.copy(textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 17.sp, stepSize = 0.5.sp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = trayH + 7.dp)
                    .height(MOTTO_STRIP - 4.dp)
                    .graphicsLayer { rotationZ = -1f }
                    .semantics { contentDescription = "Board motto: $motto" },
            )
        }
        // The eraser on its sweep, a dusty band trailing behind it.
        if (wipeFrom != null) {
            Canvas(Modifier.matchParentSize()) {
                val dp = density
                val inset = 10f * dp
                val slateW = size.width - 2 * inset
                val slateH = size.height - trayH.toPx() - 2 * inset
                val x = inset + slateW * wipe.value
                drawRect(colors.card.copy(alpha = 0.10f), Offset(x - 14f * dp, inset), Size(14f * dp, slateH))
                val ey = inset + slateH * (0.5f + 0.3f * sin(wipe.value * PI.toFloat() * 3f)) - 17f * dp
                val tl = Offset(x - 8f * dp, ey.coerceIn(inset, inset + slateH - 34f * dp))
                drawRoundRect(colors.ink2, tl, Size(16f * dp, 34f * dp), CornerRadius(2f * dp))
                drawRect(colors.lavender2, Offset(tl.x + 11f * dp, tl.y), Size(5f * dp, 34f * dp))
                drawRoundRect(colors.ink, tl, Size(16f * dp, 34f * dp), CornerRadius(2f * dp), style = Stroke(1.2f * dp))
                repeat(5) { i ->
                    drawCircle(colors.card.copy(alpha = 0.35f), (1.5f + i % 2) * dp, Offset(tl.x - (4f + 5f * i) * dp, tl.y + (6f + 6f * i) * dp))
                }
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

private const val WIPE_MS = 400
private val MOTTO_STRIP = 22.dp

/** Space Lab's slate: a faint, fixed speckle of stars. */
private fun DrawScope.drawStarSpeckle(tl: Offset, slate: Size, chalk: Color) {
    val rnd = Random(11)
    repeat(46) {
        val at = Offset(tl.x + rnd.nextFloat() * slate.width, tl.y + rnd.nextFloat() * slate.height)
        drawCircle(chalk.copy(alpha = 0.10f + rnd.nextFloat() * 0.14f), (0.5f + rnd.nextFloat() * 0.9f) * density, at)
    }
}

/**
 * One student at their desk, in their resting pose (Mira's head tilted at you, Juno's hat askew and
 * her eyes on the board with a pencil ready, Pip big and up high behind his desk). Each bump of [waveTick],
 * and his PIP_WAVE idle move, is Pip's wave: no arms, so his whole body sways side to side with a
 * hop and a friendly look at you, still seated. The tap area reaches up over [SeatPlace.headroom], so
 * a bigger student's head is tappable too. Not with animations removed.
 */
@Composable
private fun Seat(
    seat: ClassroomContract.Seat,
    place: SeatPlace,
    deskColors: ChalkroomColors,
    life: RoomLife?,
    doodleStrokes: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    waveTick: Int = 0,
    asleep: Boolean = false,
    noticeTick: Int = 0,
    callTick: Int = 0,
    startle: Boolean = false,
    readAt: Offset? = null,
    headX: Float? = null,
    slowNodTick: Int = 0,
    proud: Boolean = false,
    extraHops: Int = 0,
    headroom: Dp = 0.dp,
    pose: ScenePose? = null,
    sceneHops: Int = 0,
    sceneNods: Int = 0,
) {
    val kind = life?.kind
    val id = seat.studentId
    val colors = Chalkroom.colors
    val reduced = Chalkroom.reducedMotion
    val sway = remember { Animatable(0f) }
    var waveHops by remember { mutableIntStateOf(0) }
    var waveLooks by remember { mutableIntStateOf(0) }
    var waving by remember { mutableStateOf(false) }
    suspend fun wave() {
        if (reduced || id != StudentId.PIP) return
        try {
            waving = true
            waveLooks++
            waveHops++
            coroutineScope {
                launch {
                    delay(WAVE_SWAY_MS / 2L)
                    waveHops++
                }
                sway.animateTo(
                    0f,
                    keyframes {
                        durationMillis = WAVE_SWAY_MS
                        0f at 0 using EaseOutCubic
                        -9f at 130 using EaseInOutSine
                        8f at 330 using EaseInOutSine
                        -6f at 510 using EaseInOutSine
                        4f at 680 using EaseInOutSine
                        -1.5f at 840 using EaseInOutSine
                    },
                )
            }
            delay(300)
        } finally {
            sway.snapTo(0f)
            waving = false
        }
    }
    LaunchedEffect(waveTick) { if (waveTick > 0) wave() }
    LaunchedEffect(kind) { if (kind == LifeKind.PIP_WAVE) wave() }
    val propMotion by animateFloatAsState(
        if (kind == LifeKind.MIRA_DOODLE || kind == LifeKind.PIP_FIDGET || kind == LifeKind.JUNO_PLANT) 1f else 0f,
        tween(if (kind == null) 500 else 900, easing = EaseInOutSine),
        label = "prop",
    )
    // The desk in the room's style; the student and Juno's pencil keep the app palette.
    CompositionLocalProvider(LocalChalkroomColors provides deskColors) { StudentAtDesk(
        studentId = id,
        expression = pose?.face?.expression ?: Expression.IDLE,
        motes = seat.motes,
        notebookOpen = seat.paused,
        // In a scene, the scene says where they look (null: the face's own look).
        lookAt = when {
            pose != null -> pose.lookAt
            else -> readAt ?: kind?.lookAt ?: restGaze[id]
        },
        hopTick = (if (kind == LifeKind.PIP_BOUNCE) life?.tick ?: 0 else 0) + extraHops + waveHops + sceneHops,
        nodTick = sceneNods,
        propMotion = propMotion,
        doodleStrokes = doodleStrokes,
        asleep = asleep || pose?.face?.asleep == true,
        noticeTick = noticeTick + waveLooks,
        callTick = callTick,
        startle = startle,
        headX = headX,
        slowNodTick = slowNodTick,
        proud = proud || waving || pose?.face?.proud == true,
        rise = place.rise,
        figureScale = place.scale,
        sway = sway.value,
        restTilt = if (id == StudentId.MIRA) MIRA_REST_TILT else 0f,
        hatTilt = if (id == StudentId.JUNO) JUNO_HAT_TILT else 0f,
        figureProp = if (id == StudentId.JUNO) ({ drawPencil(size.width, colors) }) else null,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onTap,
            )
            .padding(top = headroom),
    ) }
}

private const val WAVE_SWAY_MS = 1_000

/** Resting gazes: Juno's eyes are on the board, ready to copy. The others look at you. */
private val restGaze = mapOf(StudentId.JUNO to Offset(-0.03f, -0.04f))

/**
 * Juno's small pencil, held up at her right side ready to copy, its lower end behind the desk: gold
 * body, a lavender cap, a cream wooden tip with an ink point. In figure coordinates ([w] wide).
 */
private fun DrawScope.drawPencil(w: Float, colors: ChalkroomColors) {
    val c = Offset(0.82f * w, 0.97f * w)
    val len = 0.36f * w
    val bw = 0.06f * w
    val ink = Stroke(0.016f * w, join = StrokeJoin.Round)
    rotate(-28f, c) {
        val top = c.y - len / 2
        val body = Rect(c.x - bw / 2, top + 0.07f * w, c.x + bw / 2, c.y + len / 2)
        val tip = Path().apply {
            moveTo(body.left, body.top)
            lineTo(c.x, top)
            lineTo(body.right, body.top)
            close()
        }
        drawPath(tip, colors.wood)
        drawPath(tip, colors.ink, style = ink)
        drawPath(
            Path().apply {
                moveTo(c.x - bw * 0.22f, top + 0.07f * w * 0.32f)
                lineTo(c.x, top)
                lineTo(c.x + bw * 0.22f, top + 0.07f * w * 0.32f)
                close()
            },
            colors.ink,
        )
        drawRect(colors.gold, body.topLeft, body.size)
        drawLine(colors.goldLedge, Offset(c.x, body.top + 0.01f * w), Offset(c.x, body.bottom), 0.012f * w)
        val cap = Rect(body.left, body.bottom - 0.05f * w, body.right, body.bottom)
        drawRect(colors.lavender2, cap.topLeft, cap.size)
        drawRect(colors.ink, body.topLeft, body.size, style = ink)
        drawLine(colors.ink, Offset(cap.left, cap.top), Offset(cap.right, cap.top), 0.012f * w)
    }
}

/** One small thing one student does; looks are listed twice so faces change more often than bodies. */
private enum class LifeKind(val student: StudentId, val lookAt: Offset?) {
    MIRA_LOOK_UP(StudentId.MIRA, Offset(0.01f, -0.055f)),
    MIRA_DOODLE(StudentId.MIRA, Offset(-0.045f, 0.045f)),
    PIP_BOUNCE(StudentId.PIP, null),
    PIP_WAVE(StudentId.PIP, null),
    PIP_FIDGET(StudentId.PIP, Offset(-0.04f, 0.045f)),
    JUNO_GAZE_UP(StudentId.JUNO, Offset(0.02f, -0.05f)),
    JUNO_PLANT(StudentId.JUNO, Offset(-0.05f, 0.035f)),
}

private val lifeBag = listOf(
    LifeKind.MIRA_LOOK_UP, LifeKind.MIRA_LOOK_UP, LifeKind.MIRA_DOODLE,
    LifeKind.PIP_BOUNCE, LifeKind.PIP_FIDGET, LifeKind.PIP_FIDGET, LifeKind.PIP_WAVE,
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
            snapshotFlow { state.quiet }.first { !it }
            val kind = lifeBag.filter { it != last }.random()
            last = kind
            state.start(kind)
            try {
                delay(LIFE_MS)
            } finally {
                state.stop()
            }
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

    /** Someone else is moving (walking in, a scene, a lesson calling): hold off until they're done. */
    var quiet by mutableStateOf(false)

    val busy: Boolean get() = current != null

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
