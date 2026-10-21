package app.chalkroom.feature.classroom

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.chalkroom.ui.theme.Chalkroom
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A big hand-drawn chalk headline in the middle of the slate: Caveat, the whole line tilted a
 * little, each letter on a slightly wobbly baseline with its own small lean, chalk grain showing
 * the slate through, and a loose stroke under it. Sized to fit the slate. Never animated.
 */
@Composable
fun ChalkHeadline(text: String, modifier: Modifier = Modifier) {
    val colors = Chalkroom.colors
    val measurer = rememberTextMeasurer()
    val base = Chalkroom.type.chalkHand.copy(color = colors.chalk.copy(alpha = 0.94f))
    Spacer(
        modifier
            .semantics { contentDescription = text }
            .drawWithCache {
                val dp = density
                fun style(px: Float) = base.copy(fontSize = (px / fontScale / dp).sp, lineHeight = (px * 1.1f / fontScale / dp).sp)
                var px = 50f * dp
                var line = measurer.measure(text, style(px))
                val fit = minOf(1f, size.width * 0.8f / line.size.width, size.height * 0.48f / line.size.height)
                if (fit < 1f) {
                    px *= fit
                    line = measurer.measure(text, style(px))
                }
                val origin = Offset((size.width - line.size.width) / 2f, (size.height - line.size.height) / 2f - size.height * 0.05f)
                val glyphs = text.indices.filter { !text[it].isWhitespace() }.map { i ->
                    val g = measurer.measure(text[i].toString(), style(px))
                    // A slow wave along the baseline, a touch of per-letter jitter, and a small lean each.
                    val dy = (sin(i * 0.9f + 0.4f) * 0.045f + sin(i * 2.7f) * 0.02f) * line.size.height
                    Triple(g, origin + Offset(line.getBoundingBox(i).left, dy), sin(i * 2.3f + 1f) * 4f)
                }
                val rnd = Random(text.hashCode())
                val count = (line.size.width * line.size.height / (dp * dp * 30f)).toInt().coerceAtMost(700)
                val grain = List(count) {
                    Offset(origin.x + rnd.nextFloat() * line.size.width, origin.y + rnd.nextFloat() * line.size.height) to (0.5f + rnd.nextFloat() * 0.8f) * dp
                }
                val under = Path().apply {
                    val y = origin.y + line.size.height * 0.98f
                    val l = origin.x + line.size.width * 0.12f
                    val r = origin.x + line.size.width * 0.9f
                    moveTo(l, y)
                    cubicTo(l + (r - l) * 0.3f, y - 5f * dp, l + (r - l) * 0.62f, y + 4f * dp, r, y - 3f * dp)
                }
                onDrawBehind {
                    rotate(-3f, Offset(size.width / 2f, size.height / 2f)) {
                        glyphs.forEach { (g, at, lean) ->
                            rotate(lean, at + Offset(g.size.width / 2f, g.size.height * 0.7f)) { drawText(g, topLeft = at) }
                        }
                        drawPath(under, colors.chalk.copy(alpha = 0.55f), style = Stroke(2.5f * dp, cap = StrokeCap.Round))
                        grain.forEach { (o, r) -> drawCircle(colors.board, r, o) }
                    }
                }
            },
    )
}

/**
 * The slate's corners, always there under everything else: tiny chalk stars, loose scribbles and
 * half-finished maths ("2x + 3 =", a triangle with one side marked, a √ with nothing under it).
 * Faint and small, so they never compete with what's written in the middle. With [leftBusy]
 * (left-aligned lines run into the left corners) only the right corners are drawn.
 */
