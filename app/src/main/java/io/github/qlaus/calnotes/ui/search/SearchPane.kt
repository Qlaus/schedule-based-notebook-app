package io.github.qlaus.calnotes.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.core.calendar.GoogleEventColors
import io.github.qlaus.calnotes.data.local.EventWithCalendar
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.common.ThinDivider
import io.github.qlaus.calnotes.ui.nav.DetailTarget
import io.github.qlaus.calnotes.ui.notebook.NoteRow
import io.github.qlaus.calnotes.ui.theme.Accent

@Composable
fun SearchPane(onShow: (DetailTarget) -> Unit, modifier: Modifier = Modifier) {
    val vm: SearchViewModel = hiltViewModel()
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Close, null) }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(24.dp).focusRequester(focus),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                query.isBlank() -> item { Hint(stringResource(R.string.search_start)) }
                results.query == query && results.notes.isEmpty() && results.events.isEmpty() ->
                    item { Hint(stringResource(R.string.search_no_results)) }
            }
            if (results.notes.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.search_notes)) }
                items(results.notes, key = { "n" + it.note.id }) { n -> NoteRow(n) { onShow(DetailTarget.Note(n.note.id)) } }
            }
            if (results.events.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.search_events)) }
                items(results.events, key = { "e" + it.event.calendarId + it.event.id }) { e ->
                    EventResultRow(e) { onShow(DetailTarget.Event(e.event.calendarId, e.event.id)) }
                    ThinDivider()
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun EventResultRow(e: EventWithCalendar, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val color = Formats.parseColor(GoogleEventColors.hexFor(e.event.colorId) ?: e.calendarColor, Accent)
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f)) {
            Text(e.event.title.ifBlank { stringResource(R.string.no_title) }, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val day = Formats.dayLong(Formats.date(e.event.startMillis))
            Text(
                if (e.event.allDay) day else day + " · " + Formats.time(e.event.startMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
