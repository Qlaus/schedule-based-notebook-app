package io.github.qlaus.calnotes.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.common.ThinDivider
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Month grid (6 weeks × 7 days, Monday first) with up to three event chips per day. */
@Composable
fun MonthView(
    anchor: LocalDate,
    events: List<EventUi>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (EventUi) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val gridStart = CalendarViewModel.monthGridStart(anchor)
    val today = LocalDate.now()
    val byDay = remember(events, gridStart) { eventsByDay(events, gridStart, 42, zone) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowHeight = max((maxHeight - 32.dp) / 6, 96.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                DayOfWeek.values().forEach { dow ->
                    Text(
                        Formats.weekdayShort(dow),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ThinDivider()
            for (week in 0 until 6) {
                Row(Modifier.fillMaxWidth().height(rowHeight)) {
                    for (d in 0 until 7) {
                        val date = gridStart.plusDays((week * 7 + d).toLong())
                        DayCell(
                            date = date,
                            inMonth = date.month == anchor.month,
                            isToday = date == today,
                            events = byDay[date].orEmpty(),
                            onClick = { onDayClick(date) },
                            onEventClick = onEventClick,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                ThinDivider()
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    events: List<EventUi>,
    onClick: () -> Unit,
    onEventClick: (EventUi) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
        val visible = events.take(3)
        visible.forEach { e ->
            Text(
                text = (if (e.hasNotes) "📝 " else "") + e.event.title.ifBlank { stringResource(R.string.no_title) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(e.color.copy(alpha = 0.18f))
                    .clickable { onEventClick(e) }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (events.size > visible.size) {
            Text(
                stringResource(R.string.more_events, events.size - visible.size),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Groups events by every local day they touch, within [start, start + days). */
internal fun eventsByDay(events: List<EventUi>, start: LocalDate, days: Int, zone: ZoneId): Map<LocalDate, List<EventUi>> {
    val result = HashMap<LocalDate, MutableList<EventUi>>()
    val end = start.plusDays(days.toLong())
    for (e in events) {
        var day = Formats.date(e.event.startMillis, zone)
        // End is exclusive: an event ending exactly at midnight does not appear on the next day.
        val lastDay = Formats.date(maxOf(e.event.startMillis, e.event.endMillis - 1), zone)
        if (day.isBefore(start)) day = start
        while (!day.isAfter(lastDay) && day.isBefore(end)) {
            result.getOrPut(day) { mutableListOf() } += e
            day = day.plusDays(1)
        }
    }
    return result
}
