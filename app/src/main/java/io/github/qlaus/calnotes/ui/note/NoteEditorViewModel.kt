package io.github.qlaus.calnotes.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.qlaus.calnotes.data.local.EventLinkEntity
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
    val links: List<EventLinkEntity> = emptyList(),
    val notebookId: String? = null,
    val saved: Boolean = true,
)

/** Plain-text note editor with debounced autosave; pending edits are flushed when the editor closes. */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    notebookRepository: NotebookRepository,
    private val syncCoordinator: SyncCoordinator,
    @ApplicationScope private val appScope: CoroutineScope,
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
                    links = note.links,
                    notebookId = note.note.notebookId,
                )
            }
            // Keep link info (e.g. "event deleted") live without overwriting what the user types.
            noteRepository.observeNote(id).collect { latest ->
                if (latest != null) _state.value = _state.value.copy(links = latest.links, notebookId = latest.note.notebookId)
            }
        }
    }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }

    fun onBodyChange(value: String) = edit { it.copy(body = value) }

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

    fun moveToNotebook(notebookId: String?) {
        val id = noteId ?: return
        viewModelScope.launch { noteRepository.moveToNotebook(id, notebookId) }
    }

    override fun onCleared() {
        if (!_state.value.saved) appScope.launch { saveNow() }
        super.onCleared()
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 600L
    }
}
