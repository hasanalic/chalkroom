package app.chalkroom.students

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import app.chalkroom.shared.StudentId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** RELAXED: settled after the last belief clears (closing). */
enum class Expression { IDLE, THINKING, PUZZLED, GOT_IT, RELAXED }

/** Where the eyes point. [READING] scans side to side, down at the notes. */
enum class Look { AT_YOU, READING }

/**
 * Face parameters an expression tweens between. No mouths: expression lives in the eyes, lids,
 * head tilt and squash/stretch. Offsets are fractions of the figure width; [tilt] is degrees.
 */
data class FaceTargets(
    val eyeScale: Float,
    val eyeDx: Float,
    val eyeDy: Float,
    /** How much smaller the right eye is (puzzled). */
    val asym: Float,
    /** 0 = lids up, 1 = shut. */
    val lid: Float,
    val tilt: Float,
    /** Vertical stretch of the body, e.g. +0.03 for a little "aha" lift. */
    val stretch: Float,
    /**
     * 0 = none, 1 = the eye's bottom cut away by an upward arc, so it reads as smiling. Content
     * faces use this, never a lowered upper lid: on a mouthless face a lid reads as a frown.
     */
    val smile: Float = 0f,
)

fun Expression.targets(): FaceTargets = when (this) {
    Expression.IDLE -> FaceTargets(1f, 0f, 0f, 0f, 0f, 0f, 0f)
    Expression.THINKING -> FaceTargets(0.92f, 0.05f, -0.045f, 0f, 0f, 4f, 0f)
    Expression.PUZZLED -> FaceTargets(1f, 0f, 0f, 0.35f, 0f, -6f, 0f)
    Expression.GOT_IT -> FaceTargets(1.3f, 0f, -0.01f, 0f, 0f, 0f, 0.03f)
    Expression.RELAXED -> FaceTargets(1f, 0f, 0.012f, 0f, 0f, 3f, -0.015f, smile = 1f)
}

/** Expressions where the eyes wander on their own. */
val Expression.glances: Boolean get() = this == Expression.IDLE || this == Expression.PUZZLED || this == Expression.RELAXED

/** Each student breathes and blinks on its own clock, so the room never moves in sync. */
private fun StudentId.breathMs(): Int = when (this) {
    StudentId.MIRA -> 3_300
    StudentId.PIP -> 3_000
    StudentId.JUNO -> 3_900
}

/** Arrivals overshoot a hair and settle. */
fun <T> settle() = spring<T>(dampingRatio = 0.72f, stiffness = 420f)

/** Eyes dart: most of the travel in the first third, then a soft landing. */
val Dart = CubicBezierEasing(0.15f, 0.85f, 0.3f, 1f)

/** 0 → 1 → 0 on a 3–4 s ease-in-out-sine loop; stays 0 when [still]. */
@Composable
fun rememberBreath(id: StudentId, still: Boolean): State<Float> {
    if (still) return remember { mutableFloatStateOf(0f) }
    val offset = remember { Random.nextInt(0, id.breathMs()) }
    return rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(id.breathMs(), easing = EaseInOutSine), RepeatMode.Reverse, StartOffset(offset)),
        label = "breath",
    )
}

/** 0 = open, 1 = shut. Every 2–6 s, ~150 ms, sometimes a double blink. */
@Composable
fun rememberBlink(still: Boolean): State<Float> {
    val blink = remember { Animatable(0f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect blink.snapTo(0f)
        while (true) {
            delay(Random.nextLong(2_000, 6_000))
            repeat(if (Random.nextFloat() < 0.2f) 2 else 1) {
                blink.animateTo(1f, tween(65))
                blink.animateTo(0f, tween(85))
                delay(90)
            }
        }
    }
    return blink.asState()
}

/** Eye offset and the head's late follow, in fractions of the figure width. */
class Glance {
    val eyeX = Animatable(0f)
    val eyeY = Animatable(0f)
    val headX = Animatable(0f)
}

/** Eyes glance around now and then (dart there, hold, dart back); the head follows slightly late. */
@Composable
fun rememberGlance(active: Boolean): Glance {
    val g = remember { Glance() }
    LaunchedEffect(active) {
        suspend fun go(x: Float, y: Float) { coroutineScope {
            launch { g.eyeX.animateTo(x, tween(240, easing = Dart)) }
            launch { g.eyeY.animateTo(y, tween(240, easing = Dart)) }
            launch {
                delay(130)
                g.headX.animateTo(x, tween(520, easing = EaseInOutSine))
            }
        } }
        if (!active) return@LaunchedEffect go(0f, 0f)
        delay(Random.nextLong(600, 3_000))
        while (true) {
            go(Random.nextFloat() * 0.1f - 0.05f, Random.nextFloat() * 0.045f - 0.03f)
            delay(Random.nextLong(700, 2_000))
            if (Random.nextFloat() < 0.6f) go(0f, 0f)
            delay(Random.nextLong(2_500, 7_000))
        }
    }
    return g
}

/** Reading along a line: left to right, a pause, a short glance back, a pause, then back to the start. */
@Composable
fun rememberScan(): State<Float> =
    rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = -0.04f,
        targetValue = -0.04f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2_600
                -0.04f at 0 using EaseInOutSine
                0.04f at 1_000 using Dart
                0.04f at 1_400 using Dart
                0.012f at 1_650 using EaseInOutSine
                0.012f at 2_000 using Dart
                -0.04f at 2_400
            },
        ),
        label = "scan",
    )

/** Seconds of mote drift; the clock stops (motes hold where they are) while [still]. */
@Composable
fun rememberDriftClock(still: Boolean): State<Float> {
    val t = remember { mutableFloatStateOf(Random.nextFloat() * 10f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    return t
}

@Composable
fun tweenFace(value: Float, label: String): State<Float> =
    animateFloatAsState(value, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = label)
