package app.chalkroom.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.chalkroom.ui.theme.Chalkroom
import kotlin.math.sin
import kotlin.random.Random

/** Faint erased arcs on a slate whose top-left is [tl]: an old lesson wiped off. */
fun DrawScope.drawErasedArcs(tl: Offset, slate: Size, color: Color) {
    val dp = density
    clipRect(tl.x, tl.y, tl.x + slate.width, tl.y + slate.height) {
        drawArc(color, 200f, 110f, false, Offset(tl.x + slate.width * 0.08f, tl.y + slate.height * 0.2f), Size(slate.width * 0.5f, slate.height * 0.9f), style = Stroke(14f * dp, cap = StrokeCap.Round))
        drawArc(color, 160f, 90f, false, Offset(tl.x + slate.width * 0.45f, tl.y + slate.height * 0.35f), Size(slate.width * 0.45f, slate.height * 0.7f), style = Stroke(10f * dp, cap = StrokeCap.Round))
        drawArc(color, 230f, 70f, false, Offset(tl.x + slate.width * 0.3f, tl.y - slate.height * 0.1f), Size(slate.width * 0.4f, slate.height * 0.6f), style = Stroke(8f * dp, cap = StrokeCap.Round))
    }
}

/**
 * What you taught, one chalk line per cleared belief, stacked a little unevenly. Lines that weren't
 * there when this first showed are written on, glyph by glyph (instant when animations are removed);
 * the newest gets a short underline in [accent]. The chalk size is picked once so [fitLines] lines of
 * eight words fit the space, so written lines never change size as more arrive.
 */
@Composable
fun ChalkLines(
    lines: List<String>,
    accent: Color,
    fitLines: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = Chalkroom.type.chalkHand,
    spacing: Dp = 6.dp,
) {
    val reduced = Chalkroom.reducedMotion
    val alreadyThere = remember { lines.size }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val fitted = remember(constraints.maxWidth, constraints.maxHeight, fitLines, style) {
            val width = constraints.maxWidth - with(density) { MAX_INDENT.roundToPx() }
            val room = constraints.maxHeight - with(density) { (UNDERLINE_H + spacing * (fitLines - 1)).roundToPx() }
            val base = style.fontSize.value
            val size = CHALK_STEPS.map { base * it }.firstOrNull { sp ->
                val h = measurer.measure(REFERENCE_LINE, style.copy(fontSize = sp.sp, lineHeight = (sp * LINE_HEIGHT).sp), constraints = Constraints(maxWidth = width.coerceAtLeast(1))).size.height
                !constraints.hasBoundedHeight || h * fitLines <= room
            } ?: (base * CHALK_STEPS.last())
            style.copy(fontSize = size.sp, lineHeight = (size * LINE_HEIGHT).sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            lines.forEachIndexed { i, line ->
                val wobble = LINE_WOBBLE[i % LINE_WOBBLE.size]
                Column(
                    Modifier
                        .padding(start = wobble.indent)
                        .graphicsLayer { rotationZ = wobble.tilt },
                ) {
                    var written by remember(line) { mutableStateOf(reduced || i < alreadyThere) }
                    ChalkWriting(line, fitted, animate = !written, onWritten = { written = true })
                    if (i == lines.lastIndex) Underline(accent, animate = !reduced && i >= alreadyThere, shown = written)
                }
            }
        }
    }
}

/** An eight-word line to size the chalk by. */
private const val REFERENCE_LINE = "splitting water inside the leaf using light energy"
private val CHALK_STEPS = listOf(1f, 0.92f, 0.85f, 0.78f, 0.72f, 0.66f, 0.6f, 0.55f, 0.5f)
private const val LINE_HEIGHT = 1.1f
private val MAX_INDENT = 14.dp
private val UNDERLINE_H = 8.dp

private data class Wobble(val tilt: Float, val indent: Dp)

/** Fixed per row, so a line never shifts once written. */
private val LINE_WOBBLE = listOf(Wobble(-1.4f, 0.dp), Wobble(0.9f, 10.dp), Wobble(-0.6f, 4.dp), Wobble(1.3f, 14.dp))

/**
 * One line of chalk written on by an invisible hand: the glyphs appear in reading order, each wiped
 * on left to right, with a chalk speck at the tip; spaces go by quicker. Grain specks in the slate
 * colour knock a few holes in the strokes so it reads as chalk.
 */
