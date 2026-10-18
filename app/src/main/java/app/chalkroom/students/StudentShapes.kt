package app.chalkroom.students

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import app.chalkroom.shared.StudentId
import app.chalkroom.ui.theme.ChalkroomColors
import kotlin.math.hypot

/** Figure canvas aspect: height = width × this. The bust sits low enough to leave room for motes above. */
const val FIGURE_ASPECT = 1.2f

/** The canvas bottom, in figure-width units. Everything below it is cropped. */
private const val BOTTOM = FIGURE_ASPECT

/**
 * Where a student's face parts and motes sit. Every value is in figure-width units (the canvas is
 * 1 wide and [FIGURE_ASPECT] tall), so shapes keep their proportions. [eyeTilt] is in degrees and
 * mirrored per eye; [eyeSlope] lifts the right eye. [oval] eyes are ellipses, the rest capsules.
 * Motes sit on an arc around [moteCenter] that clears the head: [moteRx] across, up to [moteTop],
 * at most [moteSpan] degrees wide.
 */
data class FaceLayout(
    val center: Offset,
    val eyeGap: Float,
    val eyeW: Float,
    val eyeH: Float,
    val eyeTilt: Float = 0f,
    val eyeSlope: Float = 0f,
    val oval: Boolean = false,
    val moteCenter: Offset,
    val moteRx: Float,
    val moteTop: Float,
    val moteSpan: Float,
)

/** The three busts from devpost/references/students, redrawn as paths: flat colour, thick ink outline, no mouths. */
object StudentShapes {

    fun face(id: StudentId): FaceLayout = when (id) {
        StudentId.MIRA -> FaceLayout(
            center = Offset(0.535f, 0.86f), eyeGap = 0.24f, eyeW = 0.115f, eyeH = 0.22f, eyeSlope = -0.01f, oval = true,
            moteCenter = Offset(0.53f, 0.75f), moteRx = 0.45f, moteTop = 0.10f, moteSpan = 130f,
        )
        StudentId.PIP -> FaceLayout(
            center = Offset(0.58f, 0.89f), eyeGap = 0.225f, eyeW = 0.055f, eyeH = 0.12f, eyeTilt = 8f, eyeSlope = -0.025f,
            moteCenter = Offset(0.50f, 0.85f), moteRx = 0.48f, moteTop = 0.12f, moteSpan = 120f,
        )
        StudentId.JUNO -> FaceLayout(
            center = Offset(0.50f, 0.905f), eyeGap = 0.15f, eyeW = 0.058f, eyeH = 0.112f,
            moteCenter = Offset(0.50f, 0.84f), moteRx = 0.38f, moteTop = 0.13f, moteSpan = 110f,
        )
    }

    /** Mira: a cat-eared blob, the right ear taller. */
    private fun mira(w: Float) = Path().apply {
        fun c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = cubicTo(x1 * w, y1 * w, x2 * w, y2 * w, x3 * w, y3 * w)
        moveTo(0.15f * w, (BOTTOM + 0.12f) * w)
        c(0.10f, 1.16f, 0.10f, 0.94f, 0.16f, 0.80f)
        c(0.20f, 0.69f, 0.25f, 0.58f, 0.29f, 0.52f)
        c(0.31f, 0.485f, 0.34f, 0.48f, 0.355f, 0.51f)
        c(0.37f, 0.545f, 0.38f, 0.578f, 0.41f, 0.576f)
        c(0.47f, 0.566f, 0.56f, 0.548f, 0.59f, 0.52f)
        c(0.62f, 0.48f, 0.645f, 0.425f, 0.67f, 0.41f)
        c(0.69f, 0.395f, 0.715f, 0.40f, 0.735f, 0.43f)
        c(0.82f, 0.55f, 0.92f, 0.80f, 0.91f, 0.98f)
        c(0.905f, 1.10f, 0.88f, 1.22f, 0.84f, BOTTOM + 0.12f)
        close()
    }

    /** Pip: a lightning bolt, tip up and to the right, one point left and one right. */
    private fun pip(w: Float) = roundedPolygon(
        w,
        Corner(0.76f, 0.34f, 0.07f), // top tip
        Corner(0.70f, 0.74f, 0.025f), // notch
        Corner(0.97f, 0.64f, 0.06f), // right point
        Corner(0.74f, BOTTOM + 0.1f, 0f),
        Corner(0.10f, BOTTOM + 0.1f, 0f),
        Corner(0.30f, 0.91f, 0.025f), // inner corner
        Corner(0.05f, 0.90f, 0.06f), // left point
    )

    // Juno, in pieces: coat behind, cream face, collar flaps over the face, hat on top.
    private fun junoCoat(w: Float) = roundedPolygon(
        w,
        Corner(0.06f, BOTTOM + 0.1f, 0f),
        Corner(0.10f, 1.00f, 0.06f),
        Corner(0.90f, 0.98f, 0.06f),
        Corner(0.94f, BOTTOM + 0.1f, 0f),
    )

