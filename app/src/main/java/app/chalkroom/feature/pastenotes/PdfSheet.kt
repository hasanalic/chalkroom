package app.chalkroom.feature.pastenotes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.chalkroom.data.pdf.Part
import app.chalkroom.data.pdf.PdfLimits
import app.chalkroom.data.pdf.PdfTitle
import app.chalkroom.feature.pastenotes.PasteNotesContract.PdfFile
import app.chalkroom.feature.pastenotes.PasteNotesContract.PdfProblem
import app.chalkroom.feature.pastenotes.PasteNotesContract.Sheet
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEvent
import app.chalkroom.ui.components.CardShape
import app.chalkroom.ui.components.Ledge
import app.chalkroom.ui.components.PrimaryButton
import app.chalkroom.ui.components.QuietButton
import app.chalkroom.ui.components.TextButton
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * The sheet of paper while a PDF is on it: the ruled lines and margin stay, the text field doesn't.
 * Opening and reading show an open book turning its pages; ready shows a closed notebook.
 */
@Composable
internal fun PdfOnSheet(
    sheet: Sheet.WithFile,
    studentName: String?,
    onEvent: (UiEvent) -> Unit,
    onChoosePdf: () -> Unit,
    lineHeight: TextUnit,
) {
    val reduced = Chalkroom.reducedMotion
    val haptics = LocalHapticFeedback.current

    // Arrival: content drops in, the name follows 80ms later, the meta line 150ms later. Replays per file.
    val drop = remember(sheet.file) { Animatable(if (reduced) 1f else 0f) }
    val name = remember(sheet.file) { Animatable(if (reduced) 1f else 0f) }
    val meta = remember(sheet.file) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(sheet.file) {
        if (!reduced) {
            launch { delay(80); name.animateTo(1f, tween(ARRIVE_MS, easing = EaseOut)) }
            launch { delay(150); meta.animateTo(1f, tween(ARRIVE_MS, easing = EaseOut)) }
            drop.animateTo(1f, tween(ARRIVE_MS, easing = EaseOut))
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // One Confirm, only on the reading → ready step.
    var wasReading by remember { mutableStateOf(false) }
    LaunchedEffect(sheet::class) {
        if (wasReading && sheet is Sheet.Ready) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        wasReading = sheet is Sheet.Reading
    }

    Column(Modifier.fillMaxSize().ruledPaper(lineHeight).testTag("pdfSheet")) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(start = SHEET_PAD, end = 16.dp, top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val dropModifier = Modifier.graphicsLayer {
                    alpha = drop.value
                    translationY = -DROP.toPx() * (1f - drop.value)
                }
                val nameModifier = Modifier.graphicsLayer { alpha = name.value }
                val metaModifier = Modifier.graphicsLayer { alpha = meta.value }
                when (sheet) {
                    is Sheet.Failed -> FailedFile(sheet, studentName, onEvent, onChoosePdf, dropModifier, nameModifier, metaModifier)
                    else -> {
                        BookOrNotebook(sheet, reduced, dropModifier)
                        val title = sheet.file.title
                        if (sheet !is Sheet.Ready && title != null) {
                            Text(
                                title,
                                style = Chalkroom.type.label,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = nameModifier.padding(top = 18.dp),
                            )
                        }
                        Column(metaModifier.padding(top = if (sheet is Sheet.Ready) 18.dp else 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Pill(pillText(sheet, studentName))
                            Spacer(Modifier.height(10.dp))
                            if (sheet is Sheet.Ready) {
                                QuietButton("Choose another PDF", onChoosePdf, small = true, modifier = Modifier.testTag("anotherPdf"))
                            } else {
                                TextButton("Stop", { onEvent(UiEvent.DropFile) }, Modifier.testTag("stop"))
                            }
                        }
                    }
                }
            }
        }
        TextButton(
            "Paste text instead",
            { onEvent(UiEvent.DropFile) },
            Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp).testTag("pasteInstead"),
        )
    }
}

internal fun pillText(sheet: Sheet.WithFile, studentName: String?): String = when (sheet) {
    is Sheet.Reading -> "Reading page ${(sheet.done + 1).coerceAtMost(sheet.total)} of ${sheet.total}"
    is Sheet.Ready -> if (sheet.pageCount == 1) "1 page · PDF" else "${sheet.pageCount} pages · PDF"
    else -> studentName?.let { "$it is opening it…" } ?: "Opening it…"
}

/** The open book while opening and reading; it becomes the closed notebook when ready. */
@Composable
private fun BookOrNotebook(sheet: Sheet.WithFile, reduced: Boolean, modifier: Modifier) {
    val done = (sheet as? Sheet.Reading)?.done ?: 0
    AnimatedContent(
        targetState = sheet is Sheet.Ready,
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(tween(NOTEBOOK_IN_MS)) + scaleIn(tween(NOTEBOOK_IN_MS), initialScale = 0.96f)) togetherWith
                    fadeOut(tween(NOTEBOOK_IN_MS / 2))
            } using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.Center,
        modifier = modifier,
        label = "book",
    ) { ready ->
        if (ready) Notebook((sheet as? Sheet.Ready)?.file?.title ?: PdfTitle.UNTITLED) else OpenBook(done, reduced)
    }
}

