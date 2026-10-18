package app.chalkroom.feature.classroom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.feature.classroom.ClassroomContract.UiEffect
import app.chalkroom.feature.classroom.ClassroomContract.UiEvent
import app.chalkroom.feature.classroom.ClassroomContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Expression
import app.chalkroom.students.Mote
import app.chalkroom.students.StudentAtDesk
import app.chalkroom.students.StudentCatalog
import app.chalkroom.students.nod
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ClassroomScreen(
    onTeachSomething: () -> Unit,
    onResumeLesson: (StudentId) -> Unit,
    viewModel: ClassroomViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                UiEffect.OpenPasteNotes -> onTeachSomething()
                is UiEffect.OpenLesson -> onResumeLesson(it.studentId)
            }
        }
    }
    ClassroomContent(state, viewModel::onEvent)
}

@Composable
fun ClassroomContent(state: UiState, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    Box(Modifier.fillMaxSize().background(colors.paper)) {
        // The floor: the lower part of the room is a deeper paper tone, meeting the wall in a soft line.
        Canvas(Modifier.fillMaxSize()) {
            val floorTop = size.height * 0.56f
            drawRect(colors.paperDeep, Offset(0f, floorTop))
            drawLine(colors.ink.copy(alpha = 0.18f), Offset(0f, floorTop), Offset(size.width, floorTop), 3f)
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Board(state.board, onTap = { onEvent(UiEvent.BoardTapped) })
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                state.seats.forEach { seat ->
                    // The middle desk sits a little closer, so the row reads as a room, not a grid.
                    val closer = seat.studentId == StudentId.PIP
                    Seat(
                        seat = seat,
                        onTap = { onEvent(UiEvent.StudentTapped(seat.studentId)) },
                        modifier = Modifier.weight(if (closer) 1.12f else 1f).padding(bottom = if (closer) 0.dp else 26.dp),
                    )
                }
            }
            Spacer(Modifier.weight(0.6f))
            PrimaryButton("Teach something", onClick = { onEvent(UiEvent.TeachSomething) })
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Board(board: ClassroomContract.Board?, onTap: () -> Unit) {
    val colors = Chalkroom.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .background(colors.woodDark)
            .border(2.5.dp, colors.ink, shape)
            .padding(9.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(colors.board)
            .then(if (board != null) Modifier.clickable(role = Role.Button, onClick = onTap) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (board != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${board.name} left this here", style = Chalkroom.type.chalk)
                Text(
                    when (board.remaining) {
                        0 -> "Nothing left that doesn't click"
                        1 -> "1 thing still doesn't click"
                        else -> "${board.remaining} things still don't click"
                    },
                    style = Chalkroom.type.chalk.copy(color = colors.chalk.copy(alpha = 0.75f)),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Seat(seat: ClassroomContract.Seat, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val nod = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
        ) {
            scope.launch {
                nod.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
                nod.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            }
            onTap()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StudentAtDesk(
            studentId = seat.studentId,
            expression = Expression.IDLE,
            motes = seat.motes,
            notebookOpen = seat.paused,
            figureModifier = Modifier.nod(nod.value),
        )
        Text(StudentCatalog[seat.studentId].name, style = Chalkroom.type.label.copy(color = Chalkroom.colors.inkSoft), modifier = Modifier.padding(top = 6.dp))
    }
}

@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun ClassroomPreview() {
    ChalkroomTheme {
        ClassroomContent(
            UiState(
                board = ClassroomContract.Board(StudentId.MIRA, "Mira", 3),
                seats = StudentId.entries.map {
                    ClassroomContract.Seat(it, paused = it == StudentId.MIRA, motes = if (it == StudentId.MIRA) listOf(Mote("a"), Mote("b"), Mote("c")) else emptyList())
                },
            ),
            onEvent = {},
        )
    }
}
