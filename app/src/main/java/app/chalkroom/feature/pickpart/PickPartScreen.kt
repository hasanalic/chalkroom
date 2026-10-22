package app.chalkroom.feature.pickpart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chalkroom.data.pdf.Part
import app.chalkroom.data.pdf.PdfTitle
import app.chalkroom.feature.pickpart.PickPartContract.UiEffect
import app.chalkroom.feature.pickpart.PickPartContract.UiEvent
import app.chalkroom.feature.pickpart.PickPartContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.ui.components.CardShape
import app.chalkroom.ui.components.Ledge
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PickPartScreen(
    onChooseStudent: () -> Unit,
    onRead: (StudentId) -> Unit,
    onBack: () -> Unit,
    viewModel: PickPartViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                UiEffect.ChooseStudent -> onChooseStudent()
                is UiEffect.Read -> onRead(it.studentId)
                UiEffect.Back -> onBack()
            }
        }
    }
    BackHandler { viewModel.onEvent(UiEvent.Back) }
    PickPartContent(state, viewModel::onEvent)
}

/** "Which part are we doing?": one card per part, one selected, and a gold button pinned at the bottom. */
@Composable
fun PickPartContent(state: UiState, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    val type = Chalkroom.type
    Column(Modifier.fillMaxSize().background(colors.page).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(pickPartHeader(state.studentName), style = type.headline, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(state.parts, key = { _, part -> part.index }) { i, part ->
                PartCard(part, selected = i == state.selected, onClick = { onEvent(UiEvent.Selected(i)) })
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
        PrimaryButton(
            "This is today's lesson",
            { onEvent(UiEvent.Confirm) },
            enabled = state.parts.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("todaysLesson"),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** A light card on its ledge; the selected one has an ink border and sits pressed onto its ledge. */
@Composable
private fun PartCard(part: Part, selected: Boolean, onClick: () -> Unit) {
    val colors = Chalkroom.colors
    val type = Chalkroom.type
    Ledge(
        shape = CardShape,
        color = colors.card,
        ledgeColor = colors.lavender2,
        pressed = selected,
        border = if (selected) colors.ink else colors.line,
        borderWidth = if (selected) 2.dp else 1.5.dp,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }.testTag("part${part.index}"),
        contentModifier = Modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(PdfTitle.partLabel(part), style = type.small)
            Text(part.heading, style = type.label, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(PdfTitle.length(part.text), style = type.small, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun PickPartPreview() {
    ChalkroomTheme {
        PickPartContent(
            UiState(
                parts = listOf(
                    Part(1, "Cells are the units of life. ".repeat(40), "What a cell is", 1, 3),
                    Part(2, "Membranes keep the inside in. ".repeat(40), "The cell membrane", 3, 6),
                    Part(3, "Mitochondria make energy. ".repeat(40), "Energy and mitochondria", 7, 7),
                ),
                selected = 1,
            ),
            onEvent = {},
        )
    }
}
