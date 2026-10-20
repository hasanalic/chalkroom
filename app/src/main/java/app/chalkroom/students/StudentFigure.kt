package app.chalkroom.students

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.chalkroom.shared.StudentId
import app.chalkroom.students.StudentShapes.drawBust
import app.chalkroom.students.StudentShapes.drawFront
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One mote per misconception. [clearProgress] runs 0 → 1 during the clearing sequence. */
@Immutable
data class Mote(val id: String, val clearProgress: Float = 0f)

private const val MOTE_R = 0.065f // of the figure width
private const val LAST_MOTE_SCALE = 1.25f

/**
 * THE character: one composable, used unchanged on every screen. Body, face and motes are drawn
 * in one Canvas from code-built Paths; breathing, blinking, glances and expression tweens run
 * inside. [activeMoteId] gets a full ring and the others a faded one (lesson); with no active mote
 * every ring is full. [motesStill] freezes the motes' drift (a clearing is under way). Each bump of
 * [reactTick] plays the student's own unconvinced reaction (a miss): never red, never a shake.
 * [lookAt] points the eyes somewhere (fractions of the width; the head follows late) and each bump
 * of [hopTick] is a small bounce: the classroom's ambient life uses both. Each bump of [nodTick]
 * is a small nod (the same one a tap gives, without looking up). While [asleep] the eyes are shut,
 * the head droops and the breathing is slower and deeper; waking opens the eyes, blinks, stretches
 * and looks up at you for a beat.
 */
