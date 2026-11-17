package io.github.qlaus.calnotes.data.repo

import androidx.room.withTransaction
import io.github.qlaus.calnotes.auth.CurrentAccount
import io.github.qlaus.calnotes.core.model.EventLinkMatcher
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.core.model.EventRef
import io.github.qlaus.calnotes.core.model.LinkKey
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.local.EventEntity
import io.github.qlaus.calnotes.data.local.NotebookEntity
import io.github.qlaus.calnotes.data.local.NotebookLinkEntity
import io.github.qlaus.calnotes.data.local.NotebookWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun NotebookLinkEntity.toKey() = LinkKey(calendarId, eventId, recurringEventId, scope)

data class NotebookLinkWithNotebook(val link: NotebookLinkEntity, val notebook: NotebookEntity)

@Singleton
class NotebookRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val account: CurrentAccount,
) {
    private val dao = db.notebookDao()
    private val noteDao = db.noteDao()

    val notebooks: Flow<List<NotebookWithCount>> = dao.observeWithCounts()
    val activeNotebooks: Flow<List<NotebookEntity>> = dao.observeActive()

    fun observeNotebook(id: String): Flow<NotebookEntity?> = dao.observe(id).map { it?.takeIf { n -> n.deletedAt == null } }

    suspend fun create(title: String, color: String? = null): String {
        val now = clock.millis()
        val id = UUID.randomUUID().toString()
        dao.upsert(
            NotebookEntity(
                id = id,
                accountId = account.accountId,
                title = title.trim().ifEmpty { "Defter" },
                color = color,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                version = 1,
                dirty = true,
                remoteFileId = null,
            ),
        )
        return id
    }

    suspend fun update(id: String, title: String, color: String?) {
        val current = dao.get(id) ?: return
        val cleaned = title.trim().ifEmpty { current.title }
        if (cleaned == current.title && color == current.color) return
        dao.upsert(current.copy(title = cleaned, color = color, updatedAt = clock.millis(), version = current.version + 1, dirty = true))
    }

    /** Soft-deletes the notebook. Its notes are kept and become unfiled; event links to it are removed. */
    suspend fun delete(id: String) {
        val current = dao.get(id) ?: return
        val now = clock.millis()
        db.withTransaction {
            dao.upsert(current.copy(deletedAt = now, updatedAt = now, version = current.version + 1, dirty = true))
            dao.softDeleteLinksForNotebook(id, now)
            noteDao.activeInNotebook(id).forEach { note ->
                noteDao.upsert(note.copy(notebookId = null, updatedAt = now, version = note.version + 1, dirty = true))
            }
        }
    }

    /** The notebook an event occurrence is linked to (instance link wins over series link). */
    fun observeLinkFor(event: EventRef): Flow<NotebookLinkWithNotebook?> =
        combine(dao.observeActiveLinks(), dao.observeActive()) { links, notebooks ->
            val byId = notebooks.associateBy { it.id }
            links.filter { EventLinkMatcher.matches(it.toKey(), event) && byId.containsKey(it.notebookId) }
                .sortedBy { if (it.scope == EventLinkScope.INSTANCE) 0 else 1 }
                .firstOrNull()
                ?.let { NotebookLinkWithNotebook(it, byId.getValue(it.notebookId)) }
        }

    suspend fun notebookIdFor(event: EventRef): String? {
        val active = dao.activeLinks().filter { EventLinkMatcher.matches(it.toKey(), event) }
        val alive = active.filter { dao.get(it.notebookId)?.deletedAt == null }
        return alive.minByOrNull { if (it.scope == EventLinkScope.INSTANCE) 0 else 1 }?.notebookId
    }

    /** Links [event] (this occurrence or its whole series) to a notebook, replacing an earlier link. */
    suspend fun linkEvent(event: EventEntity, scope: EventLinkScope, notebookId: String) {
        val effectiveScope = if (event.recurringEventId == null) EventLinkScope.INSTANCE else scope
        val targetId = if (effectiveScope == EventLinkScope.SERIES) event.recurringEventId!! else event.id
        val now = clock.millis()
        db.withTransaction {
            dao.activeLinks()
                .filter { it.calendarId == event.calendarId && it.eventId == targetId && it.scope == effectiveScope }
                .forEach { dao.softDeleteLink(it.id, now) }
            dao.insertLink(
                NotebookLinkEntity(
                    id = UUID.randomUUID().toString(),
                    notebookId = notebookId,
                    accountId = account.accountId,
                    calendarId = event.calendarId,
                    eventId = targetId,
                    recurringEventId = event.recurringEventId,
                    scope = effectiveScope,
                    eventTitleSnapshot = event.title,
                    createdAt = now,
                    deletedAt = null,
                ),
            )
        }
    }

    suspend fun unlink(linkId: String) = dao.softDeleteLink(linkId, clock.millis())
}
