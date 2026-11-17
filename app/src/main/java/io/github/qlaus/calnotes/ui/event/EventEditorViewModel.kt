package io.github.qlaus.calnotes.ui.event

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.qlaus.calnotes.core.calendar.RepeatRule
import io.github.qlaus.calnotes.data.local.CalendarEntity
import io.github.qlaus.calnotes.data.remote.calendar.EventDraft
import io.github.qlaus.calnotes.data.repo.CalendarRepository
import io.github.qlaus.calnotes.sync.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class EditorState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val isRecurring: Boolean = false,
    val calendars: List<CalendarEntity> = emptyList(),
    val draft: EventDraft? = null,
    val error: EditorError? = null,
)

enum class EditorError { END_BEFORE_START, NO_CALENDAR }

@HiltViewModel
class EventEditorViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var eventId: String? = null
    private val zone: ZoneId get() = ZoneId.systemDefault()

    suspend fun load(calendarId: String?, eventId: String?, presetDate: LocalDate?, presetTime: LocalTime?) {
        if (_state.value.loaded) return
        this.eventId = eventId
        val writable = calendarRepository.calendars.first().filter { it.writable }
        val existing = if (calendarId != null && eventId != null) calendarRepository.observeEvent(calendarId, eventId).first() else null
        val draft = if (existing != null) {
            val e = existing.event
            val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.startMillis), zone)
            val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.endMillis), zone)
            EventDraft(
                calendarId = e.calendarId,
                title = e.title,
                description = e.description,
                location = e.location,
                allDay = e.allDay,
                startDate = start.toLocalDate(),
                startTime = start.toLocalTime(),
                // All-day ends are exclusive in storage; the editor shows the inclusive last day.
                endDate = if (e.allDay) end.toLocalDate().minusDays(1).coerceAtLeast(start.toLocalDate()) else end.toLocalDate(),
                endTime = end.toLocalTime(),
            )
        } else {
            val date = presetDate ?: LocalDate.now()
            val time = presetTime ?: LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
            val endDateTime = LocalDateTime.of(date, time).plusHours(1)
            val calendar = writable.firstOrNull { it.isPrimary } ?: writable.firstOrNull()
            EventDraft(
                calendarId = calendar?.id.orEmpty(),
                title = "",
                description = "",
                location = "",
                allDay = false,
                startDate = date,
                startTime = time,
                endDate = endDateTime.toLocalDate(),
                endTime = endDateTime.toLocalTime(),
            )
        }
        _state.value = EditorState(
            loaded = true,
            isNew = existing == null,
            isRecurring = existing?.event?.recurringEventId != null,
            calendars = writable,
            draft = draft,
        )
    }

    fun update(transform: (EventDraft) -> EventDraft) {
        val current = _state.value.draft ?: return
        var next = transform(current)
        // Moving the start keeps the duration, like most calendar apps.
        if (next.startDate != current.startDate || next.startTime != current.startTime) {
            val duration = java.time.Duration.between(
                LocalDateTime.of(current.startDate, current.startTime),
                LocalDateTime.of(current.endDate, current.endTime),
            ).coerceAtLeast(java.time.Duration.ZERO)
            val newEnd = LocalDateTime.of(next.startDate, next.startTime).plus(duration)
            next = next.copy(endDate = newEnd.toLocalDate(), endTime = newEnd.toLocalTime())
        }
        _state.value = _state.value.copy(draft = next, error = null)
    }

    /** Validates and saves. Returns the (possibly temporary) event id, or null if invalid. */
    suspend fun save(wholeSeries: Boolean): Pair<String, String>? {
        val draft = _state.value.draft ?: return null
        if (draft.calendarId.isBlank()) {
            _state.value = _state.value.copy(error = EditorError.NO_CALENDAR)
            return null
        }
        if (draft.endMillis(zone) < draft.startMillis(zone)) {
            _state.value = _state.value.copy(error = EditorError.END_BEFORE_START)
            return null
        }
        val cleaned = draft.copy(title = draft.title.trim())
        val id = eventId
        val resultId = if (id == null) {
            calendarRepository.createEvent(cleaned)
        } else {
            calendarRepository.updateEvent(id, cleaned, wholeSeries)
            id
        }
        syncCoordinator.pushChanges()
        return cleaned.calendarId to resultId
    }

    fun calendarName(id: String): String = _state.value.calendars.firstOrNull { it.id == id }?.summary.orEmpty()

    fun calendarColorHex(id: String): String? = _state.value.calendars.firstOrNull { it.id == id }?.colorHex

    val repeatOptions = RepeatRule.entries

}
