package io.github.qlaus.calnotes.ui.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.data.local.NotebookWithCount
import io.github.qlaus.calnotes.data.repo.NoteWithLinks
import io.github.qlaus.calnotes.ui.common.ConfirmDialog
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.nav.NotebookSelection
import io.github.qlaus.calnotes.ui.theme.Accent
import kotlinx.coroutines.launch

@Composable
fun NotebooksPane(
    selection: NotebookSelection,
    onSelect: (NotebookSelection) -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: NotebooksViewModel = hiltViewModel()
    LaunchedEffect(selection) { vm.setSelection(selection) }
    val scope = rememberCoroutineScope()

    Box(modifier) {
        when (selection) {
            NotebookSelection.All -> NotebookList(vm, onSelect)
            else -> NotebookNotes(vm, selection, onBack = { onSelect(NotebookSelection.All) }, onOpenNote = onOpenNote)
        }
        if (selection != NotebookSelection.All) {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { onOpenNote(vm.createNote()) } },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_note)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            )
        }
    }
}

@Composable
private fun NotebookList(vm: NotebooksViewModel, onSelect: (NotebookSelection) -> Unit) {
    val notebooks by vm.notebooks.collectAsState()
    val unfiled by vm.unfiledCount.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notebooks_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, stringResource(R.string.notebook_new)) }
            }
        }
        if (notebooks.isEmpty()) {
            item { Text(stringResource(R.string.notebooks_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(notebooks, key = { it.notebook.id }) { nb -> NotebookCard(nb) { onSelect(NotebookSelection.One(nb.notebook.id)) } }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(NotebookSelection.Unfiled) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.Inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.unfiled_notes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.notebook_note_count, unfiled), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (creating) {
        NotebookEditDialog(
            title = stringResource(R.string.notebook_new),
            onConfirm = { name, color ->
                creating = false
                scope.launch { onSelect(NotebookSelection.One(vm.create(name, color))) }
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun NotebookCard(nb: NotebookWithCount, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(80.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(Formats.parseColor(nb.notebook.color, Accent)))
            Spacer(Modifier.width(20.dp))
            Text(nb.notebook.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.notebook_note_count, nb.noteCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(20.dp))
        }
    }
}

@Composable
private fun NotebookNotes(
    vm: NotebooksViewModel,
    selection: NotebookSelection,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val notes by vm.notes.collectAsState()
    val notebook by vm.selectedNotebook.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val title = when (selection) {
        NotebookSelection.Unfiled -> stringResource(R.string.unfiled_notes)
        else -> notebook?.title.orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            notebook?.let { Box(Modifier.size(14.dp).clip(CircleShape).background(Formats.parseColor(it.color, Accent))) }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (notebook != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.notebook_rename)) }, onClick = { menu = false; editing = true })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notebook_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; deleting = true },
                        )
                    }
                }
            }
        }
        if (notes.isEmpty()) {
            Text(stringResource(R.string.notebook_empty), modifier = Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(notes, key = { it.note.id }) { n -> NoteRow(n) { onOpenNote(n.note.id) } }
        }
    }

    val nb = notebook
    if (editing && nb != null) {
        NotebookEditDialog(
            title = stringResource(R.string.notebook_rename),
            initialName = nb.title,
            initialColor = nb.color,
            onConfirm = { name, color -> editing = false; vm.update(nb.id, name, color) },
            onDismiss = { editing = false },
        )
    }
    if (deleting && nb != null) {
        ConfirmDialog(
            title = stringResource(R.string.notebook_delete),
            body = stringResource(R.string.notebook_delete_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { deleting = false; vm.delete(nb.id); onBack() },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
internal fun NoteRow(n: NoteWithLinks, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(n.note.title.ifBlank { stringResource(R.string.no_title) }, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val link = n.links.firstOrNull()
            val meta = if (link != null) {
                link.eventTitleSnapshot + " · " + Formats.dayShort(Formats.date(link.eventStartSnapshot)) +
                    if (link.eventDeleted) " · " + stringResource(R.string.note_event_deleted) else ""
            } else {
                stringResource(R.string.standalone_note) + " · " + Formats.dayShort(Formats.date(n.note.createdAt))
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (n.note.body.isNotBlank()) {
                Text(n.note.body, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