/**
 * A gold cover with two cream pages. For every page read the right page lifts and turns over the
 * spine. Turns are coalesced: a fast reader never builds a backlog, and the number under the book
 * is always the real one.
 */
@Composable
private fun OpenBook(done: Int, reduced: Boolean) {
    val colors = Chalkroom.colors
    val turn = remember { Animatable(0f) }
    val doneNow by rememberUpdatedState(done)
    if (!reduced) {
        LaunchedEffect(Unit) {
            var shown = doneNow
            snapshotFlow { doneNow }.conflate().collect { d ->
                if (d > shown) {
                    turn.snapTo(0f)
                    turn.animateTo(1f, tween(TURN_MS, easing = FastOutSlowInEasing))
                    turn.snapTo(0f)
                }
                shown = d
            }
        }
    }
    Ledge(shape = RoundedCornerShape(10.dp), color = colors.gold, ledgeColor = colors.goldLedge, depth = 4.dp, modifier = Modifier.testTag("openBook")) {
        Box(Modifier.padding(COVER)) {
            Row {
                BookPage()
                BookPage()
            }
            Box(Modifier.align(Alignment.Center).width(2.dp).height(PAGE_H).background(colors.goldLedge))
            Box(
                Modifier
                    .offset(x = PAGE_W)
                    .graphicsLayer {
                        val t = turn.value
                        alpha = if (t > 0f && t < 1f) 1f else 0f
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -180f * t
                        cameraDistance = 12f * density
                    },
            ) {
                BookPage(shade = { 0.18f * sin(PI.toFloat() * turn.value) })
            }
        }
    }
}

@Composable
private fun BookPage(shade: (() -> Float)? = null) {
    val colors = Chalkroom.colors
    Box(
        Modifier
            .size(PAGE_W, PAGE_H)
            .background(colors.wall)
            .drawWithContent {
                val stroke = 2.dp.toPx()
                val left = 12.dp.toPx()
                val right = size.width - 12.dp.toPx()
                val step = 16.dp.toPx()
                repeat(6) { i ->
                    val y = 24.dp.toPx() + i * step
                    val end = if (i == 5) left + (right - left) * 0.55f else right
                    drawLine(colors.ink.copy(alpha = 0.16f), Offset(left, y), Offset(end, y), stroke, StrokeCap.Round)
                }
                shade?.let { drawRect(colors.ink.copy(alpha = it().coerceIn(0f, 1f))) }
            },
    )
}

/** The read file, closed: a gold cover with a spine band and a cream label carrying its title. */
@Composable
private fun Notebook(title: String) {
    val colors = Chalkroom.colors
    Ledge(
        shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 16.dp, bottomEnd = 16.dp),
        color = colors.gold,
        ledgeColor = colors.goldLedge,
        depth = 4.dp,
        modifier = Modifier.width(NOTEBOOK_W).testTag("notebook"),
    ) {
        Box(Modifier.fillMaxWidth().height(NOTEBOOK_H)) {
            Box(Modifier.fillMaxHeight().width(10.dp).background(colors.goldLedge))
            Box(
                Modifier
                    .padding(start = 26.dp, end = 16.dp, top = 30.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.wall)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                LabelTitle(title)
            }
        }
    }
}

