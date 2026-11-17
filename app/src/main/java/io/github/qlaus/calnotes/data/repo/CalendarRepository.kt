package io.github.qlaus.calnotes.data.repo

import androidx.room.withTransaction
import io.github.qlaus.calnotes.auth.CurrentAccount
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.local.CalendarEntity
import io.github.qlaus.calnotes.data.local.EventEntity
import io.github.qlaus.calnotes.data.local.EventWithCalendar
import io.github.qlaus.calnotes.data.local.PendingEventOpEntity
import io.github.qlaus.calnotes.data.remote.calendar.CalendarApi
import io.github.qlaus.calnotes.data.remote.calendar.EventDraft
import io.github.qlaus.calnotes.data.remote.calendar.EventMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Calendar access, offline-first: the UI observes the Room cache; refreshes and the
 * outbox of local changes run on top of it.
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val api: CalendarApi,
    private val db: AppDatabase,
    private val json: Json,
    private val clock: Clock,
    private val account: CurrentAccount,
    private val zones: ZoneProvider,
) {
    private val calendarDao = db.calendarDao()
    private val eventDao = db.eventDao()
    private val linkDao = db.eventLinkDao()
    private val opsDao = db.pendingEventOpDao()

    private val _lastOutboxError = MutableStateFlow<String?>(null)
    /** Message of the last outbox op Google rejected (it is dropped; the server state wins). */
    val lastOutboxError: StateFlow<String?> = _lastOutboxError.asStateFlow()

    val calendars: Flow<List<CalendarEntity>> = calendarDao.observeAll()
    val pendingOpCount: Flow<Int> = opsDao.observeCount()

    fun observeEvents(from: Long, to: Long): Flow<List<EventWithCalendar>> = eventDao.observeRange(from, to)

    fun observeAllVisibleEvents(): Flow<List<EventWithCalendar>> = eventDao.observeAllVisible()

    fun observeEvent(calendarId: String, eventId: String): Flow<EventWithCalendar?> =
        eventDao.observe(calendarId, eventId)

    suspend fun setCalendarVisible(id: String, visible: Boolean) = calendarDao.setVisible(id, visible)

    suspend fun refreshCalendars() {
        val entries = buildList {
            var page: String? = null
            do {
                val res = api.calendarList(page)
                addAll(res.items)
                page = res.nextPageToken
            } while (page != null)
        }
        val existing = calendarDao.getAll().associateBy { it.id }
        val rows = entries.filter { !it.hidden }.map { dto ->
            CalendarEntity(
                id = dto.id,
                accountId = account.accountId,
                summary = dto.summaryOverride ?: dto.summary ?: dto.id,
                colorHex = dto.backgroundColor ?: "#3B6FE0",
                accessRole = dto.accessRole,
                isPrimary = dto.primary,
                // Keep the user's in-app choice; first time follow Google's "selected" flag.
                visible = existing[dto.id]?.visible ?: (dto.selected || dto.primary),
            )
        }
        db.withTransaction {
            calendarDao.upsertAll(rows)
            calendarDao.deleteNotIn(rows.map { it.id })
            eventDao.deleteOrphans()
        }
    }

    /** Re-fetches all visible calendars' events overlapping [from, to) and replaces the cache for that range. */
    suspend fun refreshRange(from: Long, to: Long) {
        val zone = zones.zone()
        for (calendar in calendarDao.getAll().filter { it.visible }) {
            val fetched = buildList {
                var page: String? = null
                do {
                    val res = api.events(calendar.id, EventMapping.rfc3339(from), EventMapping.rfc3339(to), pageToken = page)
                    addAll(res.items)
                    page = res.nextPageToken
                } while (page != null)
            }
            val rows = fetched.filter { it.status != "cancelled" }
                .mapNotNull { EventMapping.toEntity(it, calendar.id, calendar.writable, zone) }
            val fetchedIds = rows.mapTo(HashSet()) { it.id }
            val pendingIds = opsDao.all().filter { it.calendarId == calendar.id }.mapTo(HashSet()) { it.eventId }

            db.withTransaction {
                val stale = eventDao.idsInRange(calendar.id, from, to)
                    .filter { it !in fetchedIds && it !in pendingIds && !it.startsWith(LOCAL_PREFIX) }
                if (stale.isNotEmpty()) eventDao.deleteIds(calendar.id, stale)
                eventDao.upsertAll(rows.filter { it.id !in pendingIds })
            }
            detectDeletedLinkedEvents(calendar.id, from, to, rows)
        }
    }

    /** Marks note links whose event disappeared on Google as "event deleted" (notes are kept). */
    private suspend fun detectDeletedLinkedEvents(calendarId: String, from: Long, to: Long, rows: List<EventEntity>) {
        val ids = rows.mapTo(HashSet()) { it.id }
        val seriesIds = rows.mapNotNullTo(HashSet()) { it.recurringEventId }
        val candidates = linkDao.activeForCalendar(calendarId).filter { link ->
            link.eventStartSnapshot in from until to && !link.eventId.startsWith(LOCAL_PREFIX) &&
                when (link.scope) {
                    EventLinkScope.INSTANCE -> link.eventId !in ids
                    EventLinkScope.SERIES ->
                        (link.recurringEventId ?: link.eventId) !in seriesIds
                }
        }
        for (link in candidates) {
            val targetId = if (link.scope == EventLinkScope.SERIES) {
                link.recurringEventId ?: link.eventId
            } else {
                link.eventId
            }
            val response = api.event(calendarId, targetId)
            val gone = response.code() == 404 || response.code() == 410 ||
                (response.isSuccessful && response.body()?.status == "cancelled")
            if (gone) linkDao.markEventDeleted(calendarId, targetId, clock.millis())
        }
    }

    /** Creates an event locally and queues it for Google. Returns the local (temporary) id. */
    suspend fun createEvent(draft: EventDraft): String {
        val zone = zones.zone()
        val localId = LOCAL_PREFIX + UUID.randomUUID().toString().replace("-", "")
        val payload = EventMapping.toPayload(draft, zone, includeTimes = true, includeRecurrence = true)
        db.withTransaction {
            eventDao.upsertAll(listOf(draft.toEntity(localId, zone, recurringEventId = null)))
            enqueue(TYPE_CREATE, draft.calendarId, localId, payload)
        }
        return localId
    }

    /**
     * Updates an event. With [wholeSeries] only text fields are changed on the series master
     * (times of a series are edited in Google Calendar).
     */
    suspend fun updateEvent(eventId: String, draft: EventDraft, wholeSeries: Boolean) {
        val zone = zones.zone()
        val current = eventDao.get(draft.calendarId, eventId) ?: return
        db.withTransaction {
            if (wholeSeries && current.recurringEventId != null) {
                val seriesId = current.recurringEventId
                val members = eventDao.seriesMembers(draft.calendarId, seriesId).map {
                    it.copy(title = draft.title, description = draft.description, location = draft.location, pendingSync = true)
                }
                eventDao.upsertAll(members)
                enqueue(TYPE_UPDATE, draft.calendarId, seriesId,
                    EventMapping.toPayload(draft, zone, includeTimes = false, includeRecurrence = false,
                        markerInTitle = current.markerInTitle, markerInDescription = current.markerInDescription))
            } else {
                eventDao.upsertAll(listOf(draft.toEntity(eventId, zone, current.recurringEventId).copy(
                    originalStartMillis = current.originalStartMillis,
                    colorId = current.colorId,
                    htmlLink = current.htmlLink,
                    markerInTitle = current.markerInTitle,
                    markerInDescription = current.markerInDescription,
                )))
                enqueue(TYPE_UPDATE, draft.calendarId, eventId,
                    EventMapping.toPayload(draft, zone, includeTimes = true, includeRecurrence = false,
                        markerInTitle = current.markerInTitle, markerInDescription = current.markerInDescription))
            }
        }
    }

    /** Deletes one occurrence, or the whole series when [wholeSeries]. Linked notes are kept and marked. */
    suspend fun deleteEvent(calendarId: String, eventId: String, wholeSeries: Boolean) {
        val current = eventDao.get(calendarId, eventId) ?: return
        val now = clock.millis()
        db.withTransaction {
            val targetId = if (wholeSeries && current.recurringEventId != null) current.recurringEventId else eventId
            if (targetId != eventId) eventDao.deleteSeries(calendarId, targetId) else eventDao.deleteIds(calendarId, listOf(eventId))
            linkDao.markEventDeleted(calendarId, targetId, now)
            if (targetId.startsWith(LOCAL_PREFIX)) {
                // Never reached Google: just drop the queued create/updates.
                opsDao.all().filter { it.calendarId == calendarId && it.eventId == targetId }.forEach { opsDao.delete(it.id) }
            } else {
                enqueue(TYPE_DELETE, calendarId, targetId, null)
            }
        }
    }

    /**
     * Sends queued changes in order. Returns false if it stopped early (offline/server error);
     * the remaining ops are retried on the next sync. Ops Google rejects (4xx) are dropped.
     */
    suspend fun flushOutbox(): Boolean {
        val zone = zones.zone()
        for (op in opsDao.all()) {
            try {
                when (op.type) {
                    TYPE_CREATE -> {
                        val created = api.insert(op.calendarId, payload(op))
                        val now = clock.millis()
                        db.withTransaction {
                            eventDao.replaceId(op.calendarId, op.eventId, created.id)
                            linkDao.remapEventId(op.calendarId, op.eventId, created.id, now)
                            linkDao.remapSeriesId(op.calendarId, op.eventId, created.id, now)
                            opsDao.remapEventId(op.calendarId, op.eventId, created.id)
                            db.notebookDao().remapEventId(op.calendarId, op.eventId, created.id)
                            if (created.recurrence.isNullOrEmpty()) {
                                writable(op.calendarId)?.let { w ->
                                    EventMapping.toEntity(created, op.calendarId, w, zone)?.let { eventDao.upsertAll(listOf(it)) }
                                }
                            } else {
                                // The local placeholder stands for a series master; the next refresh brings the instances.
                                eventDao.deleteIds(op.calendarId, listOf(created.id))
                            }
                            opsDao.delete(op.id)
                        }
                    }
                    TYPE_UPDATE -> {
                        val updated = api.patch(op.calendarId, op.eventId, payload(op))
                        db.withTransaction {
                            if (updated.recurrence.isNullOrEmpty()) {
                                writable(op.calendarId)?.let { w ->
                                    EventMapping.toEntity(updated, op.calendarId, w, zone)?.let { eventDao.upsertAll(listOf(it)) }
                                }
                            }
                            opsDao.delete(op.id)
                        }
                    }
                    TYPE_DELETE -> {
                        val res = api.delete(op.calendarId, op.eventId)
                        if (!res.isSuccessful && res.code() != 404 && res.code() != 410) throw HttpException(res)
                        opsDao.delete(op.id)
                    }
                    else -> opsDao.delete(op.id)
                }
            } catch (e: HttpException) {
                val code = e.code()
                if (code in 400..499 && code != 401 && code != 408 && code != 429) {
                    opsDao.delete(op.id)
                    _lastOutboxError.value = "HTTP $code"
                } else {
                    opsDao.recordFailure(op.id, "HTTP $code")
                    return false
                }
            } catch (e: IOException) {
                opsDao.recordFailure(op.id, e.javaClass.simpleName)
                return false
            }
        }
        return true
    }

    fun clearOutboxError() {
        _lastOutboxError.value = null
    }

    /** Removes cached calendar data (used on sign-out). Notes are kept. */
    suspend fun clearCache() = db.withTransaction {
        eventDao.clear()
        calendarDao.clear()
        opsDao.clear()
        db.eventMarkerDao().clear()
    }

    private suspend fun writable(calendarId: String): Boolean? =
        calendarDao.getAll().firstOrNull { it.id == calendarId }?.writable

    private fun payload(op: PendingEventOpEntity): JsonObject =
        json.decodeFromString(JsonObject.serializer(), requireNotNull(op.payloadJson))

    private suspend fun enqueue(type: String, calendarId: String, eventId: String, payload: JsonObject?) {
        opsDao.insert(
            PendingEventOpEntity(
                type = type,
                calendarId = calendarId,
                eventId = eventId,
                payloadJson = payload?.let { json.encodeToString(JsonObject.serializer(), it) },
                createdAt = clock.millis(),
            ),
        )
    }

    private fun EventDraft.toEntity(id: String, zone: ZoneId, recurringEventId: String?) = EventEntity(
        calendarId = calendarId,
        id = id,
        recurringEventId = recurringEventId,
        originalStartMillis = null,
        title = title,
        description = description,
        location = location,
        startMillis = startMillis(zone),
        endMillis = maxOf(endMillis(zone), startMillis(zone)),
        allDay = allDay,
        timeZone = zone.id,
        colorId = null,
        status = "confirmed",
        canEdit = true,
        htmlLink = null,
        updated = null,
        pendingSync = true,
    )

    companion object {
        const val LOCAL_PREFIX = "local"
        const val TYPE_CREATE = "CREATE"
        const val TYPE_UPDATE = "UPDATE"
        const val TYPE_DELETE = "DELETE"
    }
}