    private fun junoFace(w: Float) = roundedPolygon(
        w,
        Corner(0.40f, 0.56f, 0.06f),
        Corner(0.60f, 0.56f, 0.06f),
        Corner(0.81f, 1.06f, 0.07f),
        Corner(0.19f, 1.08f, 0.07f),
    )

    private fun junoCollar(w: Float, left: Boolean): Path {
        fun x(v: Float) = if (left) v else 1f - v
        return roundedPolygon(
            w,
            Corner(x(0.06f), BOTTOM + 0.1f, 0f),
            Corner(x(0.10f), if (left) 0.99f else 0.97f, 0.04f),
            Corner(x(0.20f), if (left) 0.86f else 0.84f, 0.035f),
            Corner(x(0.50f), 1.15f, 0.02f),
            Corner(x(0.50f), BOTTOM + 0.1f, 0f),
        )
    }

    private val JUNO_BRIM = Offset(0.49f, 0.715f)
    private const val JUNO_BRIM_TILT = -15f

    /** Body, everything behind the eyes. */
    fun DrawScope.drawBust(id: StudentId, color: Color, colors: ChalkroomColors, outline: Stroke) {
        val w = size.width
        fun fillAndLine(path: Path, fill: Color) {
            drawPath(path, fill)
            drawPath(path, colors.ink, style = outline)
        }
        when (id) {
            StudentId.MIRA -> fillAndLine(mira(w), color)
            StudentId.PIP -> fillAndLine(pip(w), color)
            StudentId.JUNO -> {
                fillAndLine(junoCoat(w), color)
                val face = junoFace(w)
                drawPath(face, colors.junoFace)
                // The brim's shadow across the top of the face.
                clipPath(face) {
                    withTransform({ rotate(JUNO_BRIM_TILT, JUNO_BRIM * w) }) {
                        drawOval(colors.junoShadow, topLeft = Offset((JUNO_BRIM.x - 0.12f) * w, (JUNO_BRIM.y - 0.02f) * w), size = Size(0.60f * w, 0.135f * w))
                    }
                }
                drawPath(face, colors.ink, style = outline)
                fillAndLine(junoCollar(w, left = true), color)
                fillAndLine(junoCollar(w, left = false), color)
            }
        }
    }

    /** Anything in front of the eyes: Juno's hat, so eyes glancing up slip under the brim. */
    fun DrawScope.drawFront(id: StudentId, color: Color, colors: ChalkroomColors, outline: Stroke) {
        if (id != StudentId.JUNO) return
        val w = size.width
        val b = JUNO_BRIM * w
        withTransform({ rotate(JUNO_BRIM_TILT, b) }) {
            val knob = Offset(b.x, b.y - 0.27f * w)
            drawCircle(color, 0.06f * w, knob)
            drawCircle(colors.ink, 0.06f * w, knob, style = outline)
            val dome = Path().apply {
                moveTo(b.x - 0.25f * w, b.y)
                cubicTo(b.x - 0.25f * w, b.y - 0.31f * w, b.x + 0.25f * w, b.y - 0.31f * w, b.x + 0.25f * w, b.y)
                close()
            }
            drawPath(dome, color)
            drawPath(dome, colors.ink, style = outline)
            val brim = Rect(b.x - 0.45f * w, b.y - 0.08f * w, b.x + 0.45f * w, b.y + 0.08f * w)
            drawOval(color, brim.topLeft, brim.size)
            drawOval(colors.ink, brim.topLeft, brim.size, style = outline)
            // The dome's base line, seen across the brim.
            drawArc(colors.ink, 0f, 180f, false, Offset(b.x - 0.25f * w, b.y - 0.035f * w), Size(0.5f * w, 0.07f * w), style = Stroke(outline.width * 0.7f, cap = outline.cap))
        }
    }

    /** A corner at ([x], [y]) in width units, rounded by [r]. */
    private data class Corner(val x: Float, val y: Float, val r: Float)

    private fun roundedPolygon(w: Float, vararg pts: Corner): Path = Path().apply {
        val n = pts.size
        pts.forEachIndexed { i, p ->
            val prev = pts[(i - 1 + n) % n]
            val next = pts[(i + 1) % n]
            val at = Offset(p.x * w, p.y * w)
            if (p.r == 0f) {
                if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
                return@forEachIndexed
            }
            fun toward(o: Corner): Offset {
                val d = Offset(o.x * w, o.y * w) - at
                val len = hypot(d.x, d.y)
                val r = minOf(p.r * w, len / 2)
                return at + d * (r / len)
            }
            val a = toward(prev)
            val b = toward(next)
            if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
            quadraticTo(at.x, at.y, b.x, b.y)
        }
        close()
    }
}
