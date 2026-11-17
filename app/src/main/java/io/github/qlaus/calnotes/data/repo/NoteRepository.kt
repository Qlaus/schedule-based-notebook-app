package io.github.qlaus.calnotes.data.repo

import androidx.room.withTransaction
import io.github.qlaus.calnotes.auth.CurrentAccount
import io.github.qlaus.calnotes.core.model.EventLinkMatcher
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.core.model.EventRef
import io.github.qlaus.calnotes.core.model.LinkKey
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.local.EventEntity
import io.github.qlaus.calnotes.data.local.EventLinkEntity
import io.github.qlaus.calnotes.data.local.NoteEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class NoteWithLinks(val note: NoteEntity, val links: List<EventLinkEntity>)

fun EventEntity.toRef() = EventRef(calendarId, id, recurringEventId, originalStartMillis)

fun EventLinkEntity.toKey() = LinkKey(calendarId, eventId, recurringEventId, scope)

/** Local notes (Room only in Phase 1). All writes bump version and mark the row dirty for later sync. */
@Singleton
class NoteRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val account: CurrentAccount,
) {
    private val noteDao = db.noteDao()
    private val linkDao = db.eventLinkDao()

    /** All active links; the calendar uses this to show which events have notes. */
    val activeLinks: Flow<List<EventLinkEntity>> = linkDao.observeActive()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNotesForEvent(event: EventRef): Flow<List<NoteEntity>> =
        linkDao.observeActive()
            .map { links -> links.filter { EventLinkMatcher.matches(it.toKey(), event) }.map { it.noteId }.distinct() }
            .flatMapLatest { ids -> noteDao.observeActive(ids) }

    fun observeNote(noteId: String): Flow<NoteWithLinks?> =
        combine(noteDao.observe(noteId), linkDao.observeForNote(noteId)) { note, links ->
            note?.takeIf { it.deletedAt == null }?.let { NoteWithLinks(it, links) }
        }

    /** Creates an empty note attached to [event] (this occurrence or its whole series). Returns the note id. */
    suspend fun createForEvent(event: EventEntity, scope: EventLinkScope, title: String, notebookId: String? = null): String {
        val now = clock.millis()
        val noteId = UUID.randomUUID().toString()
        val effectiveScope = if (event.recurringEventId == null) EventLinkScope.INSTANCE else scope
        db.withTransaction {
            noteDao.upsert(
                NoteEntity(
                    id = noteId,
                    accountId = account.accountId,
                    notebookId = notebookId,
                    title = title,
                    body = "",
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    version = 1,
                    dirty = true,
                    remoteFileId = null,
                    remoteVersion = null,
                ),
            )
            linkDao.insert(
                EventLinkEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    accountId = account.accountId,
                    calendarId = event.calendarId,
                    eventId = if (effectiveScope == EventLinkScope.SERIES) event.recurringEventId!! else event.id,
                    recurringEventId = event.recurringEventId,
                    instanceStartTime = if (effectiveScope == EventLinkScope.INSTANCE) event.originalStartMillis ?: event.startMillis else null,
                    scope = effectiveScope,
                    eventTitleSnapshot = event.title,
                    eventStartSnapshot = event.startMillis,
                    eventDeleted = false,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            )
        }
        return noteId
    }

    /** Creates a note that is not attached to any event, optionally inside a notebook. */
    suspend fun createStandalone(notebookId: String?, title: String = ""): String {
        val now = clock.millis()
        val id = UUID.randomUUID().toString()
        noteDao.upsert(
            NoteEntity(
                id = id, accountId = account.accountId, notebookId = notebookId, title = title, body = "",
                createdAt = now, updatedAt = now, deletedAt = null, version = 1, dirty = true,
                remoteFileId = null, remoteVersion = null,
            ),
        )
        return id
    }

    /** Moves a note into a notebook, or out of any notebook with null. */
    suspend fun moveToNotebook(noteId: String, notebookId: String?) {
        val current = noteDao.get(noteId) ?: return
        if (current.notebookId == notebookId) return
        noteDao.upsert(current.copy(notebookId = notebookId, updatedAt = clock.millis(), version = current.version + 1, dirty = true))
    }

    /** Notes in a notebook (or unfiled with null), newest event/creation first. */
    fun observeNotesIn(notebookId: String?): Flow<List<NoteWithLinks>> {
        val notes = if (notebookId == null) noteDao.observeUnfiled() else noteDao.observeInNotebook(notebookId)
        return combine(notes, linkDao.observeActive()) { list, links ->
            val byNote = links.groupBy { it.noteId }
            list.map { NoteWithLinks(it, byNote[it.id].orEmpty()) }
                .sortedByDescending { n -> n.links.maxOfOrNull { it.eventStartSnapshot } ?: n.note.createdAt }
        }
    }

    /** All active notes with their links (for search). */
    val allNotesWithLinks: Flow<List<NoteWithLinks>> =
        combine(noteDao.observeAllActive(), linkDao.observeActive()) { list, links ->
            val byNote = links.groupBy { it.noteId }
            list.map { NoteWithLinks(it, byNote[it.id].orEmpty()) }
        }

    /** Saves title/body. No-op if nothing changed, so autosave does not bump versions needlessly. */
    suspend fun updateContent(noteId: String, title: String, body: String) {
        val current = noteDao.get(noteId) ?: return
        if (current.deletedAt != null || (current.title == title && current.body == body)) return
        noteDao.upsert(
            current.copy(title = title, body = body, updatedAt = clock.millis(), version = current.version + 1, dirty = true),
        )
    }

    /** Soft delete (tombstone) of the note and its links. */
    suspend fun delete(noteId: String) {
        val current = noteDao.get(noteId) ?: return
        val now = clock.millis()
        db.withTransaction {
            noteDao.upsert(current.copy(deletedAt = now, updatedAt = now, version = current.version + 1, dirty = true))
            linkDao.softDeleteForNote(noteId, now)
        }
    }
}
