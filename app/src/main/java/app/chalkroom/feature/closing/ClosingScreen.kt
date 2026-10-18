package app.chalkroom.feature.closing

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import app.chalkroom.data.ClearedItem
import app.chalkroom.feature.closing.ClosingContract.Beat
import app.chalkroom.feature.closing.ClosingContract.UiEffect
import app.chalkroom.feature.closing.ClosingContract.UiEvent
import app.chalkroom.feature.closing.ClosingContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.StudentCatalog
import app.chalkroom.students.StudentFigure
import app.chalkroom.ui.components.LineCard
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ClosingScreen(
    studentId: StudentId,
    onTeachAgain: (StudentId) -> Unit,
    onBackToClass: () -> Unit,
    viewModel: ClosingViewModel = koinViewModel { parametersOf(studentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                UiEffect.SoftHaptic -> view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK,
                )
                is UiEffect.TeachAgain -> onTeachAgain(it.studentId)
                UiEffect.BackToClass -> onBackToClass()
            }
        }
    }
    ClosingContent(state, viewModel::onEvent)
}

@Composable
fun ClosingContent(state: UiState, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    val profile = StudentCatalog[state.studentId]
    val scroll = rememberScrollState()
    // Bring the notebook into view before it closes, so the moment isn't missed below the fold.
    val showNotebook = state.reached(if (state.whatStuck.isEmpty()) Beat.RELIEF else Beat.WHAT_STUCK)
    LaunchedEffect(showNotebook) {
        if (showNotebook) {
            delay(NOTEBOOK_SCROLL_DELAY_MS)
            scroll.animateScrollTo(scroll.maxValue, tween(600, easing = FastOutSlowInEasing))
        }
    }
    val optionsShown = state.reached(Beat.OPTIONS)
    val optionsAlpha by animateFloatAsState(if (optionsShown) 1f else 0f, tween(300), label = "options")
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nothing left in ${profile.name}'s head", style = Chalkroom.type.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            StudentFigure(state.studentId, state.expression, Modifier.width(150.dp))

            AnimatedVisibility(state.reached(Beat.RELIEF), enter = fadeIn(tween(320))) {
                LineCard(profile.reliefLine, Modifier.fillMaxWidth().padding(top = 16.dp))
            }

            if (state.whatStuck.isNotEmpty()) {
                AnimatedVisibility(state.reached(Beat.WHAT_STUCK), enter = fadeIn(tween(320))) {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        Text("What stuck", style = Chalkroom.type.label)
                        Spacer(Modifier.height(8.dp))
                        state.whatStuck.forEachIndexed { i, item ->
                            WhatStuckRow(item, open = state.openIndex == i) { onEvent(UiEvent.ToggleItem(i)) }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(state.reached(Beat.RELIEF), enter = fadeIn(tween(320))) {
                Notebook(state.topicTitle, closed = state.reached(Beat.NOTEBOOK_CLOSED))
            }
        }

        // Pinned below the scrolling part so both options are visible without scrolling; space is
        // reserved from the start so nothing jumps when they fade in.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer { alpha = optionsAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton("Teach ${profile.name} something else", { if (optionsShown) onEvent(UiEvent.TeachSomethingElse) }, enabled = optionsShown)
            QuietButton("Back to class", { if (optionsShown) onEvent(UiEvent.BackToClass) })
        }
    }
}

private const val NOTEBOOK_SCROLL_DELAY_MS = 450L

@Composable
private fun WhatStuckRow(item: ClearedItem, open: Boolean, onClick: () -> Unit) {
    val colors = Chalkroom.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.chalk)
            .border(1.5.dp, colors.ink.copy(alpha = 0.4f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
    ) {
        Text("“${item.yourSentence}”", style = Chalkroom.type.body)
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically()) {
            Text(
                "cleared: ${item.statement}",
                style = Chalkroom.type.body.copy(color = colors.inkSoft),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Open pages; when [closed], the cover flips over and shows the topic. */
@Composable
private fun Notebook(topicTitle: String, closed: Boolean) {
    val colors = Chalkroom.colors
    val flip by animateFloatAsState(
        if (closed) 180f else 0f,
        tween(ClosingViewModel.NOTEBOOK_CLOSE_MS.toInt(), easing = FastOutSlowInEasing),
        label = "cover",
    )
    val pageShape = RoundedCornerShape(6.dp)
    Box(Modifier.size(width = 260.dp, height = 150.dp).graphicsLayer { translationX = -(65.dp.toPx()) * (flip / 180f) }) {
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Page(Modifier.weight(1f).fillMaxHeight().clip(pageShape).background(colors.chalk).border(2.dp, colors.ink, pageShape))
        }
        // The left page (inside of the cover) swings over onto the right.
        Box(
            Modifier
                .fillMaxHeight()
                .width(130.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    rotationY = flip
                    cameraDistance = 14 * density
                },
        ) {
            if (flip < 90f) {
                Page(Modifier.fillMaxSize().clip(pageShape).background(colors.chalk).border(2.dp, colors.ink, pageShape))
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .clip(pageShape)
                        .background(colors.wood)
                        .border(2.dp, colors.woodDark, pageShape)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        topicTitle,
                        style = Chalkroom.type.label.copy(color = colors.chalk, hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading),
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                    )
                }
            }
        }
    }
}

@Composable
private fun Page(modifier: Modifier) {
    val colors = Chalkroom.colors
    Column(modifier.padding(horizontal = 12.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(4) { Box(Modifier.fillMaxWidth().height(1.5.dp).background(colors.ink.copy(alpha = 0.2f))) }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ClosingPreview() = ChalkroomTheme {
    ClosingContent(
        UiState(
            studentId = StudentId.PIP,
            topicTitle = "Photosynthesis",
            whatStuck = listOf(ClearedItem("I think the oxygen comes from carbon dioxide.", "The oxygen comes from splitting water.")),
            beat = Beat.OPTIONS,
        ),
        {},
    )
}
