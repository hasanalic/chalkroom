package app.chalkroom.students

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chalkroom.shared.StudentId
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomColors

private const val FIGURE_SHARE = 0.80f // figure width / seat width
private const val DESK_ASPECT = 0.50f // desk height / seat width
private const val SINK = 0.12f // how far (× seat width) the figure's base sits below the desk top's back edge

/** Each desk sits a few degrees off true, fixed per student. */
private fun StudentId.deskTilt(): Float = when (this) {
    StudentId.MIRA -> -2.5f
    StudentId.PIP -> 1.8f
    StudentId.JUNO -> 3f
}

/**
 * A student behind their desk, seen slightly from above: chair back behind them, gold wood with
 * grain, a deeper front panel with a nameplate, legs and a flat shadow, and their own prop on the
 * left third. An open notebook in the middle marks a paused lesson. [propMotion] 0 → 1 → 0 plays a
 * small prop action (Mira's doodle grows, Pip fidgets with the notebook, Juno's plant sways);
 * [doodleStrokes] is how much Mira has added to her doodle.
 */
@Composable
fun StudentAtDesk(
    studentId: StudentId,
    expression: Expression,
    modifier: Modifier = Modifier,
    motes: List<Mote> = emptyList(),
    look: Look = Look.AT_YOU,
    notebookOpen: Boolean = false,
    showName: Boolean = true,
    lookAt: Offset? = null,
    hopTick: Int = 0,
    nodTick: Int = 0,
    propMotion: Float = 0f,
    doodleStrokes: Int = 0,
    asleep: Boolean = false,
    noticeTick: Int = 0,
    callTick: Int = 0,
    startle: Boolean = false,
    headX: Float? = null,
    slowNodTick: Int = 0,
    proud: Boolean = false,
) {
    val profile = StudentCatalog[studentId]
    BoxWithConstraints(modifier) {
        val width: Dp = maxWidth
        val figureHeight = width * FIGURE_SHARE * FIGURE_ASPECT
        val deskTop = figureHeight - width * SINK
        val deskHeight = width * DESK_ASPECT
        Box(Modifier.fillMaxWidth().height(deskTop + deskHeight)) {
            Chair(Modifier.fillMaxWidth().height(deskTop + width * 0.1f), figureHeight)
            StudentFigure(
                studentId = studentId,
                expression = expression,
                motes = motes,
                look = look,
                lookAt = lookAt,
                hopTick = hopTick,
                nodTick = nodTick,
                asleep = asleep,
                noticeTick = noticeTick,
                callTick = callTick,
                startle = startle,
                headX = headX,
                slowNodTick = slowNodTick,
                proud = proud,
                modifier = Modifier.fillMaxWidth(FIGURE_SHARE).align(Alignment.TopCenter),
            )
            Box(
                Modifier
                    .offset(y = deskTop)
                    .fillMaxWidth()
                    .height(deskHeight)
                    .graphicsLayer { rotationZ = studentId.deskTilt() },
            ) {
                Desk(studentId, notebookOpen, propMotion, doodleStrokes, Modifier.fillMaxWidth().height(deskHeight))
                if (showName) {
                    Text(
                        profile.name,
                        style = Chalkroom.type.small.copy(color = Chalkroom.colors.ink, fontWeight = Chalkroom.type.label.fontWeight, fontSize = 12.sp, lineHeight = 14.sp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = deskHeight * 0.50f)
                            .background(Chalkroom.colors.card, RoundedCornerShape(5.dp))
                            .border(1.5.dp, Chalkroom.colors.ink, RoundedCornerShape(5.dp))
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** The back of a slate chair, peeking out behind the student. */
@Composable
private fun Chair(modifier: Modifier, figureHeight: Dp) {
    val colors = Chalkroom.colors
    Canvas(modifier) {
        val w = size.width
        val top = figureHeight.toPx() * 0.48f
        val ink = Stroke(0.02f * w, join = StrokeJoin.Round)
        val tl = Offset(0.17f * w, top)
        val s = Size(0.66f * w, size.height - top)
        drawRoundRect(colors.chair, tl, s, CornerRadius(0.08f * w))
        drawRoundRect(colors.ink, tl, s, CornerRadius(0.08f * w), style = ink)
        drawLine(colors.ink.copy(alpha = 0.35f), Offset(0.24f * w, top + 0.06f * w), Offset(0.76f * w, top + 0.06f * w), 0.015f * w, StrokeCap.Round)
    }
}

@Composable
fun Desk(studentId: StudentId, notebookOpen: Boolean, propMotion: Float, doodleStrokes: Int, modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    val profile = StudentCatalog[studentId]
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ink = Stroke(0.018f * w, join = StrokeJoin.Round)
        val backY = 0.03f * h
        val frontY = 0.30f * h
        val lipY = 0.38f * h
        val panelBottom = 0.84f * h
        val legBottom = 0.96f * h

        // Flat shadow, legs, front panel, front lip, then the top seen slightly from above.
        drawOval(colors.ink.copy(alpha = 0.12f), Offset(0.02f * w, legBottom - 0.06f * h), Size(0.96f * w, 0.12f * h))
        listOf(0.09f, 0.87f).forEach { x ->
            val tl = Offset(x * w, panelBottom - 0.02f * h)
            drawRect(colors.woodDeep, tl, Size(0.04f * w, legBottom - tl.y))
            drawRect(colors.ink, tl, Size(0.04f * w, legBottom - tl.y), style = ink)
        }
        drawRoundRect(colors.woodDeep, Offset(0.04f * w, lipY), Size(0.92f * w, panelBottom - lipY), CornerRadius(0.02f * w))
        drawRoundRect(colors.ink, Offset(0.04f * w, lipY), Size(0.92f * w, panelBottom - lipY), CornerRadius(0.02f * w), style = ink)
        drawRoundRect(colors.woodDark, Offset(0f, frontY), Size(w, lipY - frontY), CornerRadius(0.015f * w))
        drawRoundRect(colors.ink, Offset(0f, frontY), Size(w, lipY - frontY), CornerRadius(0.015f * w), style = ink)
        val top = Path().apply {
            moveTo(0.06f * w, backY)
            lineTo(0.94f * w, backY)
            lineTo(w, frontY)
            lineTo(0f, frontY)
            close()
        }
        drawPath(top, colors.wood)
        drawPath(top, colors.ink, style = ink)
        // A couple of grain lines.
        val grain = colors.woodDeep.copy(alpha = 0.35f)
        drawPath(
            Path().apply {
                moveTo(0.38f * w, 0.11f * h)
                cubicTo(0.5f * w, 0.08f * h, 0.62f * w, 0.15f * h, 0.84f * w, 0.12f * h)
            },
            grain, style = Stroke(0.01f * w, cap = StrokeCap.Round),
        )
        drawPath(
            Path().apply {
                moveTo(0.14f * w, 0.23f * h)
                cubicTo(0.3f * w, 0.20f * h, 0.42f * w, 0.26f * h, 0.6f * w, 0.23f * h)
            },
            grain, style = Stroke(0.01f * w, cap = StrokeCap.Round),
        )

        // The personal prop on the left third; the open notebook (paused lesson) in the middle.
        when (studentId) {
            StudentId.MIRA -> drawDoodle(colors, profile.color, Offset(0.1f * w, 0.04f * h), w, doodleStrokes, propMotion)
            StudentId.PIP -> drawAskewNotebook(colors, profile.color, profile.shade, Offset(0.2f * w, 0.17f * h), w, propMotion)
            StudentId.JUNO -> drawPlant(colors, Offset(0.2f * w, 0.18f * h), w, propMotion)
        }
        if (notebookOpen) drawOpenNotebook(colors, profile.color, Offset(0.58f * w, 0.17f * h), w)
    }
}

private fun DrawScope.drawDoodle(c: ChalkroomColors, color: Color, at: Offset, w: Float, strokes: Int, progress: Float) {
    rotate(-8f, at) {
        val s = Size(0.22f * w, 0.12f * w)
        drawRect(c.card, at, s)
        drawRect(c.ink, at, s, style = Stroke(0.01f * w))
        val base = Path().apply {
            moveTo(at.x + 0.03f * w, at.y + 0.08f * w)
            cubicTo(at.x + 0.06f * w, at.y + 0.01f * w, at.x + 0.09f * w, at.y + 0.11f * w, at.x + 0.14f * w, at.y + 0.04f * w)
        }
        drawPath(base, color, style = Stroke(0.014f * w, cap = StrokeCap.Round))
        // Each ambient doodle adds one loop; the newest one is drawn as it's added.
        repeat(strokes.coerceAtMost(4)) { i ->
            val p = if (i == strokes - 1) progress.coerceIn(0f, 1f).let { if (it == 0f) 1f else it } else 1f
            val cx = at.x + (0.05f + 0.045f * i) * w
            val cy = at.y + (0.03f + 0.02f * (i % 2)) * w
            drawArc(c.ink.copy(alpha = 0.7f), 200f, 300f * p, false, Offset(cx, cy), Size(0.03f * w, 0.03f * w), style = Stroke(0.008f * w, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawAskewNotebook(c: ChalkroomColors, color: Color, shade: Color, center: Offset, w: Float, fidget: Float) {
    rotate(14f + 10f * fidget, center) {
        val s = Size(0.2f * w, 0.12f * w)
        val tl = Offset(center.x - s.width / 2, center.y - s.height / 2)
        drawRoundRect(color, tl, s, CornerRadius(0.01f * w))
        drawRoundRect(c.ink, tl, s, CornerRadius(0.01f * w), style = Stroke(0.012f * w))
        drawLine(shade, Offset(tl.x + 0.03f * w, tl.y), Offset(tl.x + 0.03f * w, tl.y + s.height), 0.012f * w)
    }
}

private fun DrawScope.drawOpenNotebook(c: ChalkroomColors, color: Color, center: Offset, w: Float) {
    rotate(-4f, center) {
        val pw = 0.14f * w
        val ph = 0.09f * w
        // A bookmark in the student's colour, hanging out past the bottom by the spine.
        val ribbon = Path().apply {
            val x = center.x + pw * 0.12f
            moveTo(x, center.y - ph / 2)
            lineTo(x + 0.022f * w, center.y - ph / 2)
            lineTo(x + 0.022f * w, center.y + ph / 2 + 0.035f * w)
            lineTo(x + 0.011f * w, center.y + ph / 2 + 0.024f * w)
            lineTo(x, center.y + ph / 2 + 0.035f * w)
            close()
        }
        listOf(-1, 1).forEach { side ->
            val tl = Offset(center.x + if (side == -1) -pw else 0f, center.y - ph / 2)
            drawRect(c.card, tl, Size(pw, ph))
            drawRect(c.ink, tl, Size(pw, ph), style = Stroke(0.01f * w))
            repeat(3) { i ->
                val y = tl.y + ph * (0.3f + i * 0.22f)
                drawLine(c.ink.copy(alpha = 0.35f), Offset(tl.x + pw * 0.15f, y), Offset(tl.x + pw * 0.85f, y), 0.006f * w)
            }
        }
        drawPath(ribbon, color)
        drawPath(ribbon, c.ink, style = Stroke(0.008f * w, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawPlant(c: ChalkroomColors, base: Offset, w: Float, sway: Float) {
    val leaf = Color(0xFF6BAF5B)
    val stroke = Stroke(0.01f * w, join = StrokeJoin.Round)
    listOf(-35f, 0f, 35f).forEachIndexed { i, angle ->
        rotate(angle + sway * (if (i == 1) 6f else 9f), base) {
            val p = Path().apply {
                moveTo(base.x, base.y)
                quadraticTo(base.x - 0.05f * w, base.y - 0.09f * w, base.x, base.y - 0.16f * w)
                quadraticTo(base.x + 0.05f * w, base.y - 0.09f * w, base.x, base.y)
            }
            drawPath(p, leaf)
            drawPath(p, c.ink, style = stroke)
        }
    }
    val pot = Path().apply {
        moveTo(base.x - 0.06f * w, base.y - 0.01f * w)
        lineTo(base.x + 0.06f * w, base.y - 0.01f * w)
        lineTo(base.x + 0.045f * w, base.y + 0.08f * w)
        lineTo(base.x - 0.045f * w, base.y + 0.08f * w)
        close()
    }
    drawPath(pot, c.coral)
    drawPath(pot, c.ink, style = stroke)
}
