package app.chalkroom.students

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.chalkroom.shared.StudentId
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One mote per misconception. [clearProgress] runs 0 → 1 during the clearing sequence. */
@Immutable
data class Mote(val id: String, val clearProgress: Float = 0f)

/**
 * THE character: one composable, used unchanged on every screen. Body, face and motes are drawn
 * in one Canvas from code-built Paths; breathing, blinking and expression tweens run inside.
 */
@Composable
fun StudentFigure(
    studentId: StudentId,
    expression: Expression,
    modifier: Modifier = Modifier,
    motes: List<Mote> = emptyList(),
    look: Look = Look.AT_YOU,
) {
    val profile = StudentCatalog[studentId]
    val colors = Chalkroom.colors
    val breath by rememberBreath(studentId)
    val blink by rememberBlink(studentId)
    val scan by rememberScan()
    val orbit by rememberOrbit()

    val t = expression.targets()
    val readingDy = if (look == Look.READING) 0.045f else 0f
    val eyeScale by tweenFace(t.eyeScale, "eyeScale")
    val eyeDx by tweenFace(t.eyeDx, "eyeDx")
    val eyeDy by tweenFace(t.eyeDy + readingDy, "eyeDy")
    val asym by tweenFace(t.asym, "asym")
    val mouthCurve by tweenFace(t.mouthCurve, "mouthCurve")
    val mouthWave by tweenFace(t.mouthWave, "mouthWave")
    val mouthOpen by tweenFace(t.mouthOpen, "mouthOpen")
    val scanWeight by tweenFace(if (look == Look.READING) 1f else 0f, "scanWeight")

    // Each mote condenses in when it first appears.
    val appear = motes.map { mote ->
        key(mote.id) {
            val a = remember { Animatable(0f) }
            LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
            a
        }
    }

    Canvas(
        modifier
            .aspectRatio(1f / FIGURE_ASPECT)
            .semantics { contentDescription = "${profile.name}, ${expression.name.lowercase()}, ${motes.count { it.clearProgress < 1f }} motes" },
    ) {
        val w = size.width
        val outline = Stroke(width = 0.028f * w, join = StrokeJoin.Round, cap = StrokeCap.Round)
        val face = StudentShapes.face(studentId)

        // Body (breathing scales it from the bottom center).
        val sx = breath
        val sy = 1f + (breath - 1f) * 1.6f
        scaleFromBottom(sx, sy) {
            StudentShapes.tufts(studentId, size).forEach {
                drawPath(it, profile.color)
                drawPath(it, colors.ink, style = outline)
            }
            val body = StudentShapes.body(studentId, size)
            drawPath(body, profile.color)
            drawPath(body, colors.ink, style = outline)
            if (studentId == StudentId.JUNO) {
                val (c, r) = StudentShapes.junoFace(size)
                drawCircle(colors.junoFace, r, c)
                drawCircle(colors.ink, r, c, style = Stroke(0.018f * w))
                drawPath(StudentShapes.junoStar(size), colors.chalk)
                drawPath(StudentShapes.junoStar(size), colors.ink, style = Stroke(0.012f * w, join = StrokeJoin.Round))
            }

            // Eyes: capsules. Eyes lead: they move by the look/expression offsets.
            val fc = Offset(face.center.x * w, face.center.y * size.height)
            val dx = (eyeDx + scan * scanWeight) * w
            val dy = eyeDy * w
            val ew = 0.062f * w * eyeScale
            val eh = 0.105f * w * eyeScale
            val open = 1f - blink * 0.9f
            listOf(-1, 1).forEach { side ->
                val shrink = if (side == 1) 1f - asym else 1f
                val center = Offset(fc.x + side * face.eyeGap * w / 2 + dx, fc.y + dy - (if (side == -1) face.eyeTilt / 400f * w else 0f))
                rotate(side * face.eyeTilt, center) {
                    val h = (eh * shrink * open).coerceAtLeast(ew * 0.25f)
                    drawRoundRect(
                        color = colors.ink,
                        topLeft = Offset(center.x - ew / 2, center.y - h / 2),
                        size = Size(ew, h),
                        cornerRadius = CornerRadius(ew / 2, ew / 2),
                    )
                    if (eyeScale > 1.1f && blink < 0.5f) {
                        drawCircle(colors.paper, ew * 0.18f * (eyeScale - 1f) * 3f, Offset(center.x - ew * 0.12f, center.y - h * 0.22f))
                    }
                }
            }

            // Mouth: one cubic that can smile, frown or wobble; an "o" fades in for GOT_IT.
            val mw = 0.12f * w
            val my = fc.y + 0.11f * w + dy * 0.3f
            val mx = fc.x + dx * 0.4f
            val curve = mouthCurve * 0.07f * w
            val wave = mouthWave * 0.03f * w
            val mouth = Path().apply {
                moveTo(mx - mw / 2, my)
                cubicTo(mx - mw / 6, my + curve * 0.75f - wave, mx + mw / 6, my + curve * 0.75f + wave, mx + mw / 2, my)
            }
            drawPath(mouth, colors.ink.copy(alpha = 1f - mouthOpen), style = Stroke(0.022f * w, cap = StrokeCap.Round))
            if (mouthOpen > 0.01f) {
                val ow = 0.055f * w * mouthOpen
                val oh = 0.07f * w * mouthOpen
                drawOval(colors.ink, Offset(mx - ow / 2, my - oh * 0.2f), Size(ow, oh))
            }
        }

        // Motes orbit the head; each is a small fog cluster.
        val count = motes.size
        motes.forEachIndexed { i, mote ->
            val a = appear[i].value
            val gone = mote.clearProgress
            if (gone >= 1f || a <= 0f) return@forEachIndexed
            val angle = 2 * PI * (i.toFloat() / count + orbit)
            val ox = w * (face.center.x + 0.36f * cos(angle).toFloat())
            val oy = size.height * face.moteCenterY + 0.075f * w * sin(angle).toFloat() + 0.015f * w * sin(angle * 3).toFloat()
            // Clearing: 0 → 0.5 the fog resolves (tightens, firms up); 0.5 → 1 it drifts into the eye.
            val resolve = (gone / 0.5f).coerceIn(0f, 1f)
            val drift = ((gone - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val eye = Offset(face.center.x * w + face.eyeGap * w / 2, face.center.y * size.height)
            val c = Offset(ox + (eye.x - ox) * drift, oy + (eye.y - oy) * drift)
            val alpha = (colors.mote.alpha * (1f + 0.4f * resolve)).coerceAtMost(1f) * a * (1f - drift)
            drawMote(c, 0.07f * w * a * (1f - 0.3f * resolve) * (1f - 0.75f * drift), colors.mote.copy(alpha = alpha))
        }
    }
}

private inline fun DrawScope.scaleFromBottom(sx: Float, sy: Float, crossinline block: DrawScope.() -> Unit) =
    withTransform({ scale(sx, sy, Offset(size.width / 2, size.height)) }) { block() }

private fun DrawScope.drawMote(center: Offset, r: Float, color: Color) {
    fun blob(dx: Float, dy: Float, rr: Float) = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + dx * r, center.y + dy * r), rr * r))
    }
    val cloud = listOf(blob(-0.55f, 0.15f, 0.7f), blob(0.5f, 0.2f, 0.72f), blob(0f, -0.2f, 0.9f))
        .reduce { acc, p -> Path.combine(PathOperation.Union, acc, p) }
    drawPath(cloud, color)
}

