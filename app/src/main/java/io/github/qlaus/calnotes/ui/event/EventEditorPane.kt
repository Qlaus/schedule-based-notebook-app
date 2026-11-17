@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.qlaus.calnotes.ui.event

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.core.calendar.RepeatRule
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.common.PaneScaffold
import io.github.qlaus.calnotes.ui.common.ScopeChoiceDialog
import io.github.qlaus.calnotes.ui.common.ThinDivider
import io.github.qlaus.calnotes.ui.theme.Accent
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun EventEditorPane(
    calendarId: String?,
    eventId: String?,
    presetDate: LocalDate?,
    presetTime: LocalTime?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSaved: (calendarId: String, eventId: String) -> Unit,
) {
    val vm: EventEditorViewModel = hiltViewModel(key = "edit/$calendarId/$eventId/$presetDate/$presetTime")
    LaunchedEffect(Unit) { vm.load(calendarId, eventId, presetDate, presetTime) }
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var askScope by remember { mutableStateOf(false) }

    fun save(wholeSeries: Boolean) = scope.launch { vm.save(wholeSeries)?.let { (c, e) -> onSaved(c, e) } }

    PaneScaffold(
        canGoBack = canGoBack,
        onBack = onBack,
        actions = {
            Button(
                onClick = {
                    if (!state.isNew && state.isRecurring) {
                        askScope = true
                    } else {
                        save(false)
                    }
                },
                enabled = state.loaded,
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        val draft = state.draft
        if (!state.loaded || draft == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@PaneScaffold
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { v -> vm.update { it.copy(title = v) } },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.all_day), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = draft.allDay, onCheckedChange = { v -> vm.update { it.copy(allDay = v) } })
            }
            DateTimeRow(
                label = stringResource(R.string.field_starts),
                date = draft.startDate,
                time = draft.startTime,
                showTime = !draft.allDay,
                onDate = { d -> vm.update { it.copy(startDate = d) } },
                onTime = { t -> vm.update { it.copy(startTime = t) } },
            )
            DateTimeRow(
                label = stringResource(R.string.field_ends),
                date = draft.endDate,
                time = draft.endTime,
                showTime = !draft.allDay,
                onDate = { d -> vm.update { it.copy(endDate = d) } },
                onTime = { t -> vm.update { it.copy(endTime = t) } },
            )
            when (state.error) {
                EditorError.END_BEFORE_START -> Text(stringResource(R.string.error_end_before_start), color = MaterialTheme.colorScheme.error)
                EditorError.NO_CALENDAR -> Text(stringResource(R.string.error_no_calendar), color = MaterialTheme.colorScheme.error)
                null -> {}
            }
            ThinDivider()

            if (state.isNew) {
                Picker(
                    label = stringResource(R.string.field_calendar),
                    value = vm.calendarName(draft.calendarId),
                    dotColor = vm.calendarColorHex(draft.calendarId),
                    options = state.calendars.map { it.id to it.summary },
                    onSelect = { id -> vm.update { it.copy(calendarId = id) } },
                )
                Picker(
                    label = stringResource(R.string.field_repeat),
                    value = stringResource(repeatLabel(draft.repeat)),
                    options = RepeatRule.entries.map { it.name to stringResource(repeatLabel(it)) },
                    onSelect = { name -> vm.update { it.copy(repeat = RepeatRule.valueOf(name)) } },
                )
            } else if (state.isRecurring) {
                Text(stringResource(R.string.repeat_edit_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                value = draft.location,
                onValueChange = { v -> vm.update { it.copy(location = v) } },
                label = { Text(stringResource(R.string.field_location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { v -> vm.update { it.copy(description = v) } },
                label = { Text(stringResource(R.string.field_description)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(48.dp))
        }
    }

    if (askScope) {
        ScopeChoiceDialog(
            title = stringResource(R.string.scope_edit_title),
            instanceLabel = stringResource(R.string.scope_note_instance),
            seriesLabel = stringResource(R.string.scope_note_series),
            hint = stringResource(R.string.scope_edit_series_hint),
            onInstance = { askScope = false; save(false) },
            onSeries = { askScope = false; save(true) },
            onDismiss = { askScope = false },
        )
    }
}

private fun repeatLabel(rule: RepeatRule) = when (rule) {
    RepeatRule.NONE -> R.string.repeat_none
    RepeatRule.DAILY -> R.string.repeat_daily
    RepeatRule.WEEKLY -> R.string.repeat_weekly
    RepeatRule.MONTHLY -> R.string.repeat_monthly
    RepeatRule.YEARLY -> R.string.repeat_yearly
}

@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    showTime: Boolean,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { pickDate = true }) { Text(Formats.dateField(date)) }
        if (showTime) OutlinedButton(onClick = { pickTime = true }) { Text("%02d:%02d".format(time.hour, time.minute)) }
    }

    if (pickDate) {
        // DatePicker works in UTC midnight millis.
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { onTime(LocalTime.of(state.hour, state.minute)); pickTime = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun Picker(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    dotColor: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyLarge)
        Box {
            OutlinedButton(onClick = { open = true }) {
                if (dotColor != null) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Formats.parseColor(dotColor, Accent)))
                    Spacer(Modifier.size(8.dp))
                }
                Text(value)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); open = false })
                }
            }
        }
    }
}
