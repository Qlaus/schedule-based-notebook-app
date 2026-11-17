package io.github.qlaus.calnotes.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.core.calendar.GoogleEventColors
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.data.local.EventEntity
import io.github.qlaus.calnotes.ui.common.ConfirmDialog
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.common.PaneScaffold
import io.github.qlaus.calnotes.ui.common.ScopeChoiceDialog
import io.github.qlaus.calnotes.ui.common.ThinDivider
import io.github.qlaus.calnotes.ui.theme.Accent
import kotlinx.coroutines.launch

@Composable
fun EventDetailPane(
    calendarId: String,
    eventId: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenNote: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val vm: EventDetailViewModel = hiltViewModel(key = "event/$calendarId/$eventId")
    LaunchedEffect(calendarId, eventId) { vm.load(calendarId, eventId) }
    val state by vm.state.collectAsState()
    val notes by vm.notes.collectAsState()
    val scope = rememberCoroutineScope()

    var askNoteScope by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    val loaded = state as? EventDetailState.Loaded
    val event = loaded?.event?.event

    fun createNote(s: EventLinkScope) = scope.launch { vm.createNote(s)?.let(onOpenNote) }

    PaneScaffold(
        canGoBack = canGoBack,
        onBack = onBack,
        actions = {
            if (event?.canEdit == true) {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.event_edit)) }
                IconButton(onClick = { askDelete = true }) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.event_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        when (state) {
            EventDetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            EventDetailState.Missing -> Text(
                stringResource(R.string.event_not_found),
                modifier = Modifier.padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is EventDetailState.Loaded -> {
                val row = loaded!!.event
                val e = row.event
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(e.title.ifBlank { stringResource(R.string.no_title) }, style = MaterialTheme.typography.headlineLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val color = Formats.parseColor(GoogleEventColors.hexFor(e.colorId) ?: row.calendarColor, Accent)
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.size(8.dp))
                        Text(row.calendarName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (e.pendingSync) {
                            Text("  ·  " + stringResource(R.string.pending_sync), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    InfoRow(Icons.Outlined.Schedule, timeText(e))
                    if (e.isRecurring) InfoRow(Icons.Outlined.Repeat, stringResource(R.string.event_recurring))
                    if (e.location.isNotBlank()) InfoRow(Icons.Outlined.Place, e.location)
                    if (e.description.isNotBlank()) InfoRow(Icons.Outlined.Description, e.description)
                    if (!e.canEdit) {
                        Text(stringResource(R.string.event_read_only), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    ThinDivider()
                    Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleLarge)
                    if (notes.isEmpty()) {
                        Text(stringResource(R.string.no_notes_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    notes.forEach { note ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenNote(note.id) },
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(note.title.ifBlank { stringResource(R.string.no_title) }, style = MaterialTheme.typography.titleMedium)
                                if (note.body.isNotBlank()) {
                                    Text(note.body, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    val onCreate = {
                        if (e.isRecurring) {
                            askNoteScope = true
                        } else {
                            createNote(EventLinkScope.INSTANCE)
                        }
                    }
                    if (notes.isEmpty()) {
                        Button(onClick = { onCreate() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.create_note))
                        }
                    } else {
                        FilledTonalButton(onClick = { onCreate() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.large) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.add_another_note))
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }

    if (askNoteScope) {
        ScopeChoiceDialog(
            title = stringResource(R.string.scope_note_title),
            instanceLabel = stringResource(R.string.scope_note_instance),
            seriesLabel = stringResource(R.string.scope_note_series),
            onInstance = { askNoteScope = false; createNote(EventLinkScope.INSTANCE) },
            onSeries = { askNoteScope = false; createNote(EventLinkScope.SERIES) },
            onDismiss = { askNoteScope = false },
        )
    }
    if (askDelete && event != null) {
        if (event.isRecurring) {
            ScopeChoiceDialog(
                title = stringResource(R.string.scope_delete_title),
                instanceLabel = stringResource(R.string.scope_note_instance),
                seriesLabel = stringResource(R.string.scope_note_series),
                hint = stringResource(R.string.delete_event_body),
                onInstance = { askDelete = false; scope.launch { vm.delete(false); onDeleted() } },
                onSeries = { askDelete = false; scope.launch { vm.delete(true); onDeleted() } },
                onDismiss = { askDelete = false },
            )
        } else {
            ConfirmDialog(
                title = stringResource(R.string.delete_event_title),
                body = stringResource(R.string.delete_event_body),
                confirmLabel = stringResource(R.string.delete),
                onConfirm = { askDelete = false; scope.launch { vm.delete(false); onDeleted() } },
                onDismiss = { askDelete = false },
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun timeText(e: EventEntity): String {
    val startDay = Formats.date(e.startMillis)
    return if (e.allDay) {
        val lastDay = Formats.date(e.endMillis - 1)
        if (lastDay == startDay) Formats.dayLong(startDay) + " · " + stringResource(R.string.all_day)
        else Formats.dayLong(startDay) + " – " + Formats.dayLong(lastDay)
    } else {
        val endDay = Formats.date(e.endMillis)
        if (endDay == startDay) "${Formats.dayLong(startDay)}\n${Formats.time(e.startMillis)} – ${Formats.time(e.endMillis)}"
        else "${Formats.dayLong(startDay)} ${Formats.time(e.startMillis)} –\n${Formats.dayLong(endDay)} ${Formats.time(e.endMillis)}"
    }
}
