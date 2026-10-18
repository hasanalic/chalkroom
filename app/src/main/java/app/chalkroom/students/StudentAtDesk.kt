package app.chalkroom.students

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import app.chalkroom.shared.StudentId
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomColors

private const val DESK_ASPECT = 0.42f
private const val DESK_OVERLAP = 0.26f // share of the figure's height hidden behind the desk

/** A student's bust behind their wooden desk, with their prop. An open notebook marks a paused lesson. */
@Composable
fun StudentAtDesk(
    studentId: StudentId,
    expression: Expression,
    modifier: Modifier = Modifier,
    motes: List<Mote> = emptyList(),
    look: Look = Look.AT_YOU,
    notebookOpen: Boolean = false,
    figureModifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val width: Dp = maxWidth
        val figureHeight = width * 0.86f * FIGURE_ASPECT
        val deskHeight = width * DESK_ASPECT
        Box(Modifier.fillMaxWidth().height(figureHeight + deskHeight - figureHeight * DESK_OVERLAP)) {
            StudentFigure(
                studentId = studentId,
                expression = expression,
                motes = motes,
                look = look,
                modifier = figureModifier.fillMaxWidth(0.86f).align(Alignment.TopCenter),
            )
            Desk(
                prop = StudentCatalog[studentId].prop,
                notebookOpen = notebookOpen,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = figureHeight * (1f - DESK_OVERLAP))
                    .fillMaxWidth()
                    .height(deskHeight),
            )
        }
    }
}

@Composable
fun Desk(prop: DeskProp, notebookOpen: Boolean, modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ink = Stroke(0.022f * w, join = StrokeJoin.Round)
        val topH = 0.30f * h
        // Front panel, then the top slab overhanging it.
        drawRoundRect(colors.woodDark, Offset(0.06f * w, topH * 0.7f), Size(0.88f * w, h - topH * 0.7f), CornerRadius(0.03f * w))
        drawRoundRect(colors.ink, Offset(0.06f * w, topH * 0.7f), Size(0.88f * w, h - topH * 0.7f), CornerRadius(0.03f * w), style = ink)
        drawLine(colors.ink.copy(alpha = 0.25f), Offset(0.2f * w, 0.62f * h), Offset(0.8f * w, 0.62f * h), 0.012f * w, StrokeCap.Round)
        drawRoundRect(colors.wood, Offset(0f, 0f), Size(w, topH), CornerRadius(0.04f * w))
        drawRoundRect(colors.ink, Offset(0f, 0f), Size(w, topH), CornerRadius(0.04f * w), style = ink)
        // The prop sits on the slab, drawn slightly above it.
        when (prop) {
            DeskProp.DOODLE -> drawDoodle(colors, Offset(0.16f * w, -0.10f * h), w)
            DeskProp.NOTEBOOK -> if (!notebookOpen) drawClosedNotebook(colors, Offset(0.68f * w, -0.06f * h), w)
            DeskProp.PLANT -> drawPlant(colors, Offset(0.82f * w, 0.02f * h), w)
        }
        if (notebookOpen) drawOpenNotebook(colors, Offset(0.5f * w, 0.02f * h), w)
    }
}

private fun DrawScope.drawDoodle(c: ChalkroomColors, at: Offset, w: Float) {
    rotate(-8f, at) {
        drawRect(c.paper, at, Size(0.18f * w, 0.13f * w))
        drawRect(c.ink, at, Size(0.18f * w, 0.13f * w), style = Stroke(0.01f * w))
        val p = Path().apply {
            moveTo(at.x + 0.03f * w, at.y + 0.08f * w)
            cubicTo(at.x + 0.06f * w, at.y + 0.01f * w, at.x + 0.09f * w, at.y + 0.12f * w, at.x + 0.15f * w, at.y + 0.04f * w)
        }
        drawPath(p, c.mira, style = Stroke(0.014f * w, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawClosedNotebook(c: ChalkroomColors, at: Offset, w: Float) {
    rotate(14f, at) {
        drawRoundRect(c.pip, at, Size(0.2f * w, 0.12f * w), CornerRadius(0.01f * w))
        drawRoundRect(c.ink, at, Size(0.2f * w, 0.12f * w), CornerRadius(0.01f * w), style = Stroke(0.012f * w))
        drawLine(c.ink, Offset(at.x + 0.03f * w, at.y), Offset(at.x + 0.03f * w, at.y + 0.12f * w), 0.01f * w)
    }
}

private fun DrawScope.drawOpenNotebook(c: ChalkroomColors, center: Offset, w: Float) {
    val pw = 0.13f * w
    val ph = 0.09f * w
    listOf(-1, 1).forEach { side ->
        val tl = Offset(center.x + if (side == -1) -pw else 0f, center.y - ph / 2)
        drawRect(c.paper, tl, Size(pw, ph))
        drawRect(c.ink, tl, Size(pw, ph), style = Stroke(0.01f * w))
        repeat(3) { i ->
            val y = tl.y + ph * (0.3f + i * 0.22f)
            drawLine(c.ink.copy(alpha = 0.35f), Offset(tl.x + pw * 0.15f, y), Offset(tl.x + pw * 0.85f, y), 0.006f * w)
        }
    }
}

private fun DrawScope.drawPlant(c: ChalkroomColors, base: Offset, w: Float) {
    val leaf = c.pip.copy(red = 0.36f, green = 0.56f, blue = 0.36f)
    val stroke = Stroke(0.01f * w, join = StrokeJoin.Round)
    listOf(-35f, 0f, 35f).forEach { angle ->
        rotate(angle, base) {
            val p = Path().apply {
                moveTo(base.x, base.y)
                quadraticTo(base.x - 0.05f * w, base.y - 0.09f * w, base.x, base.y - 0.15f * w)
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
    drawPath(pot, c.paperDeep)
    drawPath(pot, c.ink, style = stroke)
}
