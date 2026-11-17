package io.github.qlaus.calnotes.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.qlaus.calnotes.core.model.EventLinkScope

// No foreign-key constraints on purpose: rows are soft-deleted (tombstones) and may arrive
// out of order during sync, so integrity is handled in the repositories.

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val title: String,
    val color: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val version: Long,
    val dirty: Boolean,
    val remoteFileId: String?,
)

@Entity(tableName = "notes", indices = [Index("notebookId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val notebookId: String?,
    val title: String,
    /** Markdown body (plain text in Phase 1). */
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val version: Long,
    val dirty: Boolean,
    val remoteFileId: String?,
    val remoteVersion: Long?,
)

@Entity(
    tableName = "event_links",
    indices = [Index("noteId"), Index("calendarId", "eventId"), Index("calendarId", "recurringEventId")],
)
data class EventLinkEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val accountId: String,
    val calendarId: String,
    val eventId: String,
    val recurringEventId: String?,
    val instanceStartTime: Long?,
    val scope: EventLinkScope,
    val eventTitleSnapshot: String,
    val eventStartSnapshot: Long,
    val eventDeleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** A version of a note that was overwritten by sync conflict resolution (Phase 3). */
@Entity(tableName = "note_backups", indices = [Index("noteId")])
data class NoteBackupEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val version: Long,
    val title: String,
    val body: String,
    val metaJson: String,
    val reason: String,
    val createdAt: Long,
)

@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val summary: String,
    val colorHex: String,
    /** owner | writer | reader | freeBusyReader */
    val accessRole: String,
    val isPrimary: Boolean,
    val visible: Boolean,
) {
    val writable: Boolean get() = accessRole == "owner" || accessRole == "writer"
}

/** Offline cache of one expanded event occurrence. Times are UTC epoch millis. */
@Entity(
    tableName = "events",
    primaryKeys = ["calendarId", "id"],
    indices = [Index("startMillis"), Index("calendarId", "recurringEventId")],
)
data class EventEntity(
    val calendarId: String,
    val id: String,
    val recurringEventId: String?,
    val originalStartMillis: Long?,
    val title: String,
    val description: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val timeZone: String?,
    val colorId: String?,
    val status: String,
    val canEdit: Boolean,
    val htmlLink: String?,
    val updated: String?,
    /** True while a local change to this event is waiting in the outbox. */
    val pendingSync: Boolean,
) {
    val isRecurring: Boolean get() = recurringEventId != null
}

data class EventWithCalendar(
    @Embedded val event: EventEntity,
    val calendarName: String,
    val calendarColor: String,
)

/** Outbox entry for an event change made in the app; flushed to Google Calendar in order. */
@Entity(tableName = "pending_event_ops")
data class PendingEventOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** CREATE | UPDATE | DELETE */
    val type: String,
    val calendarId: String,
    val eventId: String,
    val payloadJson: String?,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)