/** Breaks only between words: the size steps down until the longest word fits the label on one line. */
@Composable
private fun LabelTitle(title: String) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val base = Chalkroom.type.title.copy(hyphens = Hyphens.None, lineBreak = LineBreak.Heading)
        val longest = remember(title) { title.split(Regex("\\s+")).maxByOrNull { it.length }.orEmpty() }
        val width = constraints.maxWidth
        val size = remember(longest, width, base) {
            LABEL_SIZES.firstOrNull { sp ->
                measurer.measure(longest, base.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width <= width
            } ?: LABEL_SIZES.last()
        }
        Text(
            title,
            style = base.copy(fontSize = size.sp, lineHeight = (size + 5).sp),
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FailedFile(
    sheet: Sheet.Failed,
    studentName: String?,
    onEvent: (UiEvent) -> Unit,
    onChoosePdf: () -> Unit,
    dropModifier: Modifier,
    nameModifier: Modifier,
    metaModifier: Modifier,
) {
    val type = Chalkroom.type
    Column(dropModifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        sheet.file.title?.let {
            Text(it, style = type.label.copy(color = Chalkroom.colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
        }
    }
    Text(
        problemLine(sheet.problem, studentName),
        style = type.body,
        textAlign = TextAlign.Center,
        modifier = nameModifier.padding(horizontal = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("pdfError"),
    )
    Row(metaModifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (sheet.problem.retry) PrimaryButton("Try again", { onEvent(UiEvent.Retry) }, small = true)
        QuietButton("Choose another PDF", onChoosePdf, small = true, modifier = Modifier.testTag("anotherPdf"))
    }
}

/** A small rounded pill in a pale gold. Read out politely when it changes. */
@Composable
private fun Pill(text: String) {
    val colors = Chalkroom.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.gold.copy(alpha = 0.28f).compositeOver(colors.card))
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("pill"),
    ) {
        Text(text, style = Chalkroom.type.small.copy(color = colors.ink, fontWeight = Chalkroom.type.label.fontWeight))
    }
}

/** The PDF is too long: which pages should be read? Nothing is read until a range is picked. */
@Composable
internal fun PagesDialog(pageCount: Int, studentName: String?, onRead: (Int, Int) -> Unit, onNeverMind: () -> Unit) {
    val colors = Chalkroom.colors
    val type = Chalkroom.type
    var from by rememberSaveable { mutableStateOf("1") }
    var to by rememberSaveable { mutableStateOf("${minOf(PdfLimits.MAX_PAGES, pageCount)}") }
    val valid = validRange(from.toIntOrNull(), to.toIntOrNull(), pageCount)
    Dialog(onDismissRequest = onNeverMind, properties = DialogProperties(dismissOnClickOutside = false)) {
        Ledge(shape = CardShape, color = colors.page, ledgeColor = colors.lavender2, depth = 3.dp, border = colors.line, borderWidth = 1.5.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
                val who = studentName?.let { "should $it read" } ?: "should be read"
                Text("That's ${count(pageCount)} pages. Which ones $who?", style = type.title)
                Text("Up to ${PdfLimits.MAX_PAGES} pages at a time.", style = type.body.copy(color = colors.muted), modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.Bottom) {
                    PageField("From", from, { from = it }, Modifier.weight(1f).testTag("fromPage"))
                    Text("–", style = type.title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
                    PageField("To", to, { to = it }, Modifier.weight(1f).testTag("toPage"))
                }
                PrimaryButton(
                    "Read these",
                    { if (valid) onRead(from.toInt(), to.toInt()) },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp).testTag("readThese"),
                )
                TextButton("Never mind", onNeverMind, Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
            }
        }
    }
}

/** Digits only, at most five; anything else is refused as it's typed. */
@Composable
private fun PageField(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier) {
    val colors = Chalkroom.colors
    val type = Chalkroom.type
    Column(modifier) {
        Text(label, style = type.small, modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) onValue(it) },
            singleLine = true,
            textStyle = type.title.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(colors.ink),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.card)
                .border(1.5.dp, colors.line, RoundedCornerShape(12.dp))
                .padding(vertical = 10.dp, horizontal = 8.dp),
        )
    }
}

/** The sheet's faint ruled lines and its margin line, shared by the editor and the file. */
internal fun Modifier.ruledPaper(lineHeight: TextUnit, line: Color, margin: Color): Modifier = drawBehind {
    val step = lineHeight.toPx()
    var y = SHEET_PAD.toPx() + step
    while (y < size.height - 8.dp.toPx()) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += step
    }
    val x = SHEET_PAD.toPx() - 6.dp.toPx()
    drawLine(margin, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
}

@Composable
internal fun Modifier.ruledPaper(lineHeight: TextUnit): Modifier {
    val colors = Chalkroom.colors
    return ruledPaper(lineHeight, colors.lavender2.copy(alpha = 0.45f), colors.coral.copy(alpha = 0.35f))
}

internal val SHEET_PAD: Dp = 22.dp
private val DROP = 14.dp
private val COVER = 8.dp
private val PAGE_W = 92.dp
private val PAGE_H = 124.dp
private val NOTEBOOK_W = 200.dp
private val NOTEBOOK_H = 236.dp
private const val ARRIVE_MS = 250
private const val TURN_MS = 220
private const val NOTEBOOK_IN_MS = 230
private val LABEL_SIZES = listOf(19, 18, 17, 16, 15, 14, 13)

@Preview(widthDp = 390, heightDp = 520)
@Composable
private fun ReadingPreview() {
    ChalkroomTheme {
        Box(Modifier.background(Chalkroom.colors.card)) {
            PdfOnSheet(Sheet.Reading(PdfFile("x", "Cell Biology — Week 4"), done = 2, total = 12), null, {}, {}, 22.sp)
        }
    }
}

@Preview(widthDp = 390, heightDp = 520)
@Composable
private fun ReadyPreview() {
    ChalkroomTheme {
        Box(Modifier.background(Chalkroom.colors.card)) {
            PdfOnSheet(Sheet.Ready(PdfFile("x", "Cell Biology — Week 4"), 12, listOf(Part(1, "text", "Cells", 1, 12))), null, {}, {}, 22.sp)
        }
    }
}

@Preview(widthDp = 390, heightDp = 520)
@Composable
private fun FailedPreview() {
    ChalkroomTheme {
        Box(Modifier.background(Chalkroom.colors.card)) {
            PdfOnSheet(Sheet.Failed(PdfFile("x", "Scan"), PdfProblem.NO_WORDS), "Mira", {}, {}, 22.sp)
        }
    }
}
