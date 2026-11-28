package io.github.qlaus.calnotes.ui.nav

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Top-level sections of the navigation rail / bar. */
enum class Section { CALENDAR, NOTEBOOKS, SEARCH, SETTINGS }

/** What the detail pane (right side on tablets, full screen on narrow windows) shows. */
sealed interface DetailTarget {
    data class Event(val calendarId: String, val eventId: String) : DetailTarget
    data class Note(val noteId: String) : DetailTarget
    data class EditEvent(
        val calendarId: String? = null,
        val eventId: String? = null,
        val presetDate: LocalDate? = null,
        val presetTime: LocalTime? = null,
    ) : DetailTarget
}

/** In the notebooks section: which list the left pane shows. */
sealed interface NotebookSelection {
    data object All : NotebookSelection
    data object Unfiled : NotebookSelection
    data class One(val notebookId: String) : NotebookSelection
}

/** Navigation state shared by all panes; survives rotation. */
@HiltViewModel
class NavViewModel @Inject constructor() : ViewModel() {

    private val _section = MutableStateFlow(Section.CALENDAR)
    val section: StateFlow<Section> = _section.asStateFlow()

    private val _notebookSelection = MutableStateFlow<NotebookSelection>(NotebookSelection.All)
    val notebookSelection: StateFlow<NotebookSelection> = _notebookSelection.asStateFlow()

    private val _stack = MutableStateFlow<List<DetailTarget>>(emptyList())
    val stack: StateFlow<List<DetailTarget>> = _stack.asStateFlow()

    /** Wide layout only: the note editor takes the whole screen (rail and list hidden). */
    private val _noteFullscreen = MutableStateFlow(false)
    val noteFullscreen: StateFlow<Boolean> = _noteFullscreen.asStateFlow()

    fun setNoteFullscreen(enabled: Boolean) {
        _noteFullscreen.value = enabled
    }

    fun selectSection(section: Section) {
        _section.value = section
    }

    fun selectNotebook(selection: NotebookSelection) {
        _notebookSelection.value = selection
    }

    /** Opens a target as the new root of the detail pane. */
    fun show(target: DetailTarget) {
        _stack.value = listOf(target)
    }

    /** Pushes a target on top of the current one (e.g. event → note). */
    fun push(target: DetailTarget) {
        _stack.value = _stack.value + target
    }

    fun back() {
        _stack.value = _stack.value.dropLast(1)
    }

    /** Handles system back: detail stack first, then notebook list, then calendar section. */
    fun handleBack(): Boolean = when {
        _noteFullscreen.value && _stack.value.lastOrNull() is DetailTarget.Note -> { _noteFullscreen.value = false; true }
        _stack.value.isNotEmpty() -> { back(); true }
        _section.value == Section.NOTEBOOKS && _notebookSelection.value != NotebookSelection.All -> {
            _notebookSelection.value = NotebookSelection.All; true
        }
        _section.value != Section.CALENDAR -> { _section.value = Section.CALENDAR; true }
        else -> false
    }
}
