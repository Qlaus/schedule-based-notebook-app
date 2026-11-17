package io.github.qlaus.calnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendars ORDER BY isPrimary DESC, summary COLLATE NOCASE")
    fun observeAll(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars")
    suspend fun getAll(): List<CalendarEntity>

    @Upsert
    suspend fun upsertAll(calendars: List<CalendarEntity>)

    @Query("DELETE FROM calendars WHERE id NOT IN (:keepIds)")
    suspend fun deleteNotIn(keepIds: List<String>)

    @Query("UPDATE calendars SET visible = :visible WHERE id = :id")
    suspend fun setVisible(id: String, visible: Boolean)

    @Query("DELETE FROM calendars")
    suspend fun clear()
}

@Dao
interface EventDao {
    @Query(
        """SELECT e.*, c.summary AS calendarName, c.colorHex AS calendarColor
           FROM events e JOIN calendars c ON c.id = e.calendarId
           WHERE c.visible = 1 AND e.status != 'cancelled'
             AND e.startMillis < :to AND e.endMillis > :from
           ORDER BY e.allDay DESC, e.startMillis, e.endMillis DESC"""
    )
    fun observeRange(from: Long, to: Long): Flow<List<EventWithCalendar>>

    @Query(
        """SELECT e.*, c.summary AS calendarName, c.colorHex AS calendarColor
           FROM events e JOIN calendars c ON c.id = e.calendarId
           WHERE e.calendarId = :calendarId AND e.id = :id"""
    )
    fun observe(calendarId: String, id: String): Flow<EventWithCalendar?>

    @Query(
        """SELECT e.*, c.summary AS calendarName, c.colorHex AS calendarColor
           FROM events e JOIN calendars c ON c.id = e.calendarId
           WHERE c.visible = 1 AND e.status != 'cancelled' ORDER BY e.startMillis"""
    )
    fun observeAllVisible(): Flow<List<EventWithCalendar>>

    @Query("SELECT * FROM events WHERE calendarId = :calendarId AND id = :id")
    suspend fun get(calendarId: String, id: String): EventEntity?

    @Query("SELECT id FROM events WHERE calendarId = :calendarId AND startMillis < :to AND endMillis > :from")
    suspend fun idsInRange(calendarId: String, from: Long, to: Long): List<String>

    @Query("SELECT * FROM events WHERE calendarId = :calendarId AND (id = :seriesId OR recurringEventId = :seriesId)")
    suspend fun seriesMembers(calendarId: String, seriesId: String): List<EventEntity>

    @Upsert
    suspend fun upsertAll(events: List<EventEntity>)

    @Query("DELETE FROM events WHERE calendarId = :calendarId AND id IN (:ids)")
    suspend fun deleteIds(calendarId: String, ids: List<String>)

    @Query("DELETE FROM events WHERE calendarId = :calendarId AND (id = :seriesId OR recurringEventId = :seriesId)")
    suspend fun deleteSeries(calendarId: String, seriesId: String)

    @Query("UPDATE events SET id = :newId, pendingSync = 0 WHERE calendarId = :calendarId AND id = :oldId")
    suspend fun replaceId(calendarId: String, oldId: String, newId: String)

    @Query(
        """UPDATE events SET markerInTitle = :title, markerInDescription = :description
           WHERE calendarId = :calendarId AND (id = :eventId OR recurringEventId = :eventId)"""
    )
    suspend fun setMarkerFlags(calendarId: String, eventId: String, title: Boolean, description: Boolean)

    @Query("DELETE FROM events WHERE calendarId NOT IN (SELECT id FROM calendars)")
    suspend fun deleteOrphans()

