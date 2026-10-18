package app.chalkroom.students

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import app.chalkroom.shared.StudentId
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class Expression { IDLE, THINKING, PUZZLED, GOT_IT }

/** Where the eyes point. [READING] scans side to side, down at the notes. */
enum class Look { AT_YOU, READING }

/** Face parameters an expression tweens between. Offsets are in fractions of the figure width. */
data class FaceTargets(
    val eyeScale: Float,
    val eyeDx: Float,
    val eyeDy: Float,
    val asym: Float,
    val mouthCurve: Float,
    val mouthWave: Float,
    val mouthOpen: Float,
)

fun Expression.targets(): FaceTargets = when (this) {
    Expression.IDLE -> FaceTargets(1f, 0f, 0f, 0f, 0.55f, 0f, 0f)
    Expression.THINKING -> FaceTargets(0.9f, 0.035f, -0.035f, 0f, 0.05f, 0f, 0f)
    Expression.PUZZLED -> FaceTargets(0.95f, 0f, 0f, 0.35f, -0.1f, 1f, 0f)
    Expression.GOT_IT -> FaceTargets(1.32f, 0f, -0.01f, 0f, 0.6f, 0f, 1f)
}

/** Each student breathes and blinks on its own clock, so the room never moves in sync. */
private fun StudentId.blinkRangeMs(): LongRange = when (this) {
    StudentId.MIRA -> 3_000L..5_000L
    StudentId.PIP -> 2_500L..4_000L
    StudentId.JUNO -> 4_000L..6_000L
}

private fun StudentId.breathMs(): Int = when (this) {
    StudentId.MIRA -> 3_200
    StudentId.PIP -> 2_700
    StudentId.JUNO -> 3_900
}

const val EXPRESSION_TWEEN_MS = 220

/** Gentle body scale, 1.0 ↔ 1.015. */
@Composable
fun rememberBreath(id: StudentId): State<Float> =
    rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(id.breathMs(), easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )

/** 0 = open, 1 = shut. */
@Composable
fun rememberBlink(id: StudentId): State<Float> {
    val blink = remember { Animatable(0f) }
    LaunchedEffect(id) {
        val range = id.blinkRangeMs()
        while (true) {
            delay(Random.nextLong(range.first, range.last))
            blink.animateTo(1f, tween(70))
            blink.animateTo(0f, tween(90))
        }
    }
    return blink.asState()
}

/** Side-to-side scan while reading, in fractions of the figure width. */
@Composable
fun rememberScan(): State<Float> =
    rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = -0.04f,
        targetValue = 0.04f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scan",
    )

/** 0 → 1 once every 14 s: drives the motes' slow orbit. */
@Composable
fun rememberOrbit(): State<Float> =
    rememberInfiniteTransition(label = "orbit").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "orbit",
    )

@Composable
fun tweenFace(value: Float, label: String): State<Float> =
    animateFloatAsState(value, tween(EXPRESSION_TWEEN_MS, easing = FastOutSlowInEasing), label = label)
