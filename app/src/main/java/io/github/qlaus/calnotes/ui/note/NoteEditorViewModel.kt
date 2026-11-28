package io.github.qlaus.calnotes.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.qlaus.calnotes.data.local.EventLinkEntity
import io.github.qlaus.calnotes.data.local.NoteBackupEntity
import io.github.qlaus.calnotes.data.local.NotebookEntity
import io.github.qlaus.calnotes.data.repo.NoteRepository
import io.github.qlaus.calnotes.data.repo.NotebookRepository
import io.github.qlaus.calnotes.sync.SyncCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import io.github.qlaus.calnotes.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteEditorState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val title: String = "",
    val body: String = "",
    val ink: String? = null,
    val links: List<EventLinkEntity> = emptyList(),
    val notebookId: String? = null,
    /** True while the latest saved version has not reached Google Drive yet. */
    val cloudPending: Boolean = true,
    val backups: List<NoteBackupEntity> = emptyList(),
    /** Bumped when the text is replaced from outside the editor (backup restore): the page reloads. */
    val revision: Int = 0,
    val saved: Boolean = true,
)

/** Plain-text note editor with debounced autosave; pending edits are flushed when the editor closes. */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    notebookRepository: NotebookRepository,
    private val syncCoordinator: SyncCoordinator,
    @ApplicationScope private val appScope: CoroutineScope,
    val exporter: io.github.qlaus.calnotes.export.NoteExporter,
) : ViewModel() {

    val notebooks: StateFlow<List<NotebookEntity>> =
        notebookRepository.activeNotebooks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(NoteEditorState())
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    private var noteId: String? = null
    private var saveJob: Job? = null

    fun load(id: String) {
        if (noteId == id) return
        noteId = id
        viewModelScope.launch {
            val note = noteRepository.observeNote(id).first()
            _state.value = if (note == null) {
                NoteEditorState(loaded = true, missing = true)
            } else {
                NoteEditorState(
                    loaded = true,
                    title = note.note.title,
                    body = note.note.body,
                    ink = note.note.ink,
                    links = note.links,
                    notebookId = note.note.notebookId,
                    cloudPending = note.note.dirty,
                )
            }
            // Keep link info (e.g. "event deleted") live without overwriting what the user types.
            noteRepository.observeNote(id).collect { latest ->
                if (latest != null) {
                    _state.value = _state.value.copy(
                        links = latest.links,
                        notebookId = latest.note.notebookId,
                        cloudPending = latest.note.dirty,
                    )
                }
            }
        }
        viewModelScope.launch {
            noteRepository.observeBackups(id).collect { list -> _state.value = _state.value.copy(backups = list) }
        }
    }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }

    fun onBodyChange(value: String) = edit { it.copy(body = value) }

    /** From the page editor: title and Markdown body together. */
    fun onContentChange(title: String, body: String) {
        if (title != _state.value.title || body != _state.value.body) edit { it.copy(title = title, body = body) }
    }

    private var inkJob: Job? = null

    /** Handwriting changed (stroke added/erased/undone): saved shortly after, outside the text autosave. */
    fun onInkChange(ink: String?) {
        _state.value = _state.value.copy(ink = ink)
        inkJob?.cancel()
        inkJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            noteId?.let { noteRepository.updateInk(it, ink) }
        }
    }

    private fun edit(transform: (NoteEditorState) -> NoteEditorState) {
        _state.value = transform(_state.value).copy(saved = false)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    private suspend fun saveNow() {
        val id = noteId ?: return
        val s = _state.value
        if (!s.loaded || s.missing) return
        noteRepository.updateContent(id, s.title, s.body)
        _state.value = _state.value.copy(saved = true)
    }

    suspend fun delete() {
        val id = noteId ?: return
        saveJob?.cancel()
        _state.value = _state.value.copy(missing = true)
        noteRepository.delete(id)
        syncCoordinator.pushChanges() // remove the calendar marker if this was the last note
    }

    /** Replaces the editor text with a backup (the current text becomes a backup). */
    fun restore(backup: NoteBackupEntity) {
        viewModelScope.launch {
            saveJob?.cancel()
            saveNow()
            noteRepository.restoreBackup(backup)
            _state.value = _state.value.copy(title = backup.title, body = backup.body, saved = true, revision = _state.value.revision + 1)
        }
    }

    fun deleteBackup(id: String) {
        viewModelScope.launch { noteRepository.deleteBackup(id) }
    }

    fun moveToNotebook(notebookId: String?) {
        val id = noteId ?: return
        viewModelScope.launch { noteRepository.moveToNotebook(id, notebookId) }
    }

    override fun onCleared() {
        if (!_state.value.saved) appScope.launch { saveNow() }
        if (inkJob?.isActive == true) {
            val id = noteId
            val ink = _state.value.ink
            if (id != null) appScope.launch { noteRepository.updateInk(id, ink) }
        }
        super.onCleared()
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 600L
    }
}
