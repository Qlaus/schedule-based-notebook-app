package io.github.qlaus.calnotes.core.calendar

/** Simple recurrence choices offered when creating an event. */
enum class RepeatRule(val rrule: String?) {
    NONE(null),
    DAILY("RRULE:FREQ=DAILY"),
    WEEKLY("RRULE:FREQ=WEEKLY"),
    MONTHLY("RRULE:FREQ=MONTHLY"),
    YEARLY("RRULE:FREQ=YEARLY");

    /** Value for the Google Calendar `recurrence` field, or null for a single event. */
    fun toRecurrence(): List<String>? = rrule?.let { listOf(it) }
}
