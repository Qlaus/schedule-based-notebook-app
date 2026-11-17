package io.github.qlaus.calnotes.data.repo

import io.github.qlaus.calnotes.core.calendar.CalendarMarker
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.local.EventMarkerEntity
import io.github.qlaus.calnotes.data.remote.calendar.CalendarApi
import io.github.qlaus.calnotes.data.remote.calendar.EventMapping
import io.github.qlaus.calnotes.data.settings.SettingsSource
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import retrofit2.HttpException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes "this event has notes" markers to Google Calendar: the optional title prefix, the optional
 * description block, and the note ids in `extendedProperties.private`. It compares the desired
 * state (from active note links + settings) with what it last wrote and patches only differences.
 * The user's own title/description text is never changed.
 */
@Singleton
class MarkerSync @Inject constructor(
    private val api: CalendarApi,
    private val db: AppDatabase,
    private val settings: SettingsSource,
    private val clock: Clock,
    private val zones: ZoneProvider,
) {
    private data class Target(val calendarId: String, val eventId: String)

    suspend fun reconcile() {
        val prefs = settings.current()
        val calendars = db.calendarDao().getAll().associateBy { it.id }
        val activeNoteIds = db.noteDao().observeAllActive().first().mapTo(HashSet()) { it.id }
        val desired = HashMap<Target, MutableList<String>>()
        for (link in db.eventLinkDao().activeLiving()) {
            if (link.noteId !in activeNoteIds) continue
            val id = if (link.scope == EventLinkScope.SERIES) link.recurringEventId ?: link.eventId else link.eventId
            if (id.startsWith(CalendarRepository.LOCAL_PREFIX)) continue // not on Google yet
            desired.getOrPut(Target(link.calendarId, id)) { mutableListOf() } += link.noteId
        }
        val existing = db.eventMarkerDao().all().associateBy { Target(it.calendarId, it.eventId) }

        for (target in desired.keys + existing.keys) {
            val calendar = calendars[target.calendarId] ?: continue
            val want = desired[target]
            val have = existing[target]
            val wantIds = want?.let { CalendarMarker.encodeNoteIds(it.distinct()) } ?: ""
            val wantTitle = want != null && prefs.titleMarker
            val wantDescription = want != null && prefs.descriptionMarker

            if (have != null && have.skipped && want != null) continue
            if (have != null && !have.skipped && have.noteIds == wantIds &&
                have.titleApplied == wantTitle && have.descriptionApplied == wantDescription
            ) continue
            if (have == null && want == null) continue

            apply(target, calendar.writable, wantTitle, wantDescription, wantIds, keepRow = want != null)
        }
    }

    private suspend fun apply(
        target: Target,
        calendarWritable: Boolean,
        title: Boolean,
        description: Boolean,
        noteIds: String,
        keepRow: Boolean,
    ) {
        val markerDao = db.eventMarkerDao()
        val response = api.event(target.calendarId, target.eventId)
        val dto = response.body()
        if (response.code() == 404 || response.code() == 410 || (response.isSuccessful && dto?.status == "cancelled")) {
            markerDao.delete(target.calendarId, target.eventId)
            return
        }
        if (!response.isSuccessful || dto == null) throw HttpException(response)

        val canEdit = EventMapping.toEntity(dto, target.calendarId, calendarWritable, zones.zone())?.canEdit == true
        if (!canEdit) {
            if (keepRow) markerDao.upsert(row(target, false, false, noteIds, skipped = true))
            else markerDao.delete(target.calendarId, target.eventId)
            return
        }

        val currentTitle = dto.summary.orEmpty()
        val currentDescription = dto.description.orEmpty()
        val newTitle = CalendarMarker.applyTitle(currentTitle, title)
        val newDescription = CalendarMarker.applyDescription(currentDescription, description)
        val body = buildJsonObject {
            if (newTitle != currentTitle) put("summary", newTitle)
            if (newDescription != currentDescription) put("description", newDescription)
            putJsonObject("extendedProperties") {
                putJsonObject("private") {
                    put(CalendarMarker.NOTE_IDS_PROPERTY, if (noteIds.isEmpty()) JsonNull else JsonPrimitive(noteIds))
                }
            }
        }
        try {
            api.patch(target.calendarId, target.eventId, body)
        } catch (e: HttpException) {
            if (e.code() == 403) {
                // No permission after all (e.g. attendee copy): remember and stop retrying.
                if (keepRow) markerDao.upsert(row(target, false, false, noteIds, skipped = true))
                return
            }
            throw e
        }
        if (keepRow) markerDao.upsert(row(target, title, description, noteIds, skipped = false))
        else markerDao.delete(target.calendarId, target.eventId)
        // Keep cached flags in step so in-app edits preserve the markers.
        db.eventDao().setMarkerFlags(target.calendarId, target.eventId, title, description)
    }

    private fun row(target: Target, title: Boolean, description: Boolean, noteIds: String, skipped: Boolean) =
        EventMarkerEntity(target.calendarId, target.eventId, title, description, noteIds, skipped, clock.millis())
}
