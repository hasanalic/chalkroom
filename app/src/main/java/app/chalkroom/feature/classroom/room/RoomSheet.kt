package app.chalkroom.feature.classroom.room

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.chalkroom.feature.classroom.ClassroomContract.UiEvent
import app.chalkroom.ui.components.TextButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme

/**
 * The Room sheet: half the screen high, the room still in view above it. Every pick applies to the
 * room at once; there's no Save. Back or a swipe down closes it with the picks kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomSheet(room: RoomConfig, custom: Boolean, onEvent: (UiEvent) -> Unit) {
    val colors = Chalkroom.colors
    ModalBottomSheet(
        onDismissRequest = { onEvent(UiEvent.CustomizeDismissed) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.page,
        // No dimming: the room behind is the preview.
        scrimColor = Color.Transparent,
    ) {
        val maxH = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.42f).toDp() }
        RoomSheetContent(room, custom, onEvent, Modifier.heightIn(max = maxH))
    }
}

@Composable
fun RoomSheetContent(room: RoomConfig, custom: Boolean, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    val type = Chalkroom.type
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        Text("Your classroom", style = type.title, modifier = Modifier.padding(horizontal = 20.dp))
        Section("Rooms")
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(RoomPreset.entries) { preset ->
                val picked = preset == room.preset
                PresetCard(preset, picked = picked, custom = picked && custom, onClick = { onEvent(UiEvent.PresetPicked(preset)) })
            }
        }

        Section("Board colour")
        SwatchRow {
            val shown = CustomizeRoom.shownBoard(room)
            BoardColor.entries.filter { it.swatch }.forEach { b ->
                Swatch(RoomPalette.board(b), "Board colour: ${b.label}", b == shown) { onEvent(UiEvent.BoardPicked(b)) }
            }
        }

        Section("Wall")
        SwatchRow {
            WallColor.entries.forEach { wall ->
                Swatch(RoomPalette.wall(wall).first, "Wall: ${wall.label}", wall == room.wall, shelves = wall == WallColor.SHELVES) {
                    onEvent(UiEvent.WallPicked(wall))
                }
            }
        }

        Section("Desks")
        ChipRow(DeskStyle.entries, room.desks, { it.label }, "Desks") { onEvent(UiEvent.DesksPicked(it)) }

        Section("Light")
        ChipRow(LightColor.entries.filter { it.swatch }, CustomizeRoom.shownLight(room), { it.label }, "Light") { onEvent(UiEvent.LightPicked(it)) }

        Section("Board motto")
        MottoField(room.motto) { onEvent(UiEvent.MottoChanged(it)) }

        TextButton("Reset to Classic", onClick = { onEvent(UiEvent.ResetTapped) }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = Chalkroom.type.label, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun SwatchRow(content: @Composable () -> Unit) {
    Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

/** A round colour in a 48dp target; the picked one wears an ink ring with a gap. */
@Composable
private fun Swatch(color: Color, label: String, picked: Boolean, shelves: Boolean = false, onClick: () -> Unit) {
    val colors = Chalkroom.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = picked
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(44.dp)) {
            val dp = density
            if (picked) drawCircle(colors.ink, size.minDimension / 2 - 1.25f * dp, style = Stroke(2.5f * dp))
            val r = 15f * dp
            drawCircle(color, r)
            if (shelves) {
                // Two little planks with books, so the shelves read as shelves.
                listOf(-0.15f, 0.42f).forEach { y ->
                    val py = center.y + y * r
                    listOf(0xFF7A3B2E, 0xFF3F5A44, 0xFF3D4A6B, 0xFF8C6A2F).forEachIndexed { i, c ->
                        drawRect(Color(c), Offset(center.x - 0.6f * r + i * 0.3f * r, py - 0.45f * r), Size(0.24f * r, 0.45f * r))
                    }
                    drawRect(colors.woodDeep, Offset(center.x - 0.8f * r, py), Size(1.6f * r, 0.1f * r))
                }
            }
            drawCircle(colors.ink, r, style = Stroke(1.5f * dp))
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, picked: T, label: (T) -> String, group: String, onPick: (T) -> Unit) {
    val colors = Chalkroom.colors
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option == picked
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) colors.ink else colors.card)
                    .border(1.5.dp, colors.ink, RoundedCornerShape(14.dp))
                    .clickable(role = Role.RadioButton) { onPick(option) }
                    .semantics {
                        contentDescription = "$group: ${label(option)}"
                        selected = on
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = Chalkroom.type.label.copy(color = if (on) colors.page else colors.ink))
            }
        }
    }
}

/** One line, up to [CustomizeRoom.MOTTO_MAX] characters, with a count. Empty: nothing on the board. */
@Composable
private fun MottoField(motto: String, onChange: (String) -> Unit) {
    val colors = Chalkroom.colors
    val type = Chalkroom.type
    Column(Modifier.padding(horizontal = 20.dp)) {
        BasicTextField(
            value = motto,
            onValueChange = onChange,
            singleLine = true,
            textStyle = type.body,
            cursorBrush = SolidColor(colors.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.card)
                .border(1.5.dp, colors.ink, RoundedCornerShape(14.dp))
                .semantics { contentDescription = "Board motto" },
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), contentAlignment = Alignment.CenterStart) {
                    if (motto.isEmpty()) Text("Write something on the board…", style = type.body.copy(color = colors.muted))
                    inner()
                }
            },
        )
        Text(
            "${motto.length}/${CustomizeRoom.MOTTO_MAX}",
            style = type.small,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 4.dp),
        )
    }
}

