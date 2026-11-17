package io.github.qlaus.calnotes.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.common.ThinDivider
import java.time.LocalDate
import java.time.ZoneId

/** Chronological list of days that have events, with sticky day headers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgendaView(anchor: LocalDate, events: List<EventUi>, onEventClick: (EventUi) -> Unit) {
    val zone = ZoneId.systemDefault()
    val grouped = remember(events, anchor) {
        eventsByDay(events, anchor, CalendarViewModel.AGENDA_DAYS.toInt(), zone).toSortedMap()
    }
    val today = LocalDate.now()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        if (grouped.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.agenda_empty),
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        grouped.forEach { (day, dayEvents) ->
            stickyHeader(key = "h$day") {
                Text(
                    Formats.dayMedium(day).replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
            items(dayEvents, key = { "$day/${it.key}" }) { e ->
                AgendaRow(e, onEventClick)
                ThinDivider(Modifier.padding(start = 24.dp))
            }
        }
    }
}

@Composable
private fun AgendaRow(e: EventUi, onClick: (EventUi) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { onClick(e) }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(e.color))
        Column(Modifier.width(96.dp)) {
            if (e.event.allDay) {
                Text(stringResource(R.string.all_day), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(Formats.time(e.event.startMillis), style = MaterialTheme.typography.bodyMedium)
                Text(Formats.time(e.event.endMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                e.event.title.ifBlank { stringResource(R.string.no_title) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(e.event.location.ifBlank { null }, e.calendarName).joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (e.hasNotes) Text("📝")
    }
}