@Composable
fun BoardDoodles(modifier: Modifier = Modifier, leftBusy: Boolean = false) {
    val colors = Chalkroom.colors
    val measurer = rememberTextMeasurer()
    val chalk = colors.chalk.copy(alpha = DOODLE_ALPHA)
    val textStyle = Chalkroom.type.chalkHand.copy(color = chalk, fontSize = 13.sp, lineHeight = 14.sp)
    Spacer(
        modifier.drawWithCache {
            val sum = measurer.measure("2x + 3 =", textStyle)
            onDrawBehind {
                val dp = density
                val w = size.width
                val h = size.height
                val line = Stroke(1.4f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val m = 7f * dp
                // Top left: the unfinished sum, a scribble and a star under it.
                if (!leftBusy) rotate(-4f, Offset(m, m)) { drawText(sum, topLeft = Offset(m, m - 2f * dp)) }
                if (!leftBusy) {
                    drawStar(Offset(m + sum.size.width * 0.75f, m + sum.size.height + 12f * dp), 3.5f * dp, chalk, 0.6f)
                    drawScribble(Offset(m + 2f * dp, m + sum.size.height + 4f * dp), 26f * dp, 3, chalk, line, seed = 1)
                }
                // Top right: a small triangle, one side marked with a tick, and two stars.
                drawTriangle(Offset(w - m - 30f * dp, m + 2f * dp), 26f * dp, chalk, line)
                drawStar(Offset(w - m - 44f * dp, m + 22f * dp), 3f * dp, chalk, 0.2f)
                drawStar(Offset(w - m - 3f * dp, m + 30f * dp), 2.5f * dp, chalk, 1.1f)
                // Bottom left: a root sign with nothing under it, and a loose scribble.
                if (!leftBusy) {
                    drawRoot(Offset(m + 2f * dp, h - m - 14f * dp), 30f * dp, chalk, line)
                    drawStar(Offset(m + 44f * dp, h - m - 20f * dp), 3f * dp, chalk, 0.9f)
                }
                // Bottom right: scribbles and a star.
                drawScribble(Offset(w - m - 46f * dp, h - m - 16f * dp), 34f * dp, 4, chalk, line, seed = 2)
                drawScribble(Offset(w - m - 30f * dp, h - m - 6f * dp), 22f * dp, 3, chalk, line, seed = 3)
                drawStar(Offset(w - m - 54f * dp, h - m - 6f * dp), 3f * dp, chalk, 0.4f)
            }
        },
    )
}

private const val DOODLE_ALPHA = 0.24f

/** A tiny five-point chalk star, drawn as five strokes from the middle, turned by [turn] radians. */
private fun DrawScope.drawStar(c: Offset, r: Float, color: Color, turn: Float) {
    repeat(5) { i ->
        val a = turn + i * 2f * PI.toFloat() / 5f - PI.toFloat() / 2f
        drawLine(color, c, Offset(c.x + cos(a) * r, c.y + sin(a) * r), 1.3f * density, StrokeCap.Round)
    }
}

/** A loose, uneven zig-zag scribble [len] long with [loops] humps. */
private fun DrawScope.drawScribble(start: Offset, len: Float, loops: Int, color: Color, stroke: Stroke, seed: Int) {
    val rnd = Random(seed)
    val p = Path().apply {
        moveTo(start.x, start.y)
        val step = len / loops
        repeat(loops) { i ->
            val x0 = start.x + step * i
            val up = (3f + rnd.nextFloat() * 3f) * density
            val drift = (rnd.nextFloat() - 0.5f) * 2f * density
            cubicTo(x0 + step * 0.3f, start.y - up, x0 + step * 0.6f, start.y + up * 0.7f, x0 + step, start.y + drift)
        }
    }
    drawPath(p, color, style = stroke)
}

/** A small triangle with a tick on its left side, the corner lines overshooting a little like a hand. */
private fun DrawScope.drawTriangle(tl: Offset, s: Float, color: Color, stroke: Stroke) {
    val a = Offset(tl.x + s * 0.45f, tl.y)
    val b = Offset(tl.x + s, tl.y + s * 0.85f)
    val c = Offset(tl.x - s * 0.05f, tl.y + s * 0.8f)
    drawPath(
        Path().apply {
            moveTo(a.x - s * 0.02f, a.y - s * 0.04f)
            lineTo(b.x + s * 0.04f, b.y + s * 0.02f)
            lineTo(c.x, c.y)
            lineTo(a.x + s * 0.03f, a.y + s * 0.02f)
        },
        color,
        style = stroke,
    )
    // One side marked.
    val mid = Offset((a.x + c.x) / 2f, (a.y + c.y) / 2f)
    drawLine(color, mid + Offset(-s * 0.08f, -s * 0.04f), mid + Offset(s * 0.08f, s * 0.05f), stroke.width, StrokeCap.Round)
}

/** A root sign with its bar running on over nothing. */
private fun DrawScope.drawRoot(start: Offset, len: Float, color: Color, stroke: Stroke) {
    val p = Path().apply {
        moveTo(start.x, start.y + len * 0.2f)
        lineTo(start.x + len * 0.12f, start.y + len * 0.12f)
        lineTo(start.x + len * 0.25f, start.y + len * 0.42f)
        lineTo(start.x + len * 0.42f, start.y - len * 0.22f)
        lineTo(start.x + len * 1.05f, start.y - len * 0.24f)
    }
    drawPath(p, color, style = stroke)
}
