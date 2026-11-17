package io.github.qlaus.calnotes.ui.calendar

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.qlaus.calnotes.core.calendar.GoogleEventColors
import io.github.qlaus.calnotes.core.model.EventLinkMatcher
import io.github.qlaus.calnotes.data.local.CalendarEntity
import io.github.qlaus.calnotes.data.local.EventEntity
import io.github.qlaus.calnotes.data.repo.CalendarRepository
import io.github.qlaus.calnotes.data.repo.NoteRepository
import io.github.qlaus.calnotes.data.repo.toKey
import io.github.qlaus.calnotes.data.repo.toRef
import io.github.qlaus.calnotes.sync.SyncCoordinator
import io.github.qlaus.calnotes.sync.SyncStatus
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.theme.Accent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

enum class CalendarMode { DAY, WEEK, MONTH, AGENDA }

/** An event as the calendar views draw it. */
data class EventUi(
    val event: EventEntity,
    val calendarName: String,
    val color: Color,
    val hasNotes: Boolean,
) {
    val key: String get() = event.calendarId + "/" + event.id
}

/** What the right-hand (or full-screen on narrow windows) pane shows. */
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

data class DateRange(val from: Long, val to: Long)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    noteRepository: NoteRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val _mode = MutableStateFlow(CalendarMode.WEEK)
    val mode: StateFlow<CalendarMode> = _mode.asStateFlow()

    private val _anchor = MutableStateFlow(LocalDate.now())
    val anchor: StateFlow<LocalDate> = _anchor.asStateFlow()

    private val _detailStack = MutableStateFlow<List<DetailTarget>>(emptyList())
    val detail: StateFlow<DetailTarget?> = _detailStack.map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> = syncCoordinator.status
    val pendingOps: StateFlow<Int> = calendarRepository.pendingOpCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val outboxError: StateFlow<String?> = calendarRepository.lastOutboxError

    val calendars: StateFlow<List<CalendarEntity>> =
        calendarRepository.calendars.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val range: StateFlow<DateRange> = combine(_mode, _anchor) { m, a -> rangeFor(m, a) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, rangeFor(_mode.value, _anchor.value))

    val events: StateFlow<List<EventUi>> = range.flatMapLatest { r ->
        combine(calendarRepository.observeEvents(r.from, r.to), noteRepository.activeLinks) { rows, links ->
            val keys = links.map { it.toKey() }
            rows.map { row ->
                EventUi(
                    event = row.event,
                    calendarName = row.calendarName,
                    color = Formats.parseColor(GoogleEventColors.hexFor(row.event.colorId) ?: row.calendarColor, Accent),
                    hasNotes = keys.any { EventLinkMatcher.matches(it, row.event.toRef()) },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var rangeRefreshJob: Job? = null

    init {
        // Fetch the visible range when the user navigates (cheap, keeps the view fresh beyond the sync window).
        viewModelScope.launch {
            range.debounce(400).distinctUntilChanged().collect { r ->
                rangeRefreshJob?.cancel()
                rangeRefreshJob = launch { syncCoordinator.refreshRange(r.from, r.to) }
            }
        }
    }

    fun setMode(mode: CalendarMode) {
        _mode.value = mode
    }

    fun goToday() {
        _anchor.value = LocalDate.now()
    }

    fun step(forward: Boolean) {
        val sign = if (forward) 1L else -1L
        _anchor.value = when (_mode.value) {
            CalendarMode.DAY -> _anchor.value.plusDays(sign)
            CalendarMode.WEEK -> _anchor.value.plusWeeks(sign)
            CalendarMode.MONTH -> _anchor.value.plusMonths(sign)
            CalendarMode.AGENDA -> _anchor.value.plusDays(sign * AGENDA_DAYS)
        }
    }

    fun openDay(date: LocalDate) {
        _anchor.value = date
        _mode.value = CalendarMode.DAY
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            syncCoordinator.syncNow()
            val r = range.value
            syncCoordinator.refreshRange(r.from, r.to)
            _refreshing.value = false
        }
    }

    fun setCalendarVisible(id: String, visible: Boolean) {
        viewModelScope.launch { calendarRepository.setCalendarVisible(id, visible) }
    }

    fun dismissOutboxError() = calendarRepository.clearOutboxError()

    // --- detail navigation ---

    /** Opens a target as the new root of the detail pane (e.g. tapping an event in the calendar). */
    fun show(target: DetailTarget) {
        _detailStack.value = listOf(target)
    }

    /** Pushes a target on top of the current one (e.g. event detail → note). */
    fun push(target: DetailTarget) {
        _detailStack.value = _detailStack.value + target
    }

    /** Replaces the top entry (e.g. editor → the event it just created). */
    fun replaceTop(target: DetailTarget) {
        _detailStack.value = _detailStack.value.dropLast(1) + target
    }

    fun stackDepth(): Int = _detailStack.value.size

    fun back() {
        _detailStack.value = _detailStack.value.dropLast(1)
    }

    fun closeDetail() {
        _detailStack.value = emptyList()
    }

    private fun rangeFor(mode: CalendarMode, anchor: LocalDate): DateRange {
        val (start, endExclusive) = when (mode) {
            CalendarMode.DAY -> anchor to anchor.plusDays(1)
            CalendarMode.WEEK -> Formats.weekStart(anchor).let { it to it.plusDays(7) }
            CalendarMode.MONTH -> monthGridStart(anchor).let { it to it.plusDays(42) }
            CalendarMode.AGENDA -> anchor to anchor.plusDays(AGENDA_DAYS)
        }
        return DateRange(
            start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    companion object {
        const val AGENDA_DAYS = 60L

        fun monthGridStart(anchor: LocalDate): LocalDate = Formats.weekStart(anchor.withDayOfMonth(1))
    }
}
