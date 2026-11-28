package io.github.qlaus.calnotes.ui.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.qlaus.calnotes.data.local.NotebookEntity
import io.github.qlaus.calnotes.data.local.NotebookWithCount
import io.github.qlaus.calnotes.data.repo.NoteRepository
import io.github.qlaus.calnotes.data.repo.NoteWithLinks
import io.github.qlaus.calnotes.data.repo.NotebookRepository
import io.github.qlaus.calnotes.ui.nav.NotebookSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotebooksViewModel @Inject constructor(
    private val notebookRepository: NotebookRepository,
    private val noteRepository: NoteRepository,
    val exporter: io.github.qlaus.calnotes.export.NoteExporter,
) : ViewModel() {

    private val selection = MutableStateFlow<NotebookSelection>(NotebookSelection.All)

    val notebooks: StateFlow<List<NotebookWithCount>> =
        notebookRepository.notebooks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unfiledCount: StateFlow<Int> = noteRepository.observeNotesIn(null)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val notes: StateFlow<List<NoteWithLinks>> = selection.flatMapLatest { s ->
        when (s) {
            NotebookSelection.All -> flowOf(emptyList())
            NotebookSelection.Unfiled -> noteRepository.observeNotesIn(null)
            is NotebookSelection.One -> noteRepository.observeNotesIn(s.notebookId)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedNotebook: StateFlow<NotebookEntity?> = selection.flatMapLatest { s ->
        if (s is NotebookSelection.One) notebookRepository.observeNotebook(s.notebookId) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setSelection(s: NotebookSelection) {
        selection.value = s
    }

    suspend fun create(title: String, color: String?): String = notebookRepository.create(title, color)

    fun update(id: String, title: String, color: String?) {
        viewModelScope.launch { notebookRepository.update(id, title, color) }
    }

    fun delete(id: String) {
        viewModelScope.launch { notebookRepository.delete(id) }
    }

    /** New standalone note in the current notebook (or unfiled). Returns its id. */
    suspend fun createNote(): String {
        val notebookId = (selection.value as? NotebookSelection.One)?.notebookId
        return noteRepository.createStandalone(notebookId)
    }
}