@Composable
fun StudentFigure(
    studentId: StudentId,
    expression: Expression,
    modifier: Modifier = Modifier,
    motes: List<Mote> = emptyList(),
    look: Look = Look.AT_YOU,
    activeMoteId: String? = null,
    motesStill: Boolean = false,
    reactTick: Int = 0,
    lookAt: Offset? = null,
    hopTick: Int = 0,
    nodTick: Int = 0,
    asleep: Boolean = false,
) {
    val profile = StudentCatalog[studentId]
    val colors = Chalkroom.colors
    val reduced = Chalkroom.reducedMotion
    val breath by rememberBreath(studentId, still = reduced)
    val blink by rememberBlink(still = reduced || asleep)
    val glance = rememberGlance(active = !reduced && !asleep && look == Look.AT_YOU && expression.glances)
    val scan by rememberScan()
    val drift by rememberDriftClock(still = reduced || motesStill || motes.any { it.clearProgress > 0f })

    // Tapping anywhere on the student: they look up at you with a tiny nod. Taps aren't consumed,
    // so whatever the student sits in (a desk, a card) still gets its click.
    val attend = remember { Animatable(0f) }
    val nod = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Asleep → awake: eyes open, a blink, a small stretch, then a beat looking up at you.
    val sleep = remember { Animatable(if (asleep) 1f else 0f) }
    val sleepBreath by rememberSleepBreath(studentId, still = reduced || (!asleep && sleep.value == 0f))
    val wakeBlink = remember { Animatable(0f) }
    val wakeStretch = remember { Animatable(0f) }
    LaunchedEffect(asleep) {
        if (asleep) return@LaunchedEffect sleep.snapTo(1f)
        if (sleep.value == 0f) return@LaunchedEffect
        if (reduced) return@LaunchedEffect sleep.snapTo(0f)
        sleep.animateTo(0f, tween(320, easing = EaseOutCubic))
        delay(140)
        wakeBlink.animateTo(1f, tween(70))
        wakeBlink.animateTo(0f, tween(90))
        delay(60)
        wakeStretch.animateTo(1f, tween(360, easing = EaseInOutSine))
        launch { wakeStretch.animateTo(0f, settle()) }
        attend.animateTo(1f, tween(220, easing = Dart))
        delay(550)
        attend.animateTo(0f, tween(500))
    }

    val react = remember { Animatable(0f) }
    LaunchedEffect(reactTick) {
        if (reactTick == 0) return@LaunchedEffect
        val (inMs, outMs) = studentId.reactMs()
        react.animateTo(1f, tween(inMs, easing = Dart))
        react.animateTo(0f, tween(outMs, easing = EaseInOutSine))
    }

    val hop = remember { Animatable(0f) }
    LaunchedEffect(hopTick) {
        if (hopTick == 0) return@LaunchedEffect
        hop.animateTo(1f, tween(150, easing = Dart))
        hop.animateTo(0f, settle())
    }
    LaunchedEffect(nodTick) {
        if (nodTick == 0) return@LaunchedEffect
        nod.animateTo(1f, tween(170))
        nod.animateTo(0f, settle())
    }
    val lookW by animateFloatAsState(if (lookAt != null) 1f else 0f, tween(260, easing = Dart), label = "lookW")
    val lookX by animateFloatAsState(lookAt?.x ?: 0f, tween(260, easing = Dart), label = "lookX")
    val lookY by animateFloatAsState(lookAt?.y ?: 0f, tween(260, easing = Dart), label = "lookY")
    val lookHead by animateFloatAsState(lookAt?.x ?: 0f, tween(560, delayMillis = 130, easing = EaseInOutSine), label = "lookHead")

    val t = expression.targets()
    val readingDy = if (look == Look.READING) 0.045f else 0f
    val eyeScale by tweenFace(t.eyeScale, "eyeScale")
    val eyeDx by tweenFace(t.eyeDx, "eyeDx")
    val eyeDy by tweenFace(t.eyeDy + readingDy, "eyeDy")
    val asym by tweenFace(t.asym, "asym")
    val lid by tweenFace(t.lid, "lid")
    val tilt by tweenFace(t.tilt, "tilt")
    val stretch by tweenFace(t.stretch, "stretch")
    val smile by tweenFace(t.smile, "smile")
    val scanWeight by tweenFace(if (look == Look.READING && !reduced) 1f else 0f, "scanWeight")

    // Each mote condenses in when it first appears, and slides along the arc when another leaves.
    val live = motes.filter { it.clearProgress < 1f }
    val slots = moteSlots(live.size, StudentShapes.face(studentId).moteSpan)
    val appear = motes.map { mote ->
        key(mote.id) {
            val a = remember { Animatable(if (reduced) 1f else 0f) }
            LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
            a
        }
    }
    val angles = motes.map { mote ->
        key(mote.id) {
            val target = slots.getOrElse(live.indexOf(mote).coerceAtLeast(0)) { 270f }
            animateFloatAsState(target, settle(), label = "slot").value
        }
    }
    val lastScale by animateFloatAsState(if (live.size == 1) LAST_MOTE_SCALE else 1f, settle(), label = "last")

    Canvas(
        modifier
            .aspectRatio(1f / FIGURE_ASPECT)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    scope.launch {
                        launch {
                            attend.animateTo(1f, tween(220, easing = Dart))
                            delay(1_100)
                            attend.animateTo(0f, tween(500))
                        }
                        nod.animateTo(1f, tween(170))
                        nod.animateTo(0f, settle())
                    }
                }
            }
            .semantics { contentDescription = "${profile.name}, ${expression.name.lowercase()}, ${live.size} motes" },
    ) {
        val w = size.width
        val h = size.height
        val outline = Stroke(width = 0.032f * w, join = StrokeJoin.Round, cap = StrokeCap.Round)
        val face = StudentShapes.face(studentId)
        val a = attend.value
        val r = react.value
        val re = studentId.reaction(r)
        val z = sleep.value
        val st = wakeStretch.value

        // Body: breathing fills it (taller, a touch narrower), the head tilts and follows the eyes late.
        // The bust runs past the bottom edge and is cropped there, so a tilt never lifts a corner.
        val sx = 1f - 0.006f * breath * (1f - z) - 0.012f * sleepBreath * z - 0.01f * nod.value - 0.015f * st
        val sy = 1f + 0.02f * breath * (1f - z) + 0.04f * sleepBreath * z - 0.03f * z + stretch - 0.03f * nod.value + re.stretch + 0.025f * hop.value + 0.06f * st
        val headTurn = tilt + re.tilt + studentId.sleepTilt() * z + (glance.headX.value * (1f - lookW) + lookHead) * (1f - a) * 55f
        clipRect(bottom = h) { withTransform({
            translate(top = (nod.value * 0.02f + re.lift - 0.05f * hop.value + 0.03f * z - 0.02f * st) * w)
            rotate(headTurn, Offset(w / 2, h))
            scale(sx, sy, Offset(w / 2, h))
        }) {
            drawBust(studentId, profile.color, colors, outline)

            // Eyes, with lids that come down from the top. Looking up at you wins over a glance.
            val gx = (glance.eyeX.value * (1f - lookW) + lookX * lookW) * (1f - a)
            val gy = (glance.eyeY.value * (1f - lookW) + lookY * lookW) * (1f - a) - 0.02f * a
            val dx = (eyeDx * (1f - a) + scan * scanWeight + gx + re.eyeDx) * w
            val dy = (eyeDy * (1f - a) + gy + re.eyeDy) * w
            val ew = face.eyeW * w * eyeScale
            val eh = face.eyeH * w * eyeScale
            // Asleep, the eyes are shut to a level line, like the bottom of a blink.
            val open = 1f - maxOf(blink, z, wakeBlink.value) * 0.9f
            listOf(-1, 1).forEach { side ->
                // A miss evens the eyes out: doubt, not a squint.
                val shrink = if (side == 1) 1f - asym * (1f - r) else 1f
                val center = Offset(
                    (face.center.x + side * face.eyeGap / 2) * w + dx,
                    (face.center.y + side * face.eyeSlope / 2) * w + dy,
                )
                val eyeH = (eh * shrink * open).coerceAtLeast(ew * 0.25f)
                val top = center.y - eyeH / 2
                // A hint of lid would only flatten the eye's top, so lids start showing past 0.06.
                val lids = ((lid + re.lid - 0.06f) / 0.94f).coerceIn(0f, 0.85f)
                val lidY = top + eyeH * lids
                // Upper lids come down only for doubt and blinks. Their edge never slopes down toward
                // the middle of the face (that reads as a frown): it is drawn level on screen, undoing
                // the head tilt and the eye slant.
                val half = ew * 0.8f
                val lidEdge = Path().apply {
                    moveTo(center.x - half, lidY)
                    lineTo(center.x + half, lidY)
                }
                val below = Path().apply {
                    addPath(lidEdge)
                    lineTo(center.x + half, top + eyeH * 2)
                    lineTo(center.x - half, top + eyeH * 2)
                    close()
                }
                // A content eye smiles from below: an upward arc cuts the bottom away (◠), unlined.
                val bottom = top + eyeH
                // The arc spans just past the eye's width, so it curves even on Pip's and Juno's narrow eyes.
                val cut = smile * eyeH * 0.6f
                val span = ew * 0.62f
                val aboveSmile = Path().apply {
                    moveTo(center.x - ew, top - eyeH)
                    lineTo(center.x + ew, top - eyeH)
                    lineTo(center.x + ew, bottom + eyeH)
                    lineTo(center.x + span, bottom + eyeH)
                    lineTo(center.x + span, bottom)
                    quadraticTo(center.x, bottom - 2f * cut, center.x - span, bottom)
                    lineTo(center.x - span, bottom + eyeH)
                    lineTo(center.x - ew, bottom + eyeH)
                    close()
                }
                rotate(-headTurn, center) {
                    clipPath(below) { clipPath(aboveSmile) {
                        rotate(headTurn - side * face.eyeTilt, center) {
                            if (face.oval) {
                                drawOval(colors.ink, Offset(center.x - ew / 2, top), Size(ew, eyeH))
                            } else {
                                drawRoundRect(colors.ink, Offset(center.x - ew / 2, top), Size(ew, eyeH), CornerRadius(ew / 2, ew / 2))
                            }
                        }
                    } }
                    if (lids > 0.22f && blink < 0.5f && z < 0.5f) {
                        drawPath(lidEdge, colors.ink, style = Stroke(0.018f * w, cap = StrokeCap.Round))
                    }
                }
                if (eyeScale > 1.1f && blink < 0.5f) {
                    drawCircle(colors.page, ew * 0.18f * (eyeScale - 1f) * 3f, Offset(center.x - ew * 0.12f, center.y - eyeH * 0.22f))
                }
            }
            drawFront(studentId, profile.color, colors, outline)
        } }

        // Motes: cream circles ringed in the student's shade, on an arc that clears the head.
        val cx = face.moteCenter.x * w
        val cy = face.moteCenter.y * w
        val ry = cy - face.moteTop * w
        val rx = face.moteRx * w
        val eye = Offset((face.center.x + face.eyeGap / 2) * w, face.center.y * w)
        motes.forEachIndexed { i, mote ->
            val appearA = appear[i].value
            val gone = mote.clearProgress
            if (gone >= 1f || appearA <= 0f) return@forEachIndexed
            val rad = angles[i] * PI.toFloat() / 180f
            val phase = i * 2.1f + studentId.ordinal
            val wobX = sin(drift * 0.9f + phase) * 0.012f * w
            val wobY = cos(drift * 0.7f + phase * 1.3f) * 0.012f * w
            val rest = Offset(cx + rx * cos(rad) + wobX, cy + ry * sin(rad) + wobY)
            // Clearing: 0 → 0.5 it fades from its centre outward; 0.5 → 1 the ring drifts into the eye.
            val resolve = (gone / 0.5f).coerceIn(0f, 1f)
            val toEye = ((gone - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val c = Offset(rest.x + (eye.x - rest.x) * toEye, rest.y + (eye.y - rest.y) * toEye)
            val r = MOTE_R * w * appearA * (if (live.size == 1) lastScale else 1f) * (1f - 0.7f * toEye)
            val ringAlpha = (if (activeMoteId == null || mote.id == activeMoteId) 1f else 0.35f) * (1f - toEye)
            drawMote(c, r, resolve, colors.page.copy(alpha = 1f - toEye), profile.shade.copy(alpha = ringAlpha), 0.016f * w)
        }
    }
}

/** Asleep, each head droops its own way, a few degrees. */
private fun StudentId.sleepTilt(): Float = when (this) {
    StudentId.MIRA -> -5f
    StudentId.PIP -> 4f
    StudentId.JUNO -> 6f
}

/** A miss, per student, at [r] 0 → 1 → 0. */
private data class Reaction(val tilt: Float = 0f, val lift: Float = 0f, val stretch: Float = 0f, val lid: Float = 0f, val eyeDx: Float = 0f, val eyeDy: Float = 0f)

private fun StudentId.reaction(r: Float): Reaction = when (this) {
    // Doubt: lids come halfway down, level, and the student leans back a little, eyes aside.
    StudentId.MIRA -> Reaction(tilt = 3f * r, lift = 0.015f * r, stretch = -0.025f * r, lid = 0.55f * r, eyeDx = -0.03f * r)
    StudentId.PIP -> Reaction(tilt = -3f * r, lift = 0.015f * r, stretch = -0.03f * r, lid = 0.55f * r, eyeDx = 0.035f * r)
    StudentId.JUNO -> Reaction(tilt = 4f * r, lift = 0.015f * r, stretch = -0.025f * r, lid = 0.5f * r, eyeDx = 0.025f * r)
}

/** In/out durations; the whole reaction fits inside the lesson's unconvinced beat (550 ms). */
private fun StudentId.reactMs(): Pair<Int, Int> = when (this) {
    StudentId.MIRA -> 200 to 330
    StudentId.PIP -> 140 to 300
    StudentId.JUNO -> 260 to 280
}

/** Arc angles (degrees; 270 is straight up) for [n] motes, centred over the head. */
private fun moteSlots(n: Int, maxSpan: Float): List<Float> {
    if (n <= 1) return listOf(270f)
    val span = minOf(maxSpan, 55f * (n - 1))
    return List(n) { 270f - span / 2 + it * span / (n - 1) }
}

/** A cream disc with a ring; [hollow] 0 → 1 empties it from the centre out. */
private fun DrawScope.drawMote(center: Offset, r: Float, hollow: Float, fill: Color, ring: Color, ringW: Float) {
    if (r <= 0f) return
    val inner = r * hollow
    if (hollow < 1f) drawCircle(fill, (r + inner) / 2, center, style = Stroke(r - inner))
    drawCircle(ring, r, center, style = Stroke(ringW))
}

@Preview(widthDp = 420, heightDp = 200)
@Composable
private fun StudentFigurePreview() {
    ChalkroomTheme {
        androidx.compose.foundation.layout.Row {
            StudentFigure(StudentId.MIRA, Expression.IDLE, Modifier.width(130.dp), motes = listOf(Mote("a"), Mote("b"), Mote("c")))
            StudentFigure(StudentId.PIP, Expression.PUZZLED, Modifier.width(130.dp), motes = listOf(Mote("a")))
            StudentFigure(StudentId.JUNO, Expression.GOT_IT, Modifier.width(130.dp))
        }
    }
}

