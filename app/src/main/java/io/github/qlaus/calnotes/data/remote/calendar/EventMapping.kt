package io.github.qlaus.calnotes.data.remote.calendar

import io.github.qlaus.calnotes.data.local.EventEntity
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import io.github.qlaus.calnotes.core.calendar.CalendarMarker
import io.github.qlaus.calnotes.core.calendar.RepeatRule
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** What the user edits in the event editor. End date/time is inclusive for all-day events. */
data class EventDraft(
    val calendarId: String,
    val title: String,
    val description: String,
    val location: String,
    val allDay: Boolean,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val repeat: RepeatRule = RepeatRule.NONE,
) {
    fun startMillis(zone: ZoneId): Long =
        if (allDay) startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        else ZonedDateTime.of(startDate, startTime, zone).toInstant().toEpochMilli()

    fun endMillis(zone: ZoneId): Long =
        if (allDay) endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        else ZonedDateTime.of(endDate, endTime, zone).toInstant().toEpochMilli()
}

object EventMapping {

    fun parseMillis(dt: EventDateTimeDto?, zone: ZoneId): Long? = when {
        dt == null -> null
        dt.dateTime != null -> runCatching { OffsetDateTime.parse(dt.dateTime).toInstant().toEpochMilli() }.getOrNull()
        dt.date != null -> runCatching { LocalDate.parse(dt.date).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
        else -> null
    }

    /** Converts an API event into a cache row, or null if it has no usable start/end. */
    fun toEntity(dto: EventDto, calendarId: String, calendarWritable: Boolean, zone: ZoneId): EventEntity? {
        val start = parseMillis(dto.start, zone) ?: return null
        val end = parseMillis(dto.end, zone) ?: start
        val organizerIsUs = dto.organizer == null || dto.organizer.self == true || dto.organizer.email == calendarId
        val readOnlyType = dto.eventType == "birthday" || dto.eventType == "fromGmail"
        val title = CalendarMarker.stripTitle(dto.summary.orEmpty())
        val description = CalendarMarker.stripDescription(dto.description.orEmpty())
        return EventEntity(
            calendarId = calendarId,
            id = dto.id,
            recurringEventId = dto.recurringEventId,
            originalStartMillis = parseMillis(dto.originalStartTime, zone),
            title = title.text,
            description = description.text,
            location = dto.location.orEmpty(),
            startMillis = start,
            endMillis = maxOf(end, start),
            allDay = dto.start?.date != null,
            timeZone = dto.start?.timeZone,
            colorId = dto.colorId,
            status = dto.status ?: "confirmed",
            canEdit = calendarWritable && !readOnlyType && (organizerIsUs || dto.guestsCanModify == true),
            htmlLink = dto.htmlLink,
            updated = dto.updated,
            pendingSync = false,
            markerInTitle = title.hadMarker,
            markerInDescription = description.hadMarker,
        )
    }

    /**
     * Builds the JSON body for insert/patch. Unused date fields are sent as explicit nulls so a PATCH
     * can switch between all-day and timed events. [includeTimes] = false patches text fields only
     * (used for "whole series" edits).
     */
    fun toPayload(
        draft: EventDraft,
        zone: ZoneId,
        includeTimes: Boolean,
        includeRecurrence: Boolean,
        markerInTitle: Boolean = false,
        markerInDescription: Boolean = false,
    ): JsonObject =
        buildJsonObject {
            // Keep our note markers when the user edits the text (the cache stores text without them).
            put("summary", CalendarMarker.applyTitle(draft.title, markerInTitle))
            put("description", CalendarMarker.applyDescription(draft.description, markerInDescription))
            put("location", draft.location)
            if (includeTimes) {
                putJsonObject("start") { putDateTime(draft.allDay, draft.startDate, draft.startTime, zone, isEnd = false) }
                putJsonObject("end") { putDateTime(draft.allDay, draft.endDate, draft.endTime, zone, isEnd = true) }
            }
            if (includeRecurrence) {
                draft.repeat.toRecurrence()?.let { rules -> putJsonArray("recurrence") { rules.forEach { add(JsonPrimitive(it)) } } }
            }
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putDateTime(
        allDay: Boolean,
        date: LocalDate,
        time: LocalTime,
        zone: ZoneId,
        isEnd: Boolean,
    ) {
        if (allDay) {
            // Google all-day end dates are exclusive.
            put("date", (if (isEnd) date.plusDays(1) else date).toString())
            put("dateTime", JsonNull)
        } else {
            put("date", JsonNull)
            put("dateTime", ZonedDateTime.of(date, time, zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }
        put("timeZone", zone.id)
    }

    fun rfc3339(millis: Long): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(java.time.Instant.ofEpochMilli(millis).atOffset(java.time.ZoneOffset.UTC))
}
