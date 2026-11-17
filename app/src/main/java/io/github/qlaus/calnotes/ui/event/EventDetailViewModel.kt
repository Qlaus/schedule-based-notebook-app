package io.github.qlaus.calnotes.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.data.local.EventEntity
import io.github.qlaus.calnotes.data.local.EventWithCalendar
import io.github.qlaus.calnotes.data.local.NotebookEntity
import io.github.qlaus.calnotes.data.local.NoteEntity
import io.github.qlaus.calnotes.data.repo.CalendarRepository
import io.github.qlaus.calnotes.data.repo.NoteRepository
import io.github.qlaus.calnotes.data.repo.NotebookLinkWithNotebook
import io.github.qlaus.calnotes.data.repo.NotebookRepository
import io.github.qlaus.calnotes.data.repo.toRef
import io.github.qlaus.calnotes.sync.SyncCoordinator
import io.github.qlaus.calnotes.ui.common.Formats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** null = still loading, Missing = no such event (e.g. deleted elsewhere). */
sealed interface EventDetailState {
    data object Loading : EventDetailState
    data object Missing : EventDetailState
    data class Loaded(val event: EventWithCalendar) : EventDetailState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val key = MutableStateFlow<Pair<String, String>?>(null)

    val state: StateFlow<EventDetailState> = key.filterNotNull().flatMapLatest { (cal, id) ->
        calendarRepository.observeEvent(cal, id).map { it?.let(EventDetailState::Loaded) ?: EventDetailState.Missing }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EventDetailState.Loading)

    val notes: StateFlow<List<NoteEntity>> = state.flatMapLatest { s ->
        if (s is EventDetailState.Loaded) noteRepository.observeNotesForEvent(s.event.event.toRef()) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notebookLink: StateFlow<NotebookLinkWithNotebook?> = state.flatMapLatest { s ->
        if (s is EventDetailState.Loaded) notebookRepository.observeLinkFor(s.event.event.toRef()) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val notebooks: StateFlow<List<NotebookEntity>> =
        notebookRepository.activeNotebooks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun load(calendarId: String, eventId: String) {
        key.value = calendarId to eventId
    }

    /** Creates a note (inside the linked notebook, if any) and returns its id. */
    suspend fun createNote(scope: EventLinkScope): String? {
        val event = currentEvent() ?: return null
        val notebookId = notebookRepository.notebookIdFor(event.toRef())
        val id = noteRepository.createForEvent(event, scope, defaultTitle(event), notebookId)
        syncCoordinator.pushChanges()
        return id
    }

    /**
     * Links this occurrence or its series to a notebook (creating it when [newNotebookTitle] is set)
     * and opens a page for this occurrence in it. Returns the new note id.
     */
    suspend fun linkToNotebook(notebookId: String?, newNotebookTitle: String?, scope: EventLinkScope): String? {
        val event = currentEvent() ?: return null
        val targetId = notebookId ?: newNotebookTitle?.let { notebookRepository.create(it) } ?: return null
        notebookRepository.linkEvent(event, scope, targetId)
        val id = noteRepository.createForEvent(event, EventLinkScope.INSTANCE, defaultTitle(event), targetId)
        syncCoordinator.pushChanges()
        return id
    }

    suspend fun unlinkNotebook() {
        notebookLink.value?.let { notebookRepository.unlink(it.link.id) }
    }

    private fun currentEvent(): EventEntity? = (state.value as? EventDetailState.Loaded)?.event?.event

    private fun defaultTitle(event: EventEntity) =
        listOf(event.title.ifBlank { null }, Formats.dayShort(Formats.date(event.startMillis))).filterNotNull().joinToString(" · ")

    suspend fun delete(wholeSeries: Boolean) {
        val event = (state.value as? EventDetailState.Loaded)?.event?.event ?: return
        calendarRepository.deleteEvent(event.calendarId, event.id, wholeSeries)
        syncCoordinator.pushChanges()
    }
}
