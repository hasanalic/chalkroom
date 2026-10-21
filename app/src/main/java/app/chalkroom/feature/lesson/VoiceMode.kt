package app.chalkroom.feature.lesson

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import app.chalkroom.feature.lesson.LessonContract.UiEvent
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme

/** The recognizer for this screen; its callbacks reach the latest [onEvent]. */
@Composable
fun rememberSpeechInput(onEvent: (UiEvent) -> Unit): SpeechInput {
    val context = LocalContext.current
    val events by rememberUpdatedState(onEvent)
    return remember(context) {
        AndroidSpeechInput(
            context,
            object : SpeechInput.Listener {
                override fun onListening() = events(UiEvent.VoiceListening)
                override fun onQuiet() = events(UiEvent.VoiceQuiet)
                override fun onPartial(text: String) = events(UiEvent.VoicePartial(text))
                override fun onFinal(text: String) = events(UiEvent.VoiceFinal(text))
                override fun onError(error: VoiceError) = events(UiEvent.VoiceFailed(error))
            },
        )
    }
}

private fun Context.micGranted() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Mic permission and the recognizer's lifetime for voice mode. The mic is open only while voice mode
 * wants it and the app is in the foreground; leaving the screen destroys the recognizer.
 */
@Composable
fun VoiceInputEffects(voice: VoiceInput?, speech: SpeechInput, onEvent: (UiEvent) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val events by rememberUpdatedState(onEvent)
    var granted by remember { mutableStateOf(context.micGranted()) }
    fun refresh() {
        granted = context.micGranted()
    }

    // "Don't ask again" and a dismissed dialog both leave no rationale, so only a second refusal in a
    // row sends the user to Settings. Reset each time the overlay opens.
    var refusedOnce by remember(voice == null) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(RequestPermission()) { ok ->
        granted = ok
        val rationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO) } ?: false
        val blocked = !ok && refusedOnce && !rationale
        if (!ok) refusedOnce = true
        events(UiEvent.VoicePermission(granted = ok, blocked = blocked))
    }

    // Asks once per tap or Retry: only on the way into STARTING, never from ERROR.
    val phase = voice?.phase
    LaunchedEffect(phase) {
        if (phase == VoicePhase.STARTING) {
            refresh()
            if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Back from Settings with the mic now allowed: pick up by itself. Only on a real false → true
    // change, or a key change while resumed would retry in a loop.
    val error = voice?.error
    LifecycleResumeEffect(error) {
        val was = granted
        refresh()
        if (!was && granted && (error == VoiceError.PERMISSION_BLOCKED || error == VoiceError.PERMISSION_DENIED)) {
            events(UiEvent.VoiceRetry)
        }
        onPauseOrDispose { }
    }

    val listening = voice?.wantsMic == true && granted
    LifecycleStartEffect(listening) {
        if (listening) speech.start()
        onStopOrDispose { speech.cancel() }
    }

    DisposableEffect(speech) {
        onDispose { speech.destroy() }
    }

    BackHandler(enabled = voice != null) { events(UiEvent.VoiceCancel) }
}

/**
 * Voice mode over the whole lesson: the words as they are heard, Cancel and Done. It swallows every
 * touch. No waveform, no timer, no spinner, no red.
 */
@Composable
fun VoiceOverlay(voice: VoiceInput?, studentColor: Color, onEvent: (UiEvent) -> Unit) {
    // Hold the last open state through the fade-out, so the last frame isn't an empty overlay.
    val held = remember { arrayOfNulls<VoiceInput>(1) }
    voice?.let { held[0] = it }
    val fade = if (Chalkroom.reducedMotion) 0 else VOICE_FADE_MS
    AnimatedVisibility(voice != null, enter = fadeIn(tween(fade)), exit = fadeOut(tween(fade))) {
        held[0]?.let { VoiceOverlayContent(it, studentColor, onEvent) }
    }
}

private const val VOICE_FADE_MS = 200

@Composable
private fun VoiceOverlayContent(voice: VoiceInput, studentColor: Color, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ink.copy(alpha = 0.9f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        VoiceIndicator(voice.phase, studentColor)
        Spacer(Modifier.height(16.dp))
        HeardWords(voice, Modifier.weight(1f).fillMaxWidth())

        voice.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(
                error.message,
                style = Chalkroom.type.small.copy(color = colors.page.copy(alpha = 0.7f), textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
            when (error) {
                VoiceError.FAILED, VoiceError.PERMISSION_DENIED -> TextAction("Try again") { onEvent(UiEvent.VoiceRetry) }
                VoiceError.PERMISSION_BLOCKED -> TextAction("Open settings") {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }
                VoiceError.UNAVAILABLE -> Unit
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuietButton("Cancel", { onEvent(UiEvent.VoiceCancel) }, Modifier.weight(1f))
            PrimaryButton("Done", { onEvent(UiEvent.VoiceDone) }, Modifier.weight(1f), enabled = voice.canFinish)
        }
    }
}

/** The student's colour with the mic in it: breathes while listening, rests dimmed while quiet, gone on an error. */
@Composable
private fun VoiceIndicator(phase: VoicePhase, studentColor: Color) {
    if (phase == VoicePhase.ERROR) {
        Spacer(Modifier.size(INDICATOR_SIZE))
        return
    }
    val breathing = phase == VoicePhase.LISTENING && !Chalkroom.reducedMotion
    val scale = if (breathing) {
        val breath = rememberInfiniteTransition(label = "breath")
        breath.animateFloat(1f, 1.08f, infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse), label = "scale").value
    } else {
        1f
    }
    val micColor = if (studentColor.luminance() < 0.4f) Chalkroom.colors.page else Chalkroom.colors.ink
    Box(
        Modifier
            .size(INDICATOR_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (phase == VoicePhase.QUIET) 0.55f else 1f
            }
            .clip(CircleShape)
            .background(studentColor)
            .semantics { contentDescription = "Listening…" },
        contentAlignment = Alignment.Center,
    ) {
        MicGlyph(micColor, Modifier.size(26.dp))
    }
}

private val INDICATOR_SIZE = 56.dp

/**
 * What was heard: settled words at full colour, the current guess fainter. Short text sits in the
 * middle; long text scrolls, and every change slides to the newest words.
 */
@Composable
private fun HeardWords(voice: VoiceInput, modifier: Modifier) {
    val colors = Chalkroom.colors
    val reduced = Chalkroom.reducedMotion
    val style = Chalkroom.type.headline.copy(fontWeight = FontWeight.Bold, color = colors.page, textAlign = TextAlign.Center)
    val scroll = rememberScrollState()
    val transcript = voice.transcript
    LaunchedEffect(transcript) {
        withFrameNanos { } // let the new text lay out first
        if (reduced) scroll.scrollTo(scroll.maxValue) else scroll.animateScrollTo(scroll.maxValue)
    }
    BoxWithConstraints(modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = maxHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (transcript.isEmpty()) {
                Text("Listening…", style = style.copy(color = colors.page.copy(alpha = 0.7f)))
            } else {
                Text(
                    buildAnnotatedString {
                        if (voice.finalized.isNotBlank()) append(voice.finalized.trim())
                        if (voice.finalized.isNotBlank() && voice.partial.isNotBlank()) append(" ")
                        if (voice.partial.isNotBlank()) withStyle(SpanStyle(color = colors.page.copy(alpha = 0.55f))) { append(voice.partial.trim()) }
                    },
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Chalkroom.type.label.copy(color = Chalkroom.colors.page))
    }
}

/** A small mic drawn in code: capsule head, U-shaped holder, short stem. 2dp strokes. */
@Composable
fun MicGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val headW = w * 0.36f
        val headH = h * 0.5f
        val headTop = h * 0.06f
        drawRoundRect(
            color,
            topLeft = Offset((w - headW) / 2, headTop),
            size = Size(headW, headH),
            cornerRadius = CornerRadius(headW / 2),
            style = Stroke(stroke),
        )
        // The holder: a U hugging the head's lower half.
        val holderW = w * 0.62f
        val holderTop = headTop + headH * 0.42f
        val holderBottom = headTop + headH + h * 0.12f
        drawArc(
            color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset((w - holderW) / 2, holderTop - (holderBottom - holderTop)),
            size = Size(holderW, (holderBottom - holderTop) * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(w / 2, holderBottom), Offset(w / 2, h * 0.94f), stroke, cap = StrokeCap.Round)
    }
}

@Preview(heightDp = 760, widthDp = 380)
@Composable
private fun VoiceOverlayPreview() = ChalkroomTheme {
    VoiceOverlay(
        VoiceInput(phase = VoicePhase.LISTENING, finalized = "plants take in light", partial = "and turn it into sugar"),
        Color(0xFFC9E265),
    ) {}
}

@Preview(heightDp = 760, widthDp = 380)
@Composable
private fun VoiceOverlayErrorPreview() = ChalkroomTheme {
    VoiceOverlay(VoiceInput(phase = VoicePhase.ERROR, error = VoiceError.PERMISSION_BLOCKED), Color(0xFFC9E265)) {}
}