@Composable
fun ChalkWriting(text: String, style: TextStyle, animate: Boolean, modifier: Modifier = Modifier, onWritten: () -> Unit = {}) {
    val colors = Chalkroom.colors
    val progress = remember(text) { Animatable(if (animate) 0f else 1f) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val grainCache = remember(text, style) { arrayOfNulls<List<Pair<Offset, Float>>>(1) }
    // Smaller chalk gets finer, sparser grain, so it doesn't eat the letters.
    val grainScale = (style.fontSize.value / Chalkroom.type.chalkHand.fontSize.value).coerceIn(0.4f, 1f)
    LaunchedEffect(text, animate) {
        if (animate) {
            progress.animateTo(1f, tween(writeMs(text), easing = LinearEasing))
            onWritten()
        }
    }
    Text(
        text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier
            .semantics { contentDescription = text }
            .drawWithContent {
                val l = layout ?: return@drawWithContent
                val p = progress.value
                val tip = if (p >= 1f) null else tipAt(l, text, p)
                val reveal = if (tip == null) null else revealPath(l, tip, 4f * density)
                val grain = grainCache[0] ?: grainFor(text, size, density, grainScale).also { grainCache[0] = it }
                if (reveal == null) {
                    drawContent()
                    grain.forEach { (o, r) -> drawCircle(colors.board, r, o) }
                } else {
                    clipPath(reveal) {
                        this@drawWithContent.drawContent()
                        grain.forEach { (o, r) -> drawCircle(colors.board, r, o) }
                    }
                }
                tip?.let { t ->
                    // The chalk speck, with a tiny jitter like a hand.
                    val jitter = sin(p * 90f) * 1.2f * density
                    drawCircle(colors.card.copy(alpha = 0.9f), 2.6f * density, Offset(t.x, t.rowMid + jitter))
                }
            },
    )
}

/** About 1–1.5 s a line, a little longer for longer lines. */
fun writeMs(text: String): Int = (850 + 14 * text.length).coerceIn(1_000, 1_500)

private class Tip(val row: Int, val x: Float, val rowMid: Float)

/** Where the hand is at [p]: glyph by glyph, a space worth less than a letter. */
private fun tipAt(l: TextLayoutResult, text: String, p: Float): Tip? {
    if (text.isEmpty()) return null
    val weights = text.map { if (it.isWhitespace()) 0.35f else 1f }
    var target = weights.sum() * p
    for (i in text.indices) {
        val w = weights[i]
        if (target <= w || i == text.lastIndex) {
            val box: Rect = l.getBoundingBox(i)
            val frac = (target / w).coerceIn(0f, 1f)
            val row = l.getLineForOffset(i)
            return Tip(row, box.left + box.width * frac, (l.getLineTop(row) + l.getLineBottom(row)) / 2f)
        }
        target -= w
    }
    return null
}

/** Rows before the tip's row are fully shown; the tip's row up to the tip. [pad] covers ascenders and swashes. */
private fun revealPath(l: TextLayoutResult, tip: Tip, pad: Float): Path = Path().apply {
    for (row in 0 until tip.row) {
        addRect(Rect(-pad, l.getLineTop(row) - pad, l.size.width + pad, l.getLineBottom(row) + pad))
    }
    addRect(Rect(-pad, l.getLineTop(tip.row) - pad, tip.x, l.getLineBottom(tip.row) + pad))
}

/** The same specks for the same line every frame. */
private fun grainFor(text: String, size: Size, density: Float, scale: Float): List<Pair<Offset, Float>> {
    val rnd = Random(text.hashCode())
    val count = (size.width * size.height * scale / (density * density * 55f)).toInt().coerceAtMost(400)
    return List(count) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) to (0.5f + rnd.nextFloat() * 0.6f) * density * scale }
}

/** A short wobbly stroke under the newest line, drawn on once the line is written. */
@Composable
private fun Underline(color: Color, animate: Boolean, shown: Boolean) {
    val draw = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(shown) { if (shown && animate) draw.animateTo(1f, tween(320, easing = EaseOutCubic)) }
    Canvas(Modifier.padding(start = 2.dp, top = 1.dp).width(58.dp).height(7.dp)) {
        if (!shown) return@Canvas
        val path = Path().apply {
            moveTo(0f, size.height * 0.65f)
            cubicTo(size.width * 0.3f, size.height * 0.15f, size.width * 0.65f, size.height * 0.95f, size.width, size.height * 0.35f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * draw.value, part, true)
        drawPath(part, color, style = Stroke(3.2f * density, cap = StrokeCap.Round))
    }
}
