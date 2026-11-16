package io.github.qlaus.calnotes.core.model

/**
 * Minimal identity of a Google Calendar event occurrence, as needed to find attached notes.
 *
 * @property eventId the event id; for an expanded recurring occurrence this is the instance id.
 * @property recurringEventId id of the series this occurrence belongs to, or null for single events.
 * @property originalStartMillis original start of the occurrence (UTC millis), null for single events.
 */
data class EventRef(
    val calendarId: String,
    val eventId: String,
    val recurringEventId: String? = null,
    val originalStartMillis: Long? = null,
)

/** The part of an EventLink needed for matching. */
data class LinkKey(
    val calendarId: String,
    val eventId: String,
    val recurringEventId: String?,
    val scope: EventLinkScope,
)

object EventLinkMatcher {
    /**
     * True when a link applies to the given occurrence: either it points at this exact
     * occurrence, or it is a series link for the series the occurrence belongs to.
     */
    fun matches(link: LinkKey, event: EventRef): Boolean {
        if (link.calendarId != event.calendarId) return false
        return when (link.scope) {
            EventLinkScope.INSTANCE -> link.eventId == event.eventId
            EventLinkScope.SERIES -> {
                val series = event.recurringEventId ?: event.eventId
                (link.recurringEventId ?: link.eventId) == series
            }
        }
    }
}
