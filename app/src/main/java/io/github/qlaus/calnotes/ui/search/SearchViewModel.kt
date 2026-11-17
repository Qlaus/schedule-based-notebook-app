package io.github.qlaus.calnotes.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.qlaus.calnotes.core.search.TextSearch
import io.github.qlaus.calnotes.data.local.EventWithCalendar
import io.github.qlaus.calnotes.data.repo.CalendarRepository
import io.github.qlaus.calnotes.data.repo.NoteRepository
import io.github.qlaus.calnotes.data.repo.NoteWithLinks
import io.github.qlaus.calnotes.data.repo.NotebookRepository
import io.github.qlaus.calnotes.ui.common.Formats
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

data class SearchResults(
    val query: String = "",
    val notes: List<NoteWithLinks> = emptyList(),
    val events: List<EventWithCalendar> = emptyList(),
)

/** Searches notes (title, notebook, linked event name, dates) and cached events (title, location, dates). */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    noteRepository: NoteRepository,
    notebookRepository: NotebookRepository,
    calendarRepository: CalendarRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<SearchResults> = combine(
        _query.debounce(200),
        noteRepository.allNotesWithLinks,
        notebookRepository.activeNotebooks,
        calendarRepository.observeAllVisibleEvents(),
    ) { q, notes, notebooks, events ->
        if (q.isBlank()) return@combine SearchResults(q)
        val locale = Locale.getDefault()
        val notebookNames = notebooks.associate { it.id to it.title }
        val matchedNotes = notes.filter { n ->
            val haystack = buildString {
                append(n.note.title).append(' ')
                n.note.notebookId?.let { append(notebookNames[it].orEmpty()).append(' ') }
                append(Formats.searchDates(n.note.createdAt)).append(' ')
                n.links.forEach { l -> append(l.eventTitleSnapshot).append(' ').append(Formats.searchDates(l.eventStartSnapshot)).append(' ') }
            }
            TextSearch.matches(haystack, q, locale)
        }.sortedByDescending { n -> n.links.maxOfOrNull { it.eventStartSnapshot } ?: n.note.updatedAt }

        val now = System.currentTimeMillis()
        val matchedEvents = events.filter { e ->
            TextSearch.matches(e.event.title + " " + e.event.location + " " + Formats.searchDates(e.event.startMillis), q, locale)
        }.sortedBy { abs(it.event.startMillis - now) }.take(MAX_EVENTS).sortedBy { it.event.startMillis }

        SearchResults(q, matchedNotes, matchedEvents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    private companion object {
        const val MAX_EVENTS = 50
    }
}