    @Query("DELETE FROM events")
    suspend fun clear()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND id IN (:ids) ORDER BY createdAt, rowid")
    fun observeActive(ids: List<String>): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL")
    fun observeAllActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND notebookId = :notebookId")
    fun observeInNotebook(notebookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND notebookId IS NULL")
    fun observeUnfiled(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND notebookId = :notebookId")
    suspend fun activeInNotebook(notebookId: String): List<NoteEntity>

    /** Every note including tombstones (for sync). */
    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    /**
     * Records an upload of [version]. The note stays dirty if it was edited while uploading
     * (its version moved on), so the newer edit is sent next time.
     */
    @Query(
        """UPDATE notes SET remoteVersion = :version, remoteFileId = :fileId,
           dirty = CASE WHEN version = :version THEN 0 ELSE dirty END WHERE id = :id"""
    )
    suspend fun markSynced(id: String, version: Long, fileId: String?): Int

    /** Bumps notes whose links changed outside the editor, so the change reaches the cloud. */
    @Query("UPDATE notes SET dirty = 1, version = version + 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun touch(ids: List<String>, now: Long)
}

@Dao
interface NoteBackupDao {
    @Insert
    suspend fun insert(backup: NoteBackupEntity)

    @Query("SELECT * FROM note_backups WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun observeForNote(noteId: String): Flow<List<NoteBackupEntity>>

    @Query("DELETE FROM note_backups WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface EventLinkDao {
    @Query("SELECT * FROM event_links WHERE deletedAt IS NULL")
    fun observeActive(): Flow<List<EventLinkEntity>>

    @Query("SELECT * FROM event_links WHERE deletedAt IS NULL AND noteId = :noteId")
    fun observeForNote(noteId: String): Flow<List<EventLinkEntity>>

    @Query("SELECT * FROM event_links WHERE deletedAt IS NULL AND calendarId = :calendarId AND eventDeleted = 0")
    suspend fun activeForCalendar(calendarId: String): List<EventLinkEntity>

    @Insert
    suspend fun insert(link: EventLinkEntity)

    @Query("UPDATE event_links SET eventId = :newId, updatedAt = :now WHERE calendarId = :calendarId AND eventId = :oldId")
    suspend fun remapEventId(calendarId: String, oldId: String, newId: String, now: Long)

    @Query("UPDATE event_links SET recurringEventId = :newId, updatedAt = :now WHERE calendarId = :calendarId AND recurringEventId = :oldId")
    suspend fun remapSeriesId(calendarId: String, oldId: String, newId: String, now: Long)

    @Query(
        """UPDATE event_links SET eventDeleted = 1, updatedAt = :now
           WHERE calendarId = :calendarId AND (eventId = :eventId OR (scope = 'SERIES' AND recurringEventId = :eventId))"""
    )
    suspend fun markEventDeleted(calendarId: String, eventId: String, now: Long)

    @Query("UPDATE event_links SET deletedAt = :now, updatedAt = :now WHERE noteId = :noteId")
    suspend fun softDeleteForNote(noteId: String, now: Long)

    @Query("SELECT * FROM event_links WHERE deletedAt IS NULL AND eventDeleted = 0")
    suspend fun activeLiving(): List<EventLinkEntity>

    /** All links of a note including tombstones (for sync). */
    @Query("SELECT * FROM event_links WHERE noteId = :noteId")
    suspend fun allForNote(noteId: String): List<EventLinkEntity>

    @Query("DELETE FROM event_links WHERE noteId = :noteId")
    suspend fun deleteAllForNote(noteId: String)

    @Upsert
    suspend fun upsertAll(links: List<EventLinkEntity>)

    @Query("SELECT DISTINCT noteId FROM event_links WHERE calendarId = :calendarId AND (eventId = :eventId OR (scope = 'SERIES' AND recurringEventId = :eventId))")
    suspend fun noteIdsForEvent(calendarId: String, eventId: String): List<String>

    @Query("SELECT DISTINCT noteId FROM event_links WHERE calendarId = :calendarId AND (eventId = :eventId OR recurringEventId = :eventId)")
    suspend fun noteIdsReferencing(calendarId: String, eventId: String): List<String>
}

@Dao
interface NotebookDao {
    @Query(
        """SELECT n.*, (SELECT COUNT(*) FROM notes WHERE notes.notebookId = n.id AND notes.deletedAt IS NULL) AS noteCount
           FROM notebooks n WHERE n.deletedAt IS NULL ORDER BY n.title COLLATE NOCASE"""
    )
    fun observeWithCounts(): Flow<List<NotebookWithCount>>

    @Query("SELECT * FROM notebooks WHERE deletedAt IS NULL ORDER BY title COLLATE NOCASE")
    fun observeActive(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    fun observe(id: String): Flow<NotebookEntity?>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun get(id: String): NotebookEntity?

    @Upsert
    suspend fun upsert(notebook: NotebookEntity)

    @Query("SELECT * FROM notebook_links WHERE deletedAt IS NULL")
    fun observeActiveLinks(): Flow<List<NotebookLinkEntity>>

    @Query("SELECT * FROM notebook_links WHERE deletedAt IS NULL")
    suspend fun activeLinks(): List<NotebookLinkEntity>

    @Insert
    suspend fun insertLink(link: NotebookLinkEntity)

    @Query("UPDATE notebook_links SET deletedAt = :now WHERE id = :id")
    suspend fun softDeleteLink(id: String, now: Long)

    @Query("UPDATE notebook_links SET deletedAt = :now WHERE notebookId = :notebookId")
    suspend fun softDeleteLinksForNotebook(notebookId: String, now: Long)

    @Query("UPDATE notebook_links SET eventId = :newId WHERE calendarId = :calendarId AND eventId = :oldId")
    suspend fun remapEventId(calendarId: String, oldId: String, newId: String)

    @Query("SELECT DISTINCT notebookId FROM notebook_links WHERE calendarId = :calendarId AND eventId = :eventId")
    suspend fun notebookIdsReferencing(calendarId: String, eventId: String): List<String>

    @Query("SELECT * FROM notebooks")
    suspend fun all(): List<NotebookEntity>

    @Query("SELECT * FROM notebook_links WHERE notebookId = :notebookId")
    suspend fun allLinksFor(notebookId: String): List<NotebookLinkEntity>

    @Query("DELETE FROM notebook_links WHERE notebookId = :notebookId")
    suspend fun deleteAllLinksFor(notebookId: String)

    @Upsert
    suspend fun upsertLinks(links: List<NotebookLinkEntity>)

    @Query("SELECT COUNT(*) FROM notebooks WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query(
        """UPDATE notebooks SET remoteVersion = :version, remoteFileId = :fileId,
           dirty = CASE WHEN version = :version THEN 0 ELSE dirty END WHERE id = :id"""
    )
    suspend fun markSynced(id: String, version: Long, fileId: String?): Int

    @Query("UPDATE notebooks SET dirty = 1, version = version + 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun touch(ids: List<String>, now: Long)
}

@Dao
interface EventMarkerDao {
    @Query("SELECT * FROM event_markers")
    suspend fun all(): List<EventMarkerEntity>

    @Upsert
    suspend fun upsert(marker: EventMarkerEntity)

    @Query("DELETE FROM event_markers WHERE calendarId = :calendarId AND eventId = :eventId")
    suspend fun delete(calendarId: String, eventId: String)

    @Query("DELETE FROM event_markers")
    suspend fun clear()
}

@Dao
interface PendingEventOpDao {
    @Query("SELECT * FROM pending_event_ops ORDER BY id")
    suspend fun all(): List<PendingEventOpEntity>

    @Query("SELECT COUNT(*) FROM pending_event_ops")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(op: PendingEventOpEntity): Long

    @Query("DELETE FROM pending_event_ops WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_event_ops SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun recordFailure(id: Long, error: String)

    @Query("UPDATE pending_event_ops SET eventId = :newId WHERE calendarId = :calendarId AND eventId = :oldId")
    suspend fun remapEventId(calendarId: String, oldId: String, newId: String)

    @Query("DELETE FROM pending_event_ops")
    suspend fun clear()
}

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: SyncStateEntity)
}
