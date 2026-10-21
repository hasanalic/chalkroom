package app.chalkroom.feature.choosestudent

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiEffect
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiEvent
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentCatalog
import app.chalkroom.students.StudentFigure
import app.chalkroom.students.StudentShapes
import app.chalkroom.ui.components.BUTTON_LEDGE_DP
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomColors
import app.chalkroom.ui.theme.ChalkroomTheme
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ChooseStudentScreen(
    onRead: (StudentId) -> Unit,
    onResumeLesson: (StudentId) -> Unit,
    viewModel: ChooseStudentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                is UiEffect.Read -> onRead(it.studentId)
                is UiEffect.ResumeLesson -> onResumeLesson(it.studentId)
            }
        }
    }
    ChooseStudentContent(state, viewModel::onEvent)
}

/**
 * Title, then three separate cards (Mira, Pip, Juno) filling the space, each a small poster with its
 * student big on one side and the name and intro on the other, then a gold Continue pinned at the
 * bottom. Tapping a card picks that student; only Continue moves on.
 */
@Composable
fun ChooseStudentContent(state: UiState, onEvent: (UiEvent) -> Unit) {
    // A pick made before this screen was (re)composed is already settled: don't replay it.
    val firstTick = remember { state.selectTick }
    Column(Modifier.fillMaxSize().background(Chalkroom.colors.page).safeDrawingPadding()) {
        Text(
            "Who should learn this?",
            style = Chalkroom.type.headline,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val order = state.students.map { it.studentId }
            state.students.forEachIndexed { i, choice ->
                val picked = state.selected
                val cue = when {
                    picked == null || state.selectTick == firstTick -> null
                    picked == choice.studentId -> Cue.Celebrate(state.selectTick)
                    else -> Cue.LookAtPick(
                        dx = picked.side().compareTo(choice.studentId.side()),
                        dy = if (order.indexOf(picked) < i) -1 else 1,
                        tick = state.selectTick,
                    )
                }
                StudentBox(
                    choice,
                    selected = picked == choice.studentId,
                    cue = cue,
                    onClick = { onEvent(UiEvent.Selected(choice.studentId)) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
        PrimaryButton(
            "Continue",
            { onEvent(UiEvent.Continue) },
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            enabled = state.selected != null,
        )
    }
    state.confirm?.let { ConfirmSheet(it, onEvent) }
}

/** The student already has a paused lesson: say so, and let you pick which lesson to have. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(confirm: ChooseStudentContract.Confirm, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    val name = StudentCatalog[confirm.studentId].name
    ModalBottomSheet(onDismissRequest = { onEvent(UiEvent.DismissConfirm) }, containerColor = colors.page) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$name is still on ${confirm.pausedTopic}", style = Chalkroom.type.headline)
            Text(
                "${stillOpenLine(confirm.stillOpen)} Starting your new notes ends that lesson.",
                style = Chalkroom.type.body,
            )
            PrimaryButton("Teach the new notes", { onEvent(UiEvent.StartNew) }, Modifier.fillMaxWidth().padding(top = 4.dp))
            QuietButton("Continue ${confirm.pausedTopic}", { onEvent(UiEvent.ContinuePaused) }, Modifier.fillMaxWidth())
        }
    }
}

private fun stillOpenLine(n: Int) = if (n == 1) "1 thing still doesn't click." else "$n things still don't click."

/**
 * The highest each student gets in their idle loop, in the figure canvas (figure widths), so the
 * card's top edge never cuts it: the chalk fleck Mira hunts above her head, the top of Pip's highest
 * hop, Juno's hat knob.
 */
private fun StudentId.headTop(): Float = when (this) {
    StudentId.MIRA -> FLECK_TOP
    StudentId.PIP -> PIP_TIP - MAX_HOP
    StudentId.JUNO -> 0.38f
}

/** A mote's radius (the last one's, the biggest) plus its ring, figure widths. */
private const val MOTE_ROOM = 0.09f

/** Where across her canvas Mira grows from when she celebrates (0 left, 1 right): her ears drift right, away from the title. */
private const val MIRA_GROW_X = 0.4f

/** Pip's tip is where his jump has to clear the box top from. */
private const val PIP_TIP = 0.33f

/** Zig-zag: Mira and Juno stand on the right of their cards, Pip on the left. -1 left, +1 right. */
private fun StudentId.side(): Int = if (this == StudentId.PIP) -1 else 1

/**
 * How far left and right of the figure canvas (figure widths) a student ever reaches: Mira leaning
 * and lunging with her sparkles, Pip's widest landing squash and his sparkles, Juno's sway, and a
 * paused lesson's motes. The text beside them starts past this.
 */
private fun StudentId.reach(motes: Boolean): Pair<Float, Float> = when (this) {
    StudentId.MIRA -> (if (motes) -0.01f else 0.03f) to (if (motes) 1.07f else 1.0f)
    StudentId.PIP -> -0.08f to 1.12f
    StudentId.JUNO -> 0f to 0.97f
}

/** How big each student may be drawn, as a share of the card's height, before the room runs out. */
private fun StudentId.sizeCap(): Float = when (this) {
    StudentId.MIRA -> 1.0f
    StudentId.PIP -> 0.85f
    StudentId.JUNO -> 0.98f
}

/** Where the student stands on a card [width] × [height] px: the figure canvas's left edge and width. */
private class Spot(val x: Float, val w: Float, val textX: Float, val textW: Float)

private fun Density.spot(choice: ChooseStudentContract.Choice, width: Float, height: Float): Spot {
    val id = choice.studentId
    val motes = choice.motes.isNotEmpty()
    val (left, right) = id.reach(motes)
    val pad = 16.dp.toPx()
    val edge = 6.dp.toPx()
    val gap = 12.dp.toPx()
    // A paused lesson's motes ride in an arc above the head: keep them inside the card too.
    val top = if (!motes) id.headTop() else minOf(id.headTop(), StudentShapes.face(id).moteTop - MOTE_ROOM)
    val w = minOf(
        (height - 6.dp.toPx()) / (FIGURE_H - top),
        height * id.sizeCap(),
        (width - pad - edge - gap - MIN_TEXT.dp.toPx()) / (right - left),
    ).coerceAtLeast(1f)
    return if (id.side() > 0) {
        val x = width - edge - right * w
        Spot(x, w, pad, x + left * w - gap - pad)
    } else {
        val x = edge - left * w
        val textX = x + right * w + gap
        Spot(x, w, textX, width - pad - textX)
    }
}

/** The narrowest the name and intro column may get before the student shrinks instead. */
private const val MIN_TEXT = 120

private val CardShape20 = RoundedCornerShape(20.dp)

/**
 * One student's card: a soft wash of the student's colour (deeper once picked, on a ledge in their
 * shade), a quiet backdrop of their own, the student big on one side, cropped by the card's bottom
 * edge but free to jump out of its top, and the name and intro on the other side.
 */
@Composable
private fun StudentBox(
    choice: ChooseStudentContract.Choice,
    selected: Boolean,
    cue: Cue?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = Chalkroom.reducedMotion
    val id = choice.studentId
    val profile = StudentCatalog[id]
    val wash = animateFloatAsState(
        if (selected) PICKED_WASH else WASH,
        if (reduced) snap() else tween(220),
        label = "wash",
    )
    val ledge by animateDpAsState(
        if (selected) BUTTON_LEDGE_DP.dp else 0.dp,
        if (reduced) snap() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "ledge",
    )

    val pose = remember { Pose() }
    LaunchedEffect(cue, reduced) {
        pose.settle(snap = reduced)
        if (reduced) return@LaunchedEffect
        when (cue) {
            is Cue.Celebrate -> pose.celebrate(id)
            is Cue.LookAtPick -> pose.lookAtPick(cue.dx, cue.dy)
            null -> Unit
        }
        pose.idleLoop(id)
    }

    Layout(
        content = {
            CardBackdrop(choice, wash, selected)
            BoxText(choice)
            StudentSlot(choice, pose)
        },
        modifier = modifier
            .drawBehind {
                if (ledge > 0.dp) {
                    val r = 20.dp.toPx()
                    drawRoundRect(profile.shade, Offset(0f, ledge.toPx()), size, CornerRadius(r))
                }
            }
            .drawWithContent {
                // Cropped by the card's bottom edge and rounded corners; open above, so a jump or a popping hat can
                // rise out of it, and open to the sides above the corners, so a grown Mira can spill past her edge.
                val r = 20.dp.toPx()
                val open = Path().apply {
                    addRoundRect(
                        RoundRect(
                            0f, -size.height * 6, size.width, size.height,
                            bottomLeftCornerRadius = CornerRadius(r), bottomRightCornerRadius = CornerRadius(r),
                        ),
                    )
                    addRect(Rect(-size.width, -size.height * 6, size.width * 2, size.height - r))
                }
                clipPath(open) { this@drawWithContent.drawContent() }
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
    ) { (backdrop, text, figure), c ->
        val width = c.maxWidth
        val height = c.maxHeight
        val spot = spot(choice, width.toFloat(), height.toFloat())
        val textW = spot.textW.toInt().coerceAtLeast(1)
        val b = backdrop.measure(Constraints.fixed(width, height))
        val t = text.measure(Constraints(minWidth = textW, maxWidth = textW))
        val w = spot.w.toInt()
        val h = (w * FIGURE_H).toInt()
        val f = figure.measure(Constraints.fixed(w, h))
        layout(width, height) {
            b.place(0, 0)
            t.place(spot.textX.toInt(), 16.dp.roundToPx())
            f.place(spot.x.toInt(), height - h)
        }
    }
}

/** The card's wash of the student's colour over the page, at rest and once picked. */
private const val WASH = 0.18f
private const val PICKED_WASH = 0.30f

/**
 * Everything behind the student, inside the card's shape: the wash, a fixed seeded grain, and each
 * student's own mark (Mira's soft circle, Pip's speed lines, Juno's stars and orbit). While the card
 * is picked its surface comes alive ([drawSurface]); it fades in and out over 300ms, and never runs
 * with animations removed.
 */
@Composable
private fun CardBackdrop(choice: ChooseStudentContract.Choice, wash: State<Float>, selected: Boolean) {
    val colors = Chalkroom.colors
    val id = choice.studentId
    val profile = StudentCatalog[id]
    val reduced = Chalkroom.reducedMotion
    val live = selected && !reduced
    val life = animateFloatAsState(if (live) 1f else 0f, if (reduced) snap() else tween(300), label = "surface")
    val clock = remember { mutableFloatStateOf(0f) }
    val running by remember { derivedStateOf { life.value > 0f } }
    LaunchedEffect(live || running) {
        if (!live && !running) return@LaunchedEffect
        val start = withFrameNanos { it } - (clock.floatValue * 1e9f).toLong()
        while (true) withFrameNanos { clock.floatValue = (it - start) / 1e9f }
    }
    Box(
        Modifier.clip(CardShape20).drawWithCache {
            val spot = spot(choice, size.width, size.height)
            val grain = Random(id.ordinal * 31 + 7).let { r ->
                val n = (size.width * size.height / (14.dp.toPx() * 14.dp.toPx())).toInt()
                List(n) { Offset(r.nextFloat() * size.width, r.nextFloat() * size.height) }
            }
            val dark = grain.filterIndexed { i, _ -> i % 2 == 0 }
            val light = grain.filterIndexed { i, _ -> i % 2 == 1 }
            val dot = 1.3.dp.toPx()
            onDrawBehind {
                val l = life.value
                val t = clock.floatValue
                drawRect(profile.color.copy(alpha = wash.value).compositeOver(colors.page))
                drawPoints(dark, PointMode.Points, colors.ink.copy(alpha = 0.07f), dot, StrokeCap.Round)
                drawPoints(light, PointMode.Points, colors.card.copy(alpha = 0.5f), dot, StrokeCap.Round)
                if (l > 0f) drawSurface(id, spot, t, l, profile.shade)
                translate(spot.x, size.height - spot.w * FIGURE_H) {
                    drawMark(id, spot.w, profile.shade, colors, if (l > 0f) t else 0f, l)
                }
            }
        },
    )
}

/** 0 → 1 → 0 on a sine over [x] in 0..1, 0 outside: one soft swell. */
private fun swell(x: Float): Float = if (x <= 0f || x >= 1f) 0f else sin(PI.toFloat() * x)

/** A fixed pseudo-random 0..1 for ([a], [b]), so a bolt's spot is the same on every frame of its life. */
private fun hash01(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777216f
}

private const val TAU = (2 * PI).toFloat()

/**
 * The picked card's own surface life, in card px, at [t] seconds, faded by [life]. Only what spans the
 * whole card lives here; what belongs to the mark (Pip's line flashes and pulse, Juno's stars and
 * orbit light) is drawn by [drawMark].
 */
private fun DrawScope.drawSurface(
    id: StudentId,
    spot: Spot,
    t: Float,
    life: Float,
    shade: Color,
) {
    val w = size.width
    val h = size.height
    when (id) {
        StudentId.MIRA -> {
            // Hollow circles drifting on slow sines (ease-in-out by nature). 0 and 1 trade size; 2 and 3
            // share a band and cross each other now and then, looking merged for a moment.
            val stroke = Stroke(1.6.dp.toPx())
            val grow = sin(TAU * t / 11f)
            data class Ring(val cx: Float, val cy: Float, val ax: Float, val ay: Float, val px: Float, val py: Float, val ph: Float, val r: Float, val breathe: Float)
            listOf(
                Ring(0.30f, 0.35f, 0.18f, 0.12f, 19f, 14f, 0.0f, 30f, 0.35f),
                Ring(0.62f, 0.62f, 0.20f, 0.14f, 23f, 17f, 2.1f, 26f, -0.35f),
                Ring(0.40f, 0.70f, 0.26f, 0.06f, 17f, 21f, 0.6f, 20f, 0f),
                Ring(0.52f, 0.66f, 0.26f, 0.06f, 17f, 25f, 3.6f, 15f, 0f),
                Ring(0.78f, 0.28f, 0.12f, 0.12f, 27f, 19f, 4.4f, 40f, 0f),
            ).forEach { g ->
                val c = Offset(
                    (g.cx + g.ax * sin(TAU * t / g.px + g.ph)) * w,
                    (g.cy + g.ay * sin(TAU * t / g.py + g.ph * 0.7f)) * h,
                )
                drawCircle(shade.copy(alpha = 0.22f * life), g.r.dp.toPx() * (1f + g.breathe * grow), c, style = stroke)
            }
        }
        StudentId.PIP -> {
            // Bolts that each pop up at their own spot, hold, and fade; every other one streaks as it goes.
            // One showing per bolt per cycle (1.5s or longer), so nothing flickers faster than ~1 a second.
            val periods = floatArrayOf(1.7f, 2.1f, 1.5f, 2.4f)
            val angles = floatArrayOf(-35f, 200f, 150f, 20f)
            for (i in periods.indices) {
                val p = periods[i]
                val shifted = t + i * 0.43f
                val n = floor(shifted / p).toInt()
                val x = shifted / p - n
                if (x >= 0.7f) continue
                val a = when {
                    x < 0.12f -> sin(PI.toFloat() / 2f * x / 0.12f)
                    x < 0.35f -> 1f
                    else -> 1f - sin(PI.toFloat() / 2f * (x - 0.35f) / 0.35f)
                }
                val fade = ((x - 0.35f) / 0.35f).coerceIn(0f, 1f)
                val streak = if (i % 2 == 0) (15f + 10f * hash01(i, n + 99)).dp.toPx() * (1f - cos(PI.toFloat() / 2f * fade)) else 0f
                val rad = Math.toRadians(angles[i].toDouble()).toFloat()
                var at = Offset.Zero
                for (k in 0 until 6) {
                    at = Offset(
                        (0.06f + 0.88f * hash01(i * 7 + k, n)) * w,
                        (0.12f + 0.76f * hash01(i * 13 + k, n + 50)) * h,
                    )
                    val inText = at.x in (spot.textX - 8.dp.toPx())..(spot.textX + spot.textW + 8.dp.toPx()) && at.y < 76.dp.toPx()
                    if (!inText) break
                }
                at += Offset(cos(rad), sin(rad)) * streak
                val s = (15f + 5f * hash01(i, n + 7)).dp.toPx() * (0.85f + 0.15f * a)
                drawBolt(at, s, (hash01(i, n + 3) - 0.5f) * 40f, shade.copy(alpha = 0.45f * a * life))
            }
        }
        StudentId.JUNO -> Unit
    }
}

/** A small flat lightning bolt [s] px tall, centred on [c], turned [deg]. */
private fun DrawScope.drawBolt(c: Offset, s: Float, deg: Float, color: Color) {
    val path = Path().apply {
        fun p(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
        p(0.12f, -0.5f).let { moveTo(it.x, it.y) }
        listOf(p(-0.22f, 0.05f), p(0.02f, 0.05f), p(-0.12f, 0.5f), p(0.24f, -0.08f), p(0.0f, -0.08f)).forEach { lineTo(it.x, it.y) }
        close()
    }
    rotate(deg, c) { drawPath(path, color) }
}

/**
 * Each student's own quiet mark behind them, in their figure canvas (figure widths [w]). At [life] 0
 * it is the still mark; above 0 (the picked card) Pip's lines flash now and then over a soft pulse, and
 * Juno's stars light up in turn while a point of light travels the still orbit.
 */
private fun DrawScope.drawMark(id: StudentId, w: Float, shade: Color, colors: ChalkroomColors, t: Float = 0f, life: Float = 0f) {
    when (id) {
        StudentId.MIRA -> drawCircle(colors.card.copy(alpha = 0.4f), 0.5f * w, Offset(0.53f, 0.8f) * w)
        StudentId.PIP -> {
            if (life > 0f) {
                // A soft pink energy breathing behind him: many faint flat discs stacked, so no hard band shows.
                val breath = 0.5f - 0.5f * cos(TAU * t / 2.2f)
                val centre = Offset(0.52f, 0.80f) * w
                for (k in 0 until 8) {
                    val r = 0.30f + 0.04f * k + 0.05f * breath
                    drawCircle(StudentCatalog.pip.color.copy(alpha = (0.012f + 0.014f * breath) * life), r * w, centre)
                }
            }
            // A flash of the lines every 2.6s: a 0.35s swell, so never more than ~3 a second.
            val flash = swell(((t % 2.6f) - 1.9f) / 0.35f) * life
            // Thin strokes parallel to his top-left edge, trailing behind the bolt.
            val from = Offset(0.05f, 0.90f)
            val along = Offset(0.785f, -0.619f) * 0.904f
            val out = Offset(-0.619f, -0.785f)
            listOf(Triple(0.09f, 0.34f, 0.66f), Triple(0.16f, 0.26f, 0.50f), Triple(0.23f, 0.34f, 0.46f)).forEach { (d, a, b) ->
                drawLine(
                    shade.copy(alpha = 0.3f + 0.35f * flash),
                    (from + along * a + out * d) * w,
                    (from + along * b + out * d) * w,
                    0.012f * w * (1f + 0.4f * flash),
                    StrokeCap.Round,
                )
            }
        }
        StudentId.JUNO -> {
            val pivot = Offset(0.5f, 0.66f) * w
            rotate(-10f, pivot) {
                drawOval(
                    shade.copy(alpha = 0.28f),
                    Offset(0.02f, 0.53f) * w,
                    Size(0.96f, 0.26f) * w,
                    style = Stroke(0.008f * w),
                )
                if (life > 0f) {
                    // A thin point of light going round the orbit (once every 14s), with a short fading tail. It
                    // starts at the left end and rises over the top, where the orbit shows past her.
                    for (k in 0..5) {
                        val a = PI.toFloat() + TAU * (t - k * 0.12f) / 14f
                        val p = Offset(0.5f + 0.48f * cos(a), 0.66f + 0.13f * sin(a)) * w
                        val fade = 1f - k / 6f
                        if (k == 0) drawCircle(colors.card.copy(alpha = 0.3f * life), 0.03f * w, p)
                        drawCircle(colors.card.copy(alpha = 0.95f * fade * life), (0.014f - 0.0015f * k) * w, p)
                    }
                }
            }
            val stars = listOf(Triple(0.09f, 0.46f, 0.032f), Triple(0.17f, 0.34f, 0.02f), Triple(0.86f, 0.40f, 0.026f), Triple(0.94f, 0.62f, 0.018f))
            stars.forEachIndexed { i, (x, y, r) ->
                val c = Offset(x, y) * w
                drawStar(c, r * w, shade.copy(alpha = 0.35f))
                if (life <= 0f) return@forEachIndexed
                // Lit one after another (one every 1.1s, each glowing 1.6s), then a quiet beat.
                val lit = swell(((t - i * 1.1f) % 6f) / 1.6f)
                // Now and then a short twinkle: a 0.3s cross flare on a star picked by the 3.4s slot.
                val slot = floor(t / 3.4f).toInt()
                val pick = (hash01(slot, 5) * stars.size).toInt()
                val pick2 = if (hash01(slot, 9) > 0.6f) (pick + 2) % stars.size else -1
                val tw = if (i == pick || i == pick2) swell((t - slot * 3.4f - 1.2f) / 0.3f) else 0f
                if (lit > 0f) {
                    drawCircle(colors.card.copy(alpha = 0.35f * lit * life), r * w * 2.2f, c)
                    drawStar(c, r * w * (1f + 0.5f * lit), colors.card.copy(alpha = 0.95f * lit * life))
                }
                if (tw > 0f) {
                    val arm = r * w * (1.4f + 1.4f * tw)
                    val cross = colors.card.copy(alpha = 0.9f * tw * life)
                    drawLine(cross, c - Offset(arm, 0f), c + Offset(arm, 0f), 1.dp.toPx(), StrokeCap.Round)
                    drawLine(cross, c - Offset(0f, arm), c + Offset(0f, arm), 1.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val star = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(star, color)
}

@Composable
private fun BoxText(choice: ChooseStudentContract.Choice) {
    val colors = Chalkroom.colors
    val profile = StudentCatalog[choice.studentId]
    val end = choice.studentId.side() < 0
    Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        // A small tag above the name, so it never squeezes the name at narrow widths.
        if (choice.paused) {
            Text(
                "Paused lesson",
                style = Chalkroom.type.small.copy(fontSize = 11.sp, lineHeight = 15.sp, color = colors.ink, fontWeight = Chalkroom.type.label.fontWeight),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(50)).background(colors.lavender)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(profile.name, style = Chalkroom.type.title.copy(fontSize = 20.sp, lineHeight = 25.sp))
        Text(
            profile.cardIntro,
            style = Chalkroom.type.small.copy(fontSize = 12.sp, lineHeight = 16.sp, color = colors.ink2),
            textAlign = if (end) TextAlign.End else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * The student and the props drawn around them. The card crops them only at its bottom edge, so a
 * jump or a popping hat can rise out of it.
 */
@Composable
private fun StudentSlot(choice: ChooseStudentContract.Choice, pose: Pose) {
    val colors = Chalkroom.colors
    val id = choice.studentId
    val profile = StudentCatalog[id]
    val reduced = Chalkroom.reducedMotion
    Box(
        Modifier
            .onPlaced {
                // Lift (figure widths) that takes Pip's tip a little past the top of his box.
                val w = it.size.width.toFloat()
                if (w > 0f) pose.jumpLift = (it.positionInParent().y / w) + PIP_TIP + 0.2f
            },
    ) {
        // The student, and Juno's book, which moves with her. Mira grows around a point a little left of her
        // middle, so her ears rise past the card's top while staying clear of the title.
        Box(
            Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(MIRA_GROW_X, 1f)
                scaleX = pose.grow.value
                scaleY = pose.grow.value
            }.graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = pose.scaleX.value
                scaleY = pose.scaleY.value
                rotationZ = pose.lean.value
                rotationY = pose.spin.value
                cameraDistance = 16f * density
            },
        ) {
            StudentFigure(
                id,
                pose.expression,
                Modifier.fillMaxWidth(),
                motes = choice.motes,
                lookAt = pose.lookAt,
                headX = pose.headX,
                lift = pose.lift.value,
                ears = pose.ears.value,
                hatLift = pose.hat.value,
            )
            if (id == StudentId.JUNO) {
                Canvas(Modifier.matchParentSize()) { drawBook(size.width, pose.page.value, colors) }
            }
        }
        // Things in the air, which don't lean with the student.
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            if (reduced) return@Canvas
            when (id) {
                StudentId.MIRA -> {
                    drawFleck(pose.fleck.value, w, pose.fleckAlpha.value, colors)
                    drawSparkles(pose.sparkleAt, pose.sparkle.value, w, listOf(colors.page), colors.ink, spread = 0.85f)
                }
                StudentId.PIP -> drawSparkles(pose.sparkleAt, pose.sparkle.value, w, listOf(colors.gold, profile.color), colors.ink, spread = 0.8f)
                StudentId.JUNO -> Unit
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun ChooseStudentPreview() {
    ChalkroomTheme {
        ChooseStudentContent(
            UiState(
                StudentId.entries.map {
                    ChooseStudentContract.Choice(
                        it, paused = it == StudentId.JUNO,
                        motes = if (it == StudentId.JUNO) listOf(Mote("a"), Mote("b")) else emptyList(),
                    )
                },
                selected = StudentId.PIP,
            ),
            onEvent = {},
        )
    }
}