@Preview(widthDp = 420, heightDp = 200)
@Composable
private fun StudentFigurePreview() {
    ChalkroomTheme {
        androidx.compose.foundation.layout.Row {
            StudentFigure(StudentId.MIRA, Expression.IDLE, Modifier.width(130.dp), motes = listOf(Mote("a"), Mote("b"), Mote("c")))
            StudentFigure(StudentId.PIP, Expression.PUZZLED, Modifier.width(130.dp))
            StudentFigure(StudentId.JUNO, Expression.GOT_IT, Modifier.width(130.dp))
        }
    }
}

/** Modifier for a small nod: [progress] 0 → 1 → 0 tips the figure forward from its base. */
/** The per-student unconvinced pose, [progress] 0 → 1 → 0: Mira tilts, Pip squints lopsided, Juno narrows. Never a shake. */
fun Modifier.unconvinced(studentId: StudentId, progress: Float): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0.5f, 1f)
    when (studentId) {
        StudentId.MIRA -> rotationZ = -7f * progress
        StudentId.PIP -> { scaleX = 1f + 0.05f * progress; scaleY = 1f - 0.04f * progress; rotationZ = 3f * progress }
        StudentId.JUNO -> { scaleX = 1f - 0.03f * progress; translationY = 4f * progress * density }
    }
}

fun Modifier.nod(progress: Float): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0.5f, 1f)
    translationY = progress * 6f * density
    scaleY = 1f - progress * 0.03f
}
