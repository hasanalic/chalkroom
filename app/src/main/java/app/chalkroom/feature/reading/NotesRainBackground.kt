package app.chalkroom.feature.reading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chalkroom.ui.theme.Chalkroom
import kotlin.math.ceil
import kotlin.random.Random

/** Three depths: small and faint, medium, large and bright. */
private val DEPTH_SIZES = floatArrayOf(10f, 14f, 19f)
private val DEPTH_ALPHA = floatArrayOf(0.26f, 0.45f, 0.68f)
private val DEPTH_WEIGHTS = floatArrayOf(0.42f, 0.38f, 0.20f)
/** Cell size per font size: one monospace glyph wide plus a little air, a touch taller than wide. */
private const val CELL_W = 0.84f
private const val CELL_H = 1.06f
/** The brightest the rain gets behind the student, and how far outside it that cap eases off. */
private const val FOCUS_ALPHA = 0.14f
private val FOCUS_FEATHER = 32.dp
/** Calm (count line, messages): a third of the speed, half the brightness. */
private const val CALM_SPEED = 1f / 3f
private const val CALM_DIM = 0.5f
private const val CALM_MS = 600

private class RainLayout(
    val x: FloatArray,
    val depth: IntArray,
    val cellW: FloatArray,
    val cellH: FloatArray,
    val model: NotesRain,
) {
    /** Each glyph measured once per depth; the colour is applied when drawn. */
    val glyphs = HashMap<Int, TextLayoutResult>()
}

/**
 * The digital rain behind the Reading screen, made of the notes' own words. One draw pass per frame:
 * time is only read while drawing, so the screen never recomposes for it. [focus] is the student's
 * area in root coordinates, where the rain's brightness is capped so the face and motes read first.
 */
@Composable
fun NotesRainBackground(
    text: RainText,
    highlight: Color,
    calm: Boolean,
    focus: () -> Rect,
    modifier: Modifier = Modifier,
) {
    val colors = Chalkroom.colors
    val reduced = Chalkroom.reducedMotion
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val styles = remember(density) {
        DEPTH_SIZES.map { TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = it.sp, color = Color.White) }
    }
    val calmness = remember { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(calm, reduced) {
        val target = if (calm) 1f else 0f
        if (reduced) calmness.snapTo(target) else calmness.animateTo(target, tween(CALM_MS, easing = EaseInOutSine))
    }
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val layout = remember(text, width, height, reduced, density) {
            val random = Random(text.words.size * 31L + width)
            val xs = ArrayList<Float>()
            val depths = ArrayList<Int>()
            var x = 0f
            while (x < width) {
                val roll = random.nextFloat()
                val d = if (roll < DEPTH_WEIGHTS[0]) 0 else if (roll < DEPTH_WEIGHTS[0] + DEPTH_WEIGHTS[1]) 1 else 2
                xs += x
                depths += d
                x += with(density) { DEPTH_SIZES[d].sp.toPx() } * CELL_W
            }
            val cellW = FloatArray(3) { with(density) { DEPTH_SIZES[it].sp.toPx() } * CELL_W }
            val cellH = FloatArray(3) { with(density) { DEPTH_SIZES[it].sp.toPx() } * CELL_H }
            val rows = IntArray(xs.size) { ceil(height / cellH[depths[it]]).toInt() }
            RainLayout(xs.toFloatArray(), depths.toIntArray(), cellW, cellH, NotesRain(text, rows, seed = random.nextLong(), frozen = reduced))
        }
        val frame = remember { mutableLongStateOf(0L) }
        LaunchedEffect(layout) {
            if (layout.model.frozen) return@LaunchedEffect
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
                        layout.model.advance(dt * (1f - calmness.value * (1f - CALM_SPEED)))
                    }
                    last = now
                    frame.longValue = now
                }
            }
        }
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                frame.longValue // redraw each frame; nothing recomposes
                val dim = 1f - calmness.value * (1f - CALM_DIM)
                val area = focus()
                val feather = FOCUS_FEATHER.toPx()
                layout.model.forEachCell { column, row, glyph, fade, head, highlighted ->
                    val d = layout.depth[column]
                    val cw = layout.cellW[d]
                    val ch = layout.cellH[d]
                    val cx = layout.x[column] + cw / 2
                    val cy = row * ch + ch / 2
                    val base = DEPTH_ALPHA[d]
                    val color: Color
                    val alpha: Float
                    when {
                        head -> { color = colors.card; alpha = minOf(1f, base + 0.2f) }
                        highlighted -> { color = highlight; alpha = fade * minOf(1f, base + 0.3f) }
                        else -> { color = colors.rain; alpha = fade * base }
                    }
                    val a = alpha * dim * focusCap(cx, cy, area, feather)
                    if (a < 0.01f) return@forEachCell
                    val key = (d shl 20) or glyph.code
                    val res = layout.glyphs.getOrPut(key) { measurer.measure(glyph.toString(), styles[d]) }
                    drawText(res, color, Offset(cx - res.size.width / 2f, cy - res.size.height / 2f), alpha = a)
                }
            },
        )
    }
}

/** 1 away from the student, [FOCUS_ALPHA] over them, eased across [feather]. */
private fun focusCap(x: Float, y: Float, area: Rect, feather: Float): Float {
    if (area.isEmpty) return 1f
    val dx = maxOf(area.left - x, 0f, x - area.right)
    val dy = maxOf(area.top - y, 0f, y - area.bottom)
    val dist = maxOf(dx, dy)
    return FOCUS_ALPHA + (1f - FOCUS_ALPHA) * (dist / feather).coerceIn(0f, 1f)
}
