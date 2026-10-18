package app.chalkroom.students

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import app.chalkroom.shared.StudentId

/** Figure canvas aspect: height = width × this. The head sits low enough to leave room for motes above. */
const val FIGURE_ASPECT = 1.2f

/**
 * Where a student's face parts sit, in fractions of the canvas width (x) and height (y).
 * [eyeTilt] is in degrees: Pip's eyes sit tilted.
 */
data class FaceLayout(
    val center: Offset,
    val eyeGap: Float,
    val eyeTilt: Float,
    val moteCenterY: Float,
)

object StudentShapes {

    fun face(id: StudentId): FaceLayout = when (id) {
        StudentId.MIRA -> FaceLayout(Offset(0.55f, 0.52f), eyeGap = 0.15f, eyeTilt = 0f, moteCenterY = 0.17f)
        StudentId.PIP -> FaceLayout(Offset(0.50f, 0.63f), eyeGap = 0.17f, eyeTilt = 9f, moteCenterY = 0.26f)
        StudentId.JUNO -> FaceLayout(Offset(0.50f, 0.50f), eyeGap = 0.12f, eyeTilt = 0f, moteCenterY = 0.15f)
    }

    /** The silhouette, cut off flat at the bottom (the desk hides it). */
    fun body(id: StudentId, size: Size): Path {
        val w = size.width
        val h = size.height
        fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
            cubicTo(x1 * w, y1 * h, x2 * w, y2 * h, x3 * w, y3 * h)
        return Path().apply {
            when (id) {
                StudentId.MIRA -> {
                    // Soft bean leaning right.
                    moveTo(0.18f * w, h)
                    c(0.10f, 0.76f, 0.20f, 0.36f, 0.50f, 0.30f)
                    c(0.76f, 0.25f, 0.92f, 0.46f, 0.86f, 0.70f)
                    c(0.83f, 0.82f, 0.81f, 0.92f, 0.83f, 1f)
                }
                StudentId.PIP -> {
                    // Wide, low, lopsided: the left shoulder sits higher.
                    moveTo(0.05f * w, h)
                    c(0.01f, 0.72f, 0.08f, 0.45f, 0.30f, 0.43f)
                    c(0.56f, 0.40f, 0.86f, 0.47f, 0.93f, 0.63f)
                    c(0.98f, 0.76f, 0.96f, 0.90f, 0.95f, 1f)
                }
                StudentId.JUNO -> {
                    // Tall pear.
                    moveTo(0.12f * w, h)
                    c(0.08f, 0.78f, 0.30f, 0.66f, 0.31f, 0.42f)
                    c(0.32f, 0.21f, 0.68f, 0.21f, 0.69f, 0.42f)
                    c(0.70f, 0.66f, 0.92f, 0.78f, 0.88f, 1f)
                }
            }
            close()
        }
    }

    /** Hair-like bits drawn behind the body: Mira's single tuft, Pip's three points. Juno has none. */
    fun tufts(id: StudentId, size: Size): List<Path> {
        val w = size.width
        val h = size.height
        fun pt(x: Float, y: Float) = Offset(x * w, y * h)
        return when (id) {
            StudentId.MIRA -> listOf(
                Path().apply {
                    val a = pt(0.45f, 0.32f)
                    moveTo(a.x, a.y)
                    cubicTo(0.42f * w, 0.22f * h, 0.52f * w, 0.17f * h, 0.60f * w, 0.20f * h)
                    cubicTo(0.54f * w, 0.22f * h, 0.53f * w, 0.27f * h, 0.56f * w, 0.31f * h)
                    close()
                },
            )
            StudentId.PIP -> listOf(0.30f to -8f, 0.47f to 4f, 0.64f to 14f).map { (x, lean) ->
                Path().apply {
                    val baseY = 0.45f * h
                    val tip = pt(x + lean / 400f, 0.31f)
                    moveTo(x * w - 0.06f * w, baseY + 0.02f * h)
                    lineTo(tip.x, tip.y)
                    lineTo(x * w + 0.06f * w, baseY + 0.02f * h)
                    close()
                }
            }
            StudentId.JUNO -> emptyList()
        }
    }

    /** Juno's pale face area. */
    fun junoFace(size: Size): Pair<Offset, Float> = Offset(0.5f * size.width, 0.50f * size.height) to 0.165f * size.width

    /** Juno's star mark, above the face area. */
    fun junoStar(size: Size): Path {
        val c = Offset(0.5f * size.width, 0.335f * size.height)
        val outer = 0.04f * size.width
        val inner = outer * 0.45f
        return Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outer else inner
                val a = Math.toRadians((i * 36 - 90).toDouble())
                val x = c.x + r * kotlin.math.cos(a).toFloat()
                val y = c.y + r * kotlin.math.sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
}
