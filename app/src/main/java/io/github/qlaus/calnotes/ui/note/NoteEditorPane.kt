package io.github.qlaus.calnotes.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.ui.common.ConfirmDialog
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.common.PaneScaffold
import kotlinx.coroutines.launch

@Composable
fun NoteEditorPane(
    noteId: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onOpenEvent: (calendarId: String, eventId: String) -> Unit,
    onDeleted: () -> Unit,
) {
    val vm: NoteEditorViewModel = hiltViewModel(key = "note/$noteId")
    LaunchedEffect(noteId) { vm.load(noteId) }
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var askDelete by remember { mutableStateOf(false) }
    val notebooks by vm.notebooks.collectAsState()
    var notebookMenu by remember { mutableStateOf(false) }

    PaneScaffold(
        canGoBack = canGoBack,
        onBack = onBack,
        actions = {
            if (state.loaded && !state.missing) {
                Text(
                    if (state.saved) stringResource(R.string.note_saved) else "…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                IconButton(onClick = { askDelete = true }) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@PaneScaffold
        }
        if (state.missing) {
            Text(stringResource(R.string.event_not_found), modifier = Modifier.padding(32.dp))
            return@PaneScaffold
        }
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val current = notebooks.firstOrNull { it.id == state.notebookId }
                Box {
                    AssistChip(
                        onClick = { notebookMenu = true },
                        label = { Text(current?.title ?: stringResource(R.string.note_notebook_none)) },
                        leadingIcon = {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Formats.parseColor(current?.color, MaterialTheme.colorScheme.outline)))
                        },
                    )
                    DropdownMenu(expanded = notebookMenu, onDismissRequest = { notebookMenu = false }) {
                        notebooks.forEach { nb ->
                            DropdownMenuItem(text = { Text(nb.title) }, onClick = { notebookMenu = false; vm.moveToNotebook(nb.id) })
                        }
                        if (current != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.note_remove_from_notebook)) },
                                onClick = { notebookMenu = false; vm.moveToNotebook(null) },
                            )
                        }
                    }
                }
            }
            if (state.links.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.links.forEach { link ->
                        val label = buildString {
                            append(link.eventTitleSnapshot.ifBlank { "—" })
                            append(" · ")
                            append(Formats.dayShort(Formats.date(link.eventStartSnapshot)))
                        }
                        AssistChip(
                            onClick = {
                                if (!link.eventDeleted && link.scope == EventLinkScope.INSTANCE) {
                                    onOpenEvent(link.calendarId, link.eventId)
                                }
                            },
                            label = {
                                Text(
                                    when {
                                        link.eventDeleted -> "$label · ${stringResource(R.string.note_event_deleted)}"
                                        link.scope == EventLinkScope.SERIES -> "$label · ${stringResource(R.string.note_series)}"
                                        else -> label
                                    },
                                )
                            },
                            colors = if (link.eventDeleted) {
                                AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
                            } else {
                                AssistChipDefaults.assistChipColors()
                            },
                        )
                    }
                }
            }
            val textColor = MaterialTheme.colorScheme.onSurface
            BasicTextField(
                value = state.title,
                onValueChange = vm::onTitleChange,
                textStyle = MaterialTheme.typography.headlineLarge.copy(color = textColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                    if (state.title.isEmpty()) {
                        Text(stringResource(R.string.note_title_hint), style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    inner()
                    }
                },
            )
            BasicTextField(
                value = state.body,
                onValueChange = vm::onBodyChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp),
                decorationBox = { inner ->
                    Box {
                    if (state.body.isEmpty()) {
                        Text(stringResource(R.string.note_body_hint), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    inner()
                    }
                },
            )
        }
    }

    if (askDelete) {
        ConfirmDialog(
            title = stringResource(R.string.note_delete_title),
            body = stringResource(R.string.note_delete_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                askDelete = false
                scope.launch {
                    vm.delete()
                    onDeleted()
                }
            },
            onDismiss = { askDelete = false },
        )
    }
}
