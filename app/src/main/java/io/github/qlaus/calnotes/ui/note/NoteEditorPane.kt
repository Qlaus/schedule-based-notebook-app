@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.qlaus.calnotes.ui.note

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.core.export.ExportMarkdown
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.data.local.NoteBackupEntity
import io.github.qlaus.calnotes.ui.common.ConfirmDialog
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.common.PaneScaffold
import kotlinx.coroutines.launch

private val InkColors = listOf("#1C1C1E", "#3B6FE0", "#D93B3B", "#2E9E5B", "#F6BF26")
private val InkSizes = listOf(1.6f, 2.5f, 4f)

/**
 * Note page: WYSIWYG rich text (shared web editor in a WebView) with a full-page handwriting layer.
 * "Yazı" mode edits text; "Kalem" mode draws with the stylus (fingers scroll).
 */
@Composable
fun NoteEditorPane(
    noteId: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onOpenEvent: (calendarId: String, eventId: String) -> Unit,
    onDeleted: () -> Unit,
    fullscreen: Boolean = false,
    /** Only in the wide (landscape) layout. */
    onToggleFullscreen: (() -> Unit)? = null,
) {
    val vm: NoteEditorViewModel = hiltViewModel(key = "note/$noteId")
    LaunchedEffect(noteId) { vm.load(noteId) }
    val state by vm.state.collectAsState()
    val notebooks by vm.notebooks.collectAsState()
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as Activity
    val exporter = vm.exporter

    var askDelete by remember { mutableStateOf(false) }
    var notebookMenu by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var showBackups by remember { mutableStateOf(false) }

    // Ink tool state (kept across rotation).
    var penMode by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf(InkTool.PEN) }
    var color by rememberSaveable { mutableStateOf(InkColors[0]) }
    var size by rememberSaveable { mutableStateOf(InkSizes[1]) }
    var stylusOnly by rememberSaveable { mutableStateOf(true) }
    var inkUndoTick by remember { mutableIntStateOf(0) }

    val saveMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) scope.launch { exporter.saveMarkdown(uri, exporter.collect(listOf(noteId))) }
    }

    PaneScaffold(
        canGoBack = canGoBack,
        onBack = onBack,
        actions = {
            if (state.loaded && !state.missing) {
                Text(
                    when {
                        !state.saved -> "…"
                        state.cloudPending -> stringResource(R.string.note_cloud_pending)
                        else -> stringResource(R.string.note_cloud_synced)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                if (onToggleFullscreen != null) {
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(
                            if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            stringResource(if (fullscreen) R.string.fullscreen_exit else R.string.fullscreen_enter),
                        )
                    }
                }
                Box {
                    IconButton(onClick = { exportMenu = true }) { Icon(Icons.Outlined.Share, stringResource(R.string.export)) }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_share_md)) }, onClick = {
                            exportMenu = false
                            scope.launch { exporter.shareMarkdown(activity, state.title.ifBlank { "Not" }, exporter.collect(listOf(noteId))) }
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_save_md)) }, onClick = {
                            exportMenu = false
                            saveMd.launch("${ExportMarkdown.safeFileName(state.title.ifBlank { "Not" })}.md")
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_pdf)) }, onClick = {
                            exportMenu = false
                            scope.launch { exporter.printPdf(activity, state.title.ifBlank { "Not" }, exporter.collect(listOf(noteId))) }
                        })
                    }
                }
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
        Column(Modifier.fillMaxSize()) {
            // Notebook + linked events + backups
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                            DropdownMenuItem(text = { Text(stringResource(R.string.note_remove_from_notebook)) }, onClick = {
                                notebookMenu = false
                                vm.moveToNotebook(null)
                            })
                        }
                    }
                }
                state.links.forEach { link ->
                    val label = (link.eventTitleSnapshot.ifBlank { "—" }) + " · " + Formats.dayShort(Formats.date(link.eventStartSnapshot))
                    AssistChip(
                        onClick = {
                            if (!link.eventDeleted && link.scope == EventLinkScope.INSTANCE) onOpenEvent(link.calendarId, link.eventId)
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
                        colors = if (link.eventDeleted) AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
                        else AssistChipDefaults.assistChipColors(),
                    )
                }
                if (state.backups.isNotEmpty()) {
                    TextButton(onClick = { showBackups = true }) {
                        Text(stringResource(R.string.backups_button, state.backups.size), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            InkToolbar(
                penMode = penMode,
                onPenMode = { penMode = it },
                tool = tool, onTool = { tool = it },
                color = color, onColor = { color = it },
                size = size, onSize = { size = it },
                stylusOnly = stylusOnly, onStylusOnly = { stylusOnly = it },
                onUndo = { inkUndoTick++ },
            )

            // The page: one WebView host per note (and per restored revision).
            key(noteId, state.revision) {
                val latestVm by rememberUpdatedState(vm)
                val penModeNow by rememberUpdatedState(penMode)
                val titleHint = stringResource(R.string.note_title_hint)
                val bodyHint = stringResource(R.string.note_body_hint)
                var host by remember { mutableStateOf<NoteEditorHost?>(null) }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        NoteEditorHost(ctx).also { h ->
                            h.listener = object : NoteEditorHost.Listener {
                                override fun onReady() {
                                    val s = latestVm.state.value
                                    h.load("$noteId#${s.revision}", s.title, s.body, s.ink, titleHint, bodyHint)
                                    h.setPenMode(penModeNow)
                                }
                                override fun onContent(title: String, markdown: String) = latestVm.onContentChange(title, markdown)
                                override fun onInk(ink: String?) = latestVm.onInkChange(ink)
                            }
                            host = h
                        }
                    },
                    update = { h ->
                        h.inkView.tool = tool
                        h.inkView.colorHex = color
                        h.inkView.sizePage = size
                        h.inkView.stylusOnly = stylusOnly
                        if (h.inkView.penMode != penMode && h.ready) h.setPenMode(penMode)
                    },
                )
                LaunchedEffect(inkUndoTick) { if (inkUndoTick > 0) host?.undoInk() }
                DisposableEffect(Unit) { onDispose { host?.destroy() } }
            }
        }
    }

    if (showBackups) {
        BackupsDialog(
            backups = state.backups,
            onRestore = { showBackups = false; vm.restore(it) },
            onDelete = { vm.deleteBackup(it.id) },
            onDismiss = { showBackups = false },
        )
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

@Composable
private fun InkToolbar(
    penMode: Boolean,
    onPenMode: (Boolean) -> Unit,
    tool: InkTool,
    onTool: (InkTool) -> Unit,
    color: String,
    onColor: (String) -> Unit,
    size: Float,
    onSize: (Float) -> Unit,
    stylusOnly: Boolean,
    onStylusOnly: (Boolean) -> Unit,
    onUndo: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(selected = !penMode, onClick = { onPenMode(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) {
                Text(stringResource(R.string.mode_text))
            }
            SegmentedButton(selected = penMode, onClick = { onPenMode(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) {
                Text(stringResource(R.string.mode_pen))
            }
        }
        if (penMode) {
            listOf(InkTool.PEN to R.string.tool_pen, InkTool.HIGHLIGHTER to R.string.tool_highlighter, InkTool.ERASER to R.string.tool_eraser)
                .forEach { (t, label) -> FilterChip(selected = tool == t, onClick = { onTool(t) }, label = { Text(stringResource(label)) }) }
            Spacer(Modifier.width(4.dp))
            InkColors.forEach { hex ->
                val c = Formats.parseColor(hex, MaterialTheme.colorScheme.onSurface)
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(if (hex == color) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                        .clickable { onColor(hex) },
                )
            }
            Spacer(Modifier.width(4.dp))
            InkSizes.forEachIndexed { i, s ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(if (s == size) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f))
                        .clickable { onSize(s) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size((6 + i * 5).dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                }
            }
            TextButton(onClick = onUndo) { Text(stringResource(R.string.ink_undo)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stylus_only), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                Switch(checked = stylusOnly, onCheckedChange = onStylusOnly)
            }
        }
    }
}

@Composable
private fun BackupsDialog(
    backups: List<NoteBackupEntity>,
    onRestore: (NoteBackupEntity) -> Unit,
    onDelete: (NoteBackupEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backups_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.backups_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                backups.forEach { b ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(if (b.reason == "conflict-remote") R.string.backup_from_other_device else R.string.backup_from_this_device) +
                                " · " + Formats.dayShort(Formats.date(b.createdAt)) + " " + Formats.time(b.createdAt),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(b.title, style = MaterialTheme.typography.titleSmall)
                        Text(ExportMarkdown.plainPreview(b.body).take(300), style = MaterialTheme.typography.bodySmall, maxLines = 6)
                        Row {
                            TextButton(onClick = { onRestore(b) }) { Text(stringResource(R.string.backup_restore)) }
                            TextButton(onClick = { onDelete(b) }) { Text(stringResource(R.string.backup_delete), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