/** A preset as a little card: its room drawn small in its own colours, and its name (or "Custom"). */
@Composable
private fun PresetCard(preset: RoomPreset, picked: Boolean, custom: Boolean, onClick: () -> Unit) {
    val colors = Chalkroom.colors
    val name = if (custom) "Custom" else preset.label
    Column(
        Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(if (picked) 2.5.dp else 1.dp, if (picked) colors.ink else colors.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = if (custom) "${preset.label} room, customised" else "${preset.label} room"
                selected = picked
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoomThumbnail(RoomConfig(preset), Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(11.dp)))
        Spacer(Modifier.height(6.dp))
        Text(name, style = Chalkroom.type.small.copy(color = colors.ink, fontWeight = Chalkroom.type.label.fontWeight))
    }
}

/** The room in miniature: wall, board, floor, three desks, and the light over it. */
@Composable
fun RoomThumbnail(room: RoomConfig, modifier: Modifier = Modifier) {
    val ink = Chalkroom.colors.ink
    val roomColors = RoomPalette.roomColors(room)
    val deskColors = RoomPalette.deskColors(room)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val wallBottom = h * 0.55f
        drawRect(roomColors.wall, size = Size(w, wallBottom))
        if (room.wall == WallColor.SHELVES) {
            listOf(0.18f, 0.38f).forEach { y ->
                var x = 0f
                var i = 0
                while (x < w) {
                    val bw = w * (0.04f + (i % 3) * 0.01f)
                    drawRect(listOf(Color(0xFF7A3B2E), Color(0xFF3F5A44), Color(0xFF3D4A6B), Color(0xFF8C6A2F))[i % 4], Offset(x, h * y - h * 0.1f), Size(bw, h * 0.1f))
                    x += bw + w * 0.005f
                    i++
                }
                drawRect(roomColors.woodDeep, Offset(0f, h * y), Size(w, h * 0.02f))
            }
        }
        drawRect(roomColors.skirting, Offset(0f, wallBottom - h * 0.04f), Size(w, h * 0.04f))
        drawRect(roomColors.floor2, Offset(0f, wallBottom), Size(w, h - wallBottom))
        drawRect(roomColors.floor1, Offset(0f, wallBottom + (h - wallBottom) * 0.5f), Size(w, (h - wallBottom) * 0.5f))
        // Board with its frame.
        val boardTl = Offset(w * 0.2f, h * 0.08f)
        val board = Size(w * 0.6f, h * 0.34f)
        drawRoundRect(ink.copy(alpha = 0.8f), boardTl - Offset(2f, 2f), Size(board.width + 4f, board.height + 4f), CornerRadius(3f))
        drawRoundRect(RoomPalette.board(room.board), boardTl, board, CornerRadius(2f))
        drawLine(Color.White.copy(alpha = 0.7f), boardTl + Offset(board.width * 0.2f, board.height * 0.45f), boardTl + Offset(board.width * 0.7f, board.height * 0.45f), 1.5f * density)
        if (room.board.stars) {
            listOf(0.15f to 0.2f, 0.8f to 0.3f, 0.5f to 0.75f, 0.3f to 0.65f, 0.9f to 0.8f).forEach { (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.5f), 0.8f * density, boardTl + Offset(board.width * x, board.height * y))
            }
        }
        // Desks: two at the back, one in front.
        listOf(Triple(0.12f, 0.62f, 0.3f), Triple(0.58f, 0.62f, 0.3f), Triple(0.33f, 0.8f, 0.34f)).forEach { (x, y, dw) ->
            val tl = Offset(w * x, h * y)
            drawRect(deskColors.woodDark, tl + Offset(0f, h * 0.05f), Size(w * dw, h * 0.07f))
            drawRect(deskColors.wood, tl, Size(w * dw, h * 0.06f))
            drawRect(ink, tl, Size(w * dw, h * 0.12f), style = Stroke(1f * density))
            if (room.light.lampsOn) drawCircle(RoomPalette.lampGlow, h * 0.04f, tl + Offset(w * dw * 0.85f, -h * 0.03f))
        }
        drawRect(RoomPalette.light(room.light))
    }
}

@Preview(widthDp = 390, heightDp = 520)
@Composable
private fun RoomSheetPreview() {
    ChalkroomTheme {
        Box(Modifier.background(Chalkroom.colors.page)) {
            RoomSheetContent(
                RoomConfig(RoomPreset.LIBRARY).let { it.copy(values = it.values.copy(desks = DeskStyle.METAL), motto = "Mistakes welcome") },
                custom = true,
                onEvent = {},
            )
        }
    }
}
