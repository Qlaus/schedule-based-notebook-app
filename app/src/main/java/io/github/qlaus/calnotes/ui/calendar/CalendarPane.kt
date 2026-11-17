@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.qlaus.calnotes.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.ui.nav.DetailTarget
import io.github.qlaus.calnotes.sync.SyncStatus
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.theme.Accent

@Composable
fun CalendarPane(
    viewModel: CalendarViewModel,
    onShow: (DetailTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode by viewModel.mode.collectAsState()
    val anchor by viewModel.anchor.collectAsState()
    val events by viewModel.events.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            CalendarHeader(viewModel)
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val onEvent: (EventUi) -> Unit = { onShow(DetailTarget.Event(it.event.calendarId, it.event.id)) }
                when (mode) {
                    CalendarMode.MONTH -> MonthView(anchor, events, onDayClick = viewModel::openDay, onEventClick = onEvent)
                    CalendarMode.WEEK -> TimeGridView(
                        days = (0 until 7).map { Formats.weekStart(anchor).plusDays(it.toLong()) },
                        events = events,
                        onEventClick = onEvent,
                        onSlotClick = { d, t -> onShow(DetailTarget.EditEvent(presetDate = d, presetTime = t)) },
                        onDayHeaderClick = viewModel::openDay,
                    )
                    CalendarMode.DAY -> TimeGridView(
                        days = listOf(anchor),
                        events = events,
                        onEventClick = onEvent,
                        onSlotClick = { d, t -> onShow(DetailTarget.EditEvent(presetDate = d, presetTime = t)) },
                        onDayHeaderClick = {},
                    )
                    CalendarMode.AGENDA -> AgendaView(anchor, events, onEventClick = onEvent)
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onShow(DetailTarget.EditEvent(presetDate = anchor)) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.new_event)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        )
    }
}

@Composable
private fun CalendarHeader(viewModel: CalendarViewModel) {
    val mode by viewModel.mode.collectAsState()
    val anchor by viewModel.anchor.collectAsState()
    val calendars by viewModel.calendars.collectAsState()
    val sync by viewModel.syncStatus.collectAsState()
    val pending by viewModel.pendingOps.collectAsState()
    val outboxError by viewModel.outboxError.collectAsState()
    var calendarsMenu by remember { mutableStateOf(false) }

    val title = when (mode) {
        CalendarMode.DAY -> Formats.dayLong(anchor)
        CalendarMode.WEEK -> Formats.weekRange(Formats.weekStart(anchor))
        CalendarMode.MONTH, CalendarMode.AGENDA -> Formats.monthYear(anchor)
    }

    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    syncLabel(sync, pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sync is SyncStatus.Failed || sync is SyncStatus.AuthError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::refresh) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh)) }
            Box {
                IconButton(onClick = { calendarsMenu = true }) { Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.calendars)) }
                DropdownMenu(expanded = calendarsMenu, onDismissRequest = { calendarsMenu = false }) {
                    calendars.forEach { cal ->
                        DropdownMenuItem(
                            text = { Text(cal.summary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Formats.parseColor(cal.colorHex, Accent)))
                            },
                            trailingIcon = {
                                Checkbox(checked = cal.visible, onCheckedChange = { viewModel.setCalendarVisible(cal.id, it) })
                            },
                            onClick = { viewModel.setCalendarVisible(cal.id, !cal.visible) },
                        )
                    }
                }
            }
        }
        outboxError?.let {
            TextButton(onClick = viewModel::dismissOutboxError) {
                Text(stringResource(R.string.outbox_rejected, it), color = MaterialTheme.colorScheme.error)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val modes = listOf(
                CalendarMode.DAY to R.string.mode_day,
                CalendarMode.WEEK to R.string.mode_week,
                CalendarMode.MONTH to R.string.mode_month,
                CalendarMode.AGENDA to R.string.mode_agenda,
            )
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f, fill = false)) {
                modes.forEachIndexed { index, (m, label) ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { viewModel.setMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        icon = {},
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.step(false) }) { Icon(Icons.Filled.ChevronLeft, stringResource(R.string.previous)) }
            OutlinedButton(onClick = viewModel::goToday) { Text(stringResource(R.string.today)) }
            IconButton(onClick = { viewModel.step(true) }) { Icon(Icons.Filled.ChevronRight, stringResource(R.string.next)) }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun syncLabel(sync: SyncStatus, pending: Int): String = when {
    sync is SyncStatus.Running -> stringResource(R.string.sync_running)
    sync is SyncStatus.Offline -> stringResource(R.string.sync_offline)
    sync is SyncStatus.AuthError -> stringResource(R.string.sync_auth)
    sync is SyncStatus.Failed -> stringResource(R.string.sync_failed, sync.message)
    pending > 0 -> stringResource(R.string.sync_pending, pending)
    else -> stringResource(R.string.sync_done)
}
