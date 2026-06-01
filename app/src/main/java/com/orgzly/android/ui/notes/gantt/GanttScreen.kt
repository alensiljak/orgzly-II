package com.orgzly.android.ui.notes.gantt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.alensiljak.orgzly.R
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.painterIcon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import androidx.compose.material.icons.Icons as MIcons
import androidx.compose.material.icons.filled.Today
import kotlin.math.max

private val MAX_TITLE_WIDTH = 160.dp
private val MIN_TITLE_WIDTH = 60.dp
private const val ROW_HEIGHT_DP = 40f
private const val AXIS_HEIGHT_DP = 32f
private const val MIN_SCALE = 0.2f
private const val MAX_SCALE = 8f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GanttScreen(
    viewModel: GanttViewModel,
    onNavigateUp: () -> Unit,
) {
    val allItems by viewModel.items.observeAsState(emptyList())
    val showUnscheduled by viewModel.showUnscheduled.observeAsState(true)

    val items = if (showUnscheduled) allItems else allItems.filter {
        it.scheduledMs != null || it.deadlineMs != null
    }

    val tz = TimeZone.getDefault()
    val todayMs = remember {
        Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val (rangeStartMs, rangeEndMs) = remember(items) {
        val dates = items.flatMap { listOfNotNull(it.scheduledMs, it.deadlineMs) }
        if (dates.isEmpty()) (todayMs - 7 * 86_400_000L) to (todayMs + 23 * 86_400_000L)
        else (dates.min() - 2 * 86_400_000L) to (dates.max() + 2 * 86_400_000L)
    }
    val rangeDays = ((rangeEndMs - rangeStartMs) / 86_400_000.0).toFloat().coerceAtLeast(1f)

    var scaleX by rememberSaveable { mutableStateOf(1f) }
    var offsetXPx by rememberSaveable { mutableStateOf(0f) }

    // Returns offsetXPx clamped so the date range can't scroll off-screen.
    // barWidthPx: the pixel width of the chart area (not title column).
    fun clampOffset(offset: Float, scale: Float, barWidthPx: Float): Float {
        val totalWidth = barWidthPx * scale
        val minOffset = -(totalWidth - barWidthPx).coerceAtLeast(0f)
        return offset.coerceIn(minOffset, 0f)
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = TextStyle(fontSize = 13.sp)

    val titleColumnWidth: Dp = remember(items) {
        val maxPx = items.maxOfOrNull { item ->
            val indentPx = with(density) { ((item.level - 1) * 12).dp.toPx() }
            val treeLinePx = if (item.level > 1) with(density) { 8.dp.toPx() } else 0f
            val paddingPx = with(density) { 8.dp.toPx() }  // start + end padding
            val textPx = textMeasurer.measure(item.title, titleStyle).size.width.toFloat()
            indentPx + treeLinePx + paddingPx + textPx
        } ?: 0f
        with(density) { maxPx.toDp() }.coerceIn(MIN_TITLE_WIDTH, MAX_TITLE_WIDTH)
    }

    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface      = MaterialTheme.colorScheme.onSurface
    val outlineColor   = MaterialTheme.colorScheme.outline

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gantt") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(painterIcon(Icons.ARROW_BACK), contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Reset to default view (scale=1, today near centre)
                        scaleX = 1f
                        offsetXPx = 0f
                    }) {
                        Icon(MIcons.Default.Today, contentDescription = "Jump to today")
                    }
                    IconButton(onClick = { viewModel.toggleShowUnscheduled() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_visibility),
                            contentDescription = if (showUnscheduled) "Hide undated" else "Show undated",
                            tint = if (showUnscheduled) onSurface else onSurface.copy(alpha = 0.38f),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        // BoxWithConstraints gives us a real bounded width to work with.
        // Never use fillMaxSize/fillMaxWidth inside a scroll container — it gets
        // infinite constraints and crashes Compose's composition local resolution.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val barWidth: Dp = maxWidth - titleColumnWidth
            val barWidthPx = with(density) { barWidth.toPx() }

            // Gesture modifier shared by header and all rows — updates the same state.
            // Pinch zoom keeps the centroid (pan centroid x) fixed on screen.
            val gestureModifier = Modifier.pointerInput(barWidthPx) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scaleX * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    // Adjust offset so the point under the centroid stays fixed after zoom.
                    val newOffset = (offsetXPx - centroid.x) * (newScale / scaleX) + centroid.x + pan.x
                    scaleX = newScale
                    offsetXPx = clampOffset(newOffset, newScale, barWidthPx)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {

                // ── X-axis header ─────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AXIS_HEIGHT_DP.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.width(titleColumnWidth).fillMaxHeight().background(MaterialTheme.colorScheme.surface))
                    // Explicit width — never fillMaxWidth inside a gesture/scroll area
                    Canvas(
                        modifier = Modifier
                            .width(barWidth)
                            .fillMaxHeight()
                            .then(gestureModifier),
                    ) {
                        drawAxisHeader(rangeStartMs, rangeEndMs, rangeDays, scaleX, offsetXPx, tz, outlineColor, onSurface, textMeasurer)
                    }
                }

                // ── Rows ──────────────────────────────────────────────────
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(items) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ROW_HEIGHT_DP.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TitleCell(item, items, index, titleColumnWidth, onSurface, outlineColor)

                            Canvas(
                                modifier = Modifier
                                    .width(barWidth)
                                    .fillMaxHeight()
                                    .then(gestureModifier),
                            ) {
                                clipRect {
                                    if (index % 2 == 1) drawRect(surfaceVariant.copy(alpha = 0.4f))
                                    drawGrid(rangeStartMs, rangeEndMs, rangeDays, scaleX, offsetXPx, tz, outlineColor, onSurface, textMeasurer)
                                    val todayX = xOf(todayMs, rangeStartMs, rangeDays, scaleX, offsetXPx)
                                    if (todayX in 0f..size.width) {
                                        drawLine(Color.Red.copy(alpha = 0.7f), Offset(todayX, 0f), Offset(todayX, size.height), 2.dp.toPx())
                                    }
                                    drawBar(item, rangeStartMs, rangeDays, scaleX, offsetXPx, primaryColor, secondaryColor, todayMs)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleCell(
    item: GanttItem,
    items: List<GanttItem>,
    index: Int,
    titleWidth: Dp,
    textColor: Color,
    lineColor: Color,
) {
    val indentDp = ((item.level - 1) * 12).dp
    val hasPrev  = index > 0 && items[index - 1].level >= item.level
    val hasChild = index + 1 < items.size && items[index + 1].level > item.level

    Row(
        modifier = Modifier
            .width(titleWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = indentDp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.level > 1) {
            Canvas(modifier = Modifier.width(8.dp).fillMaxHeight()) {
                val midY = size.height / 2f
                drawLine(lineColor.copy(alpha = 0.5f), Offset(0f, midY), Offset(size.width, midY), 1.dp.toPx())
                if (hasPrev || hasChild) {
                    drawLine(lineColor.copy(alpha = 0.35f), Offset(0f, 0f), Offset(0f, midY), 1.dp.toPx())
                }
            }
        }
        Text(
            text = item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = textColor,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

// ── Time-scale tier ──────────────────────────────────────────────────────────

private enum class TimeTier { YEARS, MONTHS, WEEKS, DAYS }

private fun timeTier(pixelsPerDay: Float): TimeTier = when {
    pixelsPerDay < 3f  -> TimeTier.YEARS
    pixelsPerDay < 15f -> TimeTier.MONTHS
    pixelsPerDay < 60f -> TimeTier.WEEKS
    else               -> TimeTier.DAYS
}

/** Returns (primaryMs list, secondaryMs list) for the visible range. */
private fun buildTicks(
    rangeStartMs: Long, rangeEndMs: Long, tz: TimeZone, tier: TimeTier,
): Pair<List<Long>, List<Long>> {
    val lookahead = 366 * 86_400_000L  // generous overrun for partial periods
    val end = rangeEndMs + lookahead

    fun collect(start: Long, truncate: (Long) -> Long, advance: (Long) -> Long): List<Long> {
        val list = mutableListOf<Long>()
        var cur = truncate(start)
        while (cur <= end) { list += cur; cur = advance(cur) }
        return list
    }

    return when (tier) {
        TimeTier.YEARS -> {
            val primary   = collect(rangeStartMs, { truncateToYear(it, tz) },  { nextYear(it, tz) })
            val secondary = collect(rangeStartMs, { truncateToMonth(it, tz) }, { nextMonth(it, tz) })
            primary to secondary
        }
        TimeTier.MONTHS -> {
            val primary   = collect(rangeStartMs, { truncateToMonth(it, tz) }, { nextMonth(it, tz) })
            val secondary = collect(rangeStartMs, { truncateToWeek(it, tz) },  { nextWeek(it, tz) })
            primary to secondary
        }
        TimeTier.WEEKS -> {
            val primary   = collect(rangeStartMs, { truncateToWeek(it, tz) },  { nextWeek(it, tz) })
            val secondary = collect(rangeStartMs, { truncateToDay(it, tz) },   { nextDay(it) })
            primary to secondary
        }
        TimeTier.DAYS -> {
            val primary   = collect(rangeStartMs, { truncateToDay(it, tz) },   { nextDay(it) })
            primary to emptyList()
        }
    }
}

private fun labelFor(ms: Long, tz: TimeZone, tier: TimeTier): String {
    val cal = Calendar.getInstance(tz).apply { timeInMillis = ms }
    return when (tier) {
        TimeTier.YEARS  -> "${cal.get(Calendar.YEAR)}"
        TimeTier.MONTHS -> SimpleDateFormat("MMM yyyy", Locale.getDefault()).apply { this.timeZone = tz }.format(ms)
        TimeTier.WEEKS  -> {
            val fmt = SimpleDateFormat("MMM d", Locale.getDefault()).apply { this.timeZone = tz }
            "W${cal.get(Calendar.WEEK_OF_YEAR)} ${fmt.format(ms)}"
        }
        TimeTier.DAYS   -> SimpleDateFormat("d MMM", Locale.getDefault()).apply { this.timeZone = tz }.format(ms)
    }
}

// ── Drawing ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawAxisHeader(
    rangeStartMs: Long, rangeEndMs: Long, rangeDays: Float,
    scaleX: Float, offsetXPx: Float, tz: TimeZone,
    gridColor: Color, labelColor: Color, textMeasurer: TextMeasurer,
) {
    val pixelsPerDay = size.width / rangeDays * scaleX
    val tier = timeTier(pixelsPerDay)
    val (primary, secondary) = buildTicks(rangeStartMs, rangeEndMs, tz, tier)

    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val midY = size.height / 2f

    // Secondary ticks — short marks in the bottom half
    for (ms in secondary) {
        val x = xOf(ms, rangeStartMs, rangeDays, scaleX, offsetXPx)
        if (x in -1f..size.width + 1f)
            drawLine(gridColor.copy(alpha = 0.4f), Offset(x, midY), Offset(x, size.height), 1f)
    }

    // Primary ticks + labels
    val primarySorted = primary.sorted()
    for (i in primarySorted.indices) {
        val ms   = primarySorted[i]
        val x    = xOf(ms, rangeStartMs, rangeDays, scaleX, offsetXPx)
        val xEnd = if (i + 1 < primarySorted.size)
            xOf(primarySorted[i + 1], rangeStartMs, rangeDays, scaleX, offsetXPx)
        else
            size.width + 200f

        // Full-height tick at the primary boundary
        if (x in -1f..size.width + 1f)
            drawLine(gridColor.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, size.height), 1.5f)

        // Label — draw only if the slot is at least wide enough to show something
        val slotWidth = xEnd - x
        if (slotWidth > 20f) {
            val label  = labelFor(ms, tz, tier)
            val layout: TextLayoutResult = textMeasurer.measure(label, labelStyle)
            val tw     = layout.size.width.toFloat()
            // Start just after the tick; clip to slot so it never overlaps the next tick
            val labelX = (x + 3f).coerceAtMost(xEnd - tw - 2f)
            if (labelX + tw > 0f && labelX < size.width) {
                drawText(layout, topLeft = Offset(labelX, (midY - layout.size.height) / 2f))
            }
        }
    }
}

private fun DrawScope.drawGrid(
    rangeStartMs: Long, rangeEndMs: Long, rangeDays: Float,
    scaleX: Float, offsetXPx: Float, tz: TimeZone,
    gridColor: Color, @Suppress("UNUSED_PARAMETER") labelColor: Color,
    @Suppress("UNUSED_PARAMETER") textMeasurer: TextMeasurer,
) {
    val pixelsPerDay = size.width / rangeDays * scaleX
    val tier = timeTier(pixelsPerDay)
    val (primary, secondary) = buildTicks(rangeStartMs, rangeEndMs, tz, tier)

    for (ms in secondary) {
        val x = xOf(ms, rangeStartMs, rangeDays, scaleX, offsetXPx)
        if (x in 0f..size.width)
            drawLine(gridColor.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, size.height), 1f)
    }
    for (ms in primary) {
        val x = xOf(ms, rangeStartMs, rangeDays, scaleX, offsetXPx)
        if (x in 0f..size.width)
            drawLine(gridColor.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height), 1f)
    }
}

private fun DrawScope.drawBar(
    item: GanttItem,
    rangeStartMs: Long, rangeDays: Float, scaleX: Float, offsetXPx: Float,
    primaryColor: Color, secondaryColor: Color, todayMs: Long,
) {
    val h   = size.height * 0.45f
    val top = (size.height - h) / 2f

    val s = item.scheduledMs
    val d = item.deadlineMs

    when {
        s != null && d != null -> {
            val x1 = xOf(s, rangeStartMs, rangeDays, scaleX, offsetXPx)
            val x2 = xOf(d, rangeStartMs, rangeDays, scaleX, offsetXPx)
            drawRect(
                color = if (d < todayMs) Color(0xFFD32F2F) else primaryColor,
                topLeft = Offset(x1, top),
                size = Size(max(4f, x2 - x1), h),
            )
        }
        s != null -> {
            val x = xOf(s, rangeStartMs, rangeDays, scaleX, offsetXPx)
            drawCircle(primaryColor, h / 2.5f, Offset(x, size.height / 2f))
        }
        d != null -> {
            val x  = xOf(d, rangeStartMs, rangeDays, scaleX, offsetXPx)
            val r  = h / 2.5f
            val cy = size.height / 2f
            drawPath(
                path = Path().apply {
                    moveTo(x, cy - r); lineTo(x + r, cy)
                    lineTo(x, cy + r); lineTo(x - r, cy); close()
                },
                color = if (d < todayMs) Color(0xFFD32F2F) else secondaryColor,
            )
        }
    }
}

private fun DrawScope.xOf(ms: Long, rangeStartMs: Long, rangeDays: Float, scaleX: Float, offsetXPx: Float): Float =
    ((ms - rangeStartMs) / (rangeDays * 86_400_000.0) * size.width * scaleX + offsetXPx).toFloat()

// ── Date helpers ─────────────────────────────────────────────────────────────

private fun truncateToYear(ms: Long, tz: TimeZone): Long =
    Calendar.getInstance(tz).apply {
        timeInMillis = ms
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun nextYear(ms: Long, tz: TimeZone): Long =
    Calendar.getInstance(tz).apply { timeInMillis = ms; add(Calendar.YEAR, 1) }.timeInMillis

private fun truncateToMonth(ms: Long, tz: TimeZone): Long =
    Calendar.getInstance(tz).apply {
        timeInMillis = ms
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun nextMonth(ms: Long, tz: TimeZone): Long =
    Calendar.getInstance(tz).apply { timeInMillis = ms; add(Calendar.MONTH, 1) }.timeInMillis

private fun truncateToWeek(ms: Long, tz: TimeZone): Long =
    Calendar.getInstance(tz).apply {
        timeInMillis = ms
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun nextWeek(ms: Long, tz: TimeZone): Long =
    Calendar.getInstance(tz).apply { timeInMillis = ms; add(Calendar.WEEK_OF_YEAR, 1) }.timeInMillis

private fun truncateToDay(ms: Long, tz: TimeZone): Long =
    Calendar.getInstance(tz).apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun nextDay(ms: Long): Long = ms + 86_400_000L
