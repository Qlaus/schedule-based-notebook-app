package io.github.qlaus.calnotes.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.core.layout.EventColumns
import io.github.qlaus.calnotes.core.layout.TimedItem
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.common.ThinDivider
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val HourHeight = 60.dp
private val GutterWidth = 52.dp

/** Day (one column) or week (seven columns) time grid with an all-day strip and overlap layout. */
@Composable
fun TimeGridView(
    days: List<LocalDate>,
    events: List<EventUi>,
    onEventClick: (EventUi) -> Unit,
    onSlotClick: (LocalDate, LocalTime) -> Unit,
    onDayHeaderClick: (LocalDate) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val allDay = remember(events) { events.filter { it.event.allDay } }
    val timed = remember(events) { events.filter { !it.event.allDay } }
    val allDayByDay = remember(allDay, days) { eventsByDay(allDay, days.first(), days.size, zone) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Day headers
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Spacer(Modifier.width(GutterWidth))
            days.forEach { day ->
                val isToday = day == today
                Column(
                    modifier = Modifier.weight(1f).clickable { onDayHeaderClick(day) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        Formats.weekdayShort(day.dayOfWeek),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        // All-day strip
        if (allDay.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    stringResource(R.string.all_day),
                    modifier = Modifier.width(GutterWidth).padding(end = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                days.forEach { day ->
                    Column(Modifier.weight(1f).padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        allDayByDay[day].orEmpty().forEach { e -> EventChip(e, onEventClick) }
                    }
                }
            }
        }
        ThinDivider()

        val scroll = rememberScrollState()
        val density = LocalDensity.current
        LaunchedEffect(days.first()) {
            // Start around 07:00 so the working day is visible.
            scroll.scrollTo(with(density) { (HourHeight * 7).roundToPx() })
        }
        Row(Modifier.fillMaxSize().verticalScroll(scroll)) {
            HourGutter()
            days.forEach { day ->
                DayColumn(
                    day = day,
                    events = timed,
                    zone = zone,
                    nowMillis = now,
                    isToday = day == today,
                    onEventClick = onEventClick,
                    onSlotClick = onSlotClick,
                    modifier = Modifier.weight(1f).height(HourHeight * 24),
                )
            }
        }
    }
}

@Composable
private fun HourGutter() {
    Column(Modifier.width(GutterWidth)) {
        for (hour in 0 until 24) {
            Box(Modifier.height(HourHeight).fillMaxWidth()) {
                if (hour > 0) {
                    Text(
                        "%02d:00".format(hour),
                        modifier = Modifier.align(Alignment.TopEnd).offset(y = (-8).dp).padding(end = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    events: List<EventUi>,
    zone: ZoneId,
    nowMillis: Long,
    isToday: Boolean,
    onEventClick: (EventUi) -> Unit,
    onSlotClick: (LocalDate, LocalTime) -> Unit,
    modifier: Modifier,
) {
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val nowColor = MaterialTheme.colorScheme.primary
    val placed = remember(events, dayStart) {
        val items = events
            .filter { it.event.startMillis < dayEnd && it.event.endMillis > dayStart }
            .map { e ->
                val s = maxOf(e.event.startMillis, dayStart)
                // Give zero/short events a minimum visual height of 20 minutes.
                val en = maxOf(minOf(e.event.endMillis, dayEnd), s + 20 * 60_000)
                TimedItem(e, s, en)
            }
        EventColumns.layout(items).map { p -> p to items.first { it.item === p.item } }
    }

    BoxWithConstraints(
        modifier = modifier
            .drawBehind {
                val hourPx = size.height / 24f
                for (h in 0..24) drawLine(lineColor, Offset(0f, h * hourPx), Offset(size.width, h * hourPx), 1f)
                drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), 1f)
            }
            .pointerInput(day) {
                detectTapGestures { offset ->
                    val hour = (offset.y / (size.height / 24f)).toInt().coerceIn(0, 23)
                    onSlotClick(day, LocalTime.of(hour, 0))
                }
            },
    ) {
        val columnWidth = maxWidth
        placed.forEach { (p, item) ->
            val top = minutesToDp(((item.start - dayStart) / 60_000).toInt())
            val height = minutesToDp(((item.end - item.start) / 60_000).toInt())
            val w = columnWidth / p.columnCount
            Box(
                Modifier
                    .offset(x = w * p.column, y = top)
                    .width(w)
                    .height(height)
                    .padding(horizontal = 1.dp, vertical = 1.dp),
            ) {
                TimedEventBlock(p.item, onEventClick)
            }
        }
        if (isToday && nowMillis in dayStart until dayEnd) {
            val y = minutesToDp(((nowMillis - dayStart) / 60_000).toInt())
            Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(2.dp).background(nowColor))
            Box(Modifier.offset(x = (-4).dp, y = y - 5.dp).size(10.dp).clip(CircleShape).background(nowColor))
        }
    }
}

private fun minutesToDp(minutes: Int): Dp = HourHeight * (minutes / 60f)

@Composable
private fun TimedEventBlock(e: EventUi, onClick: (EventUi) -> Unit) {
    Row(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(e.color.copy(alpha = 0.16f))
            .clickable { onClick(e) },
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(e.color))
        Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text(
                (if (e.hasNotes) "📝 " else "") + e.event.title.ifBlank { stringResource(R.string.no_title) },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                Formats.time(e.event.startMillis) + "–" + Formats.time(e.event.endMillis),
                fontSize = 11.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EventChip(e: EventUi, onClick: (EventUi) -> Unit) {
    Text(
        (if (e.hasNotes) "📝 " else "") + e.event.title.ifBlank { stringResource(R.string.no_title) },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(e.color.copy(alpha = 0.2f))
            .clickable { onClick(e) }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
