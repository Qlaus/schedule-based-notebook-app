package io.github.qlaus.calnotes.data.remote.calendar

import kotlinx.serialization.Serializable

@Serializable
data class CalendarListResponse(
    val items: List<CalendarListEntryDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class CalendarListEntryDto(
    val id: String,
    val summary: String? = null,
    val summaryOverride: String? = null,
    val backgroundColor: String? = null,
    val accessRole: String = "reader",
    val primary: Boolean = false,
    val selected: Boolean = false,
    val hidden: Boolean = false,
)

@Serializable
data class EventsResponse(
    val items: List<EventDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class EventDto(
    val id: String,
    val status: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val location: String? = null,
    val start: EventDateTimeDto? = null,
    val end: EventDateTimeDto? = null,
    val recurringEventId: String? = null,
    val originalStartTime: EventDateTimeDto? = null,
    val recurrence: List<String>? = null,
    val colorId: String? = null,
    val organizer: PersonDto? = null,
    val guestsCanModify: Boolean? = null,
    val eventType: String? = null,
    val htmlLink: String? = null,
    val updated: String? = null,
)

@Serializable
data class EventDateTimeDto(
    val date: String? = null,
    val dateTime: String? = null,
    val timeZone: String? = null,
)

@Serializable
data class PersonDto(
    val email: String? = null,
    val self: Boolean? = null,
)
