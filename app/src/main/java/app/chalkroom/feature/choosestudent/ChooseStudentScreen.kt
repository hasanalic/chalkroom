package app.chalkroom.feature.choosestudent

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiEffect
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiEvent
import app.chalkroom.feature.choosestudent.ChooseStudentContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentCatalog
import app.chalkroom.students.StudentFigure
import app.chalkroom.students.StudentShapes
import app.chalkroom.ui.components.BUTTON_LEDGE_DP
import app.chalkroom.ui.components.CardShape
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChooseStudentScreen(
    onRead: (StudentId) -> Unit,
    onResumeLesson: (StudentId) -> Unit,
    viewModel: ChooseStudentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                is UiEffect.Read -> onRead(it.studentId)
                is UiEffect.ResumeLesson -> onResumeLesson(it.studentId)
            }
        }
    }
    ChooseStudentContent(state, viewModel::onEvent)
}

/**
 * Title, then the screen split into three equal boxes (Mira, Pip, Juno), each with its student big
 * and centred, then a gold Continue pinned at the bottom. Tapping a box picks that student; only
 * Continue moves on.
 */
@Composable
fun ChooseStudentContent(state: UiState, onEvent: (UiEvent) -> Unit) {
    // A pick made before this screen was (re)composed is already settled: don't replay it.
    val firstTick = remember { state.selectTick }
    Column(Modifier.fillMaxSize().background(Chalkroom.colors.page).safeDrawingPadding()) {
        Text(
            "Who should learn this?",
            style = Chalkroom.type.headline,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val order = state.students.map { it.studentId }
            state.students.forEachIndexed { i, choice ->
                val picked = state.selected
                val cue = when {
                    picked == null || state.selectTick == firstTick -> null
                    picked == choice.studentId -> Cue.Celebrate(state.selectTick)
                    else -> Cue.LookAtPick(if (order.indexOf(picked) < i) -1 else 1, state.selectTick)
                }
                StudentBox(
                    choice,
                    selected = picked == choice.studentId,
                    cue = cue,
                    onClick = { onEvent(UiEvent.Selected(choice.studentId)) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
        PrimaryButton(
            "Continue",
            { onEvent(UiEvent.Continue) },
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            enabled = state.selected != null,
        )
    }
    state.confirm?.let { ConfirmSheet(it, onEvent) }
}

/** The student already has a paused lesson: say so, and let you pick which lesson to have. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(confirm: ChooseStudentContract.Confirm, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    val name = StudentCatalog[confirm.studentId].name
    ModalBottomSheet(onDismissRequest = { onEvent(UiEvent.DismissConfirm) }, containerColor = colors.page) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$name is still on ${confirm.pausedTopic}", style = Chalkroom.type.headline)
            Text(
                "${stillOpenLine(confirm.stillOpen)} Starting your new notes ends that lesson.",
                style = Chalkroom.type.body,
            )
            PrimaryButton("Teach the new notes", { onEvent(UiEvent.StartNew) }, Modifier.fillMaxWidth().padding(top = 4.dp))
            QuietButton("Continue ${confirm.pausedTopic}", { onEvent(UiEvent.ContinuePaused) }, Modifier.fillMaxWidth())
        }
    }
}

private fun stillOpenLine(n: Int) = if (n == 1) "1 thing still doesn't click." else "$n things still don't click."

/**
 * The highest each head gets in its idle loop, in the figure canvas (figure widths), so the text
 * above never meets it: Mira's pricked ears, the top of Pip's highest hop, Juno's hat knob.
 */
private fun StudentId.headTop(): Float = when (this) {
    StudentId.MIRA -> 0.36f
    StudentId.PIP -> PIP_TIP - MAX_HOP
    StudentId.JUNO -> 0.38f
}

/** A mote's radius (the last one's, the biggest) plus its ring, figure widths. */
private const val MOTE_ROOM = 0.09f

/** Pip's tip is where his jump has to clear the box top from. */
private const val PIP_TIP = 0.33f

/**
 * One student's box: a card on a ledge (the student's colour once picked), the name and self-intro
 * across the top, the student big and centred below, cropped by the box's bottom edge but free to
 * jump out of its top. The box presses down onto its ledge under a finger.
 */
@Composable
private fun StudentBox(
    choice: ChooseStudentContract.Choice,
    selected: Boolean,
    cue: Cue?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Chalkroom.colors
    val reduced = Chalkroom.reducedMotion
    val id = choice.studentId
    val profile = StudentCatalog[id]
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = BUTTON_LEDGE_DP.dp
    val press by animateDpAsState(
        if (pressed) depth else 0.dp,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh),
        label = "press",
    )

    val pose = remember { Pose() }
    LaunchedEffect(cue, reduced) {
        pose.settle(snap = reduced)
        if (reduced) return@LaunchedEffect
        when (cue) {
            is Cue.Celebrate -> pose.celebrate(id)
            is Cue.LookAtPick -> pose.lookAtPick(cue.dir)
            null -> Unit
        }
        pose.idleLoop(id)
    }

    Box(
        modifier
            .padding(bottom = depth)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        Box(Modifier.matchParentSize().offset(y = depth).clip(CardShape).background(if (selected) profile.color else colors.line))
        Box(
            Modifier.matchParentSize().offset(y = press).clip(CardShape).background(colors.card)
                .border(2.dp, colors.ink, CardShape),
        )
        Layout(
            content = {
                BoxText(choice)
                StudentSlot(choice, pose)
            },
            modifier = Modifier.matchParentSize().offset(y = press),
        ) { (text, figure), c ->
            val pad = 14.dp.roundToPx()
            val border = 2.dp.roundToPx()
            val t = text.measure(Constraints(maxWidth = c.maxWidth - 2 * pad))
            val gap = 4.dp.roundToPx()
            val room = c.maxHeight - border - (pad + t.height + gap)
            // A paused lesson's motes ride in an arc above the head: keep the text clear of them too.
            val top = if (choice.motes.isEmpty()) id.headTop() else minOf(id.headTop(), StudentShapes.face(id).moteTop - MOTE_ROOM)
            val w = minOf(room / (FIGURE_H - top), c.maxWidth * 0.5f).toInt().coerceAtLeast(1)
            val h = (w * FIGURE_H).toInt()
            val f = figure.measure(Constraints.fixed(w, h))
            layout(c.maxWidth, c.maxHeight) {
                t.place(pad, pad)
                f.place((c.maxWidth - w) / 2, c.maxHeight - border - h)
            }
        }
    }
}

@Composable
private fun BoxText(choice: ChooseStudentContract.Choice) {
    val colors = Chalkroom.colors
    val profile = StudentCatalog[choice.studentId]
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(profile.name, style = Chalkroom.type.title)
            if (choice.paused) {
                Text(
                    "Paused lesson",
                    style = Chalkroom.type.small.copy(color = colors.ink, fontWeight = Chalkroom.type.label.fontWeight),
                    modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(50)).background(colors.lavender)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        Text(profile.intro, style = Chalkroom.type.small.copy(color = colors.ink), modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * The student and the props drawn around them. Clipped only at the bottom (the box's edge), so a
 * jump or a popping hat can rise out of the box.
 */
@Composable
private fun StudentSlot(choice: ChooseStudentContract.Choice, pose: Pose) {
    val colors = Chalkroom.colors
    val id = choice.studentId
    val profile = StudentCatalog[id]
    val reduced = Chalkroom.reducedMotion
    Box(
        Modifier
            .onPlaced {
                // Lift (figure widths) that takes Pip's tip a little past the top of his box.
                val w = it.size.width.toFloat()
                if (w > 0f) pose.jumpLift = (it.positionInParent().y / w) + PIP_TIP + 0.2f
            }
            .drawWithContent {
                clipRect(left = -size.width * 4, top = -size.height * 6, right = size.width * 5, bottom = size.height) {
                    this@drawWithContent.drawContent()
                }
            },
    ) {
        // The student, and Juno's book, which moves with her.
        Box(
            Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = pose.scaleX.value
                scaleY = pose.scaleY.value
                rotationZ = pose.lean.value
                rotationY = pose.spin.value
                cameraDistance = 16f * density
            },
        ) {
            StudentFigure(
                id,
                pose.expression,
                Modifier.fillMaxWidth(),
                motes = choice.motes,
                lookAt = pose.lookAt,
                headX = pose.headX,
                lift = pose.lift.value,
                ears = pose.ears.value,
                hatLift = pose.hat.value,
            )
            if (id == StudentId.JUNO) {
                Canvas(Modifier.matchParentSize()) { drawBook(size.width, pose.page.value, colors) }
            }
        }
        // Things in the air, which don't lean with the student.
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            if (reduced) return@Canvas
            when (id) {
                StudentId.MIRA -> {
                    drawFleck(pose.fleck.value, w, pose.fleckAlpha.value, colors)
                    drawSparkles(pose.sparkleAt, pose.sparkle.value, w, listOf(colors.page), colors.ink, spread = 1.3f)
                }
                StudentId.PIP -> drawSparkles(pose.sparkleAt, pose.sparkle.value, w, listOf(colors.gold, profile.color), colors.ink, spread = 1.1f)
                StudentId.JUNO -> Unit
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun ChooseStudentPreview() {
    ChalkroomTheme {
        ChooseStudentContent(
            UiState(
                StudentId.entries.map {
                    ChooseStudentContract.Choice(
                        it, paused = it == StudentId.JUNO,
                        motes = if (it == StudentId.JUNO) listOf(Mote("a"), Mote("b")) else emptyList(),
                    )
                },
                selected = StudentId.PIP,
            ),
            onEvent = {},
        )
    }
}
