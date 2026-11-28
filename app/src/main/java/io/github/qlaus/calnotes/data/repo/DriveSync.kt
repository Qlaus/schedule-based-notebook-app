package io.github.qlaus.calnotes.data.repo

import androidx.room.withTransaction
import io.github.qlaus.calnotes.auth.CurrentAccount
import io.github.qlaus.calnotes.core.format.NoteFileV1
import io.github.qlaus.calnotes.core.format.NoteFormat
import io.github.qlaus.calnotes.core.format.NoteLinkV1
import io.github.qlaus.calnotes.core.format.NotebookFileV1
import io.github.qlaus.calnotes.core.format.NotebookLinkV1
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.core.sync.LocalState
import io.github.qlaus.calnotes.core.sync.RemoteState
import io.github.qlaus.calnotes.core.sync.SyncAction
import io.github.qlaus.calnotes.core.sync.SyncDecision
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.local.EventLinkEntity
import io.github.qlaus.calnotes.data.local.NoteBackupEntity
import io.github.qlaus.calnotes.data.local.NoteEntity
import io.github.qlaus.calnotes.data.local.NotebookEntity
import io.github.qlaus.calnotes.data.local.NotebookLinkEntity
import io.github.qlaus.calnotes.data.remote.drive.DriveApi
import io.github.qlaus.calnotes.data.remote.drive.DriveFile
import io.github.qlaus.calnotes.data.remote.drive.DriveFileMetadata
import kotlinx.serialization.json.Json
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Counts of what one sync run did (for tests and the status line). */
data class DriveSyncResult(val uploaded: Int = 0, val downloaded: Int = 0, val conflicts: Int = 0, val skipped: Int = 0)

/**
 * Two-way sync of notes and notebooks with the Google Drive appDataFolder (format: docs/note-format.md).
 *
 * Remote state comes from one file listing with appProperties (kind, id, version, updatedAt), so
 * unchanged items cost no downloads. Each item is resolved with [SyncDecision]; conflict losers are
 * stored in note_backups. Deletes travel as tombstones. Uploads write the body before the metadata,
 * and the metadata carries the body hash, so half-finished uploads are detected and retried.
 */
@Singleton
class DriveSync @Inject constructor(
    private val api: DriveApi,
    private val db: AppDatabase,
    private val json: Json,
    private val clock: Clock,
    private val account: CurrentAccount,
) {
    private val noteDao = db.noteDao()
    private val linkDao = db.eventLinkDao()
    private val notebookDao = db.notebookDao()
    private val backupDao = db.noteBackupDao()

    private data class Remote(val meta: DriveFile, val body: DriveFile?, val ink: DriveFile?, val version: Long, val updatedAt: Long)

    suspend fun sync(): DriveSyncResult {
        val files = listAll()
        val byName = files.groupBy { it.name.orEmpty() }
        fun pick(name: String) = byName[name]?.maxByOrNull { it.appProperties?.get(P_VERSION)?.toLongOrNull() ?: -1 }

        var result = DriveSyncResult()

        // Notebooks first, so notes can refer to them.
        val remoteNotebooks = files.filter { it.appProperties?.get(P_KIND) == KIND_NOTEBOOK }
            .mapNotNull { f -> f.appProperties?.get(P_ID)?.let { it to f } }.toMap()
            .mapValues { (id, _) -> pick(NoteFormat.notebookName(id))!! }
        val localNotebooks = notebookDao.all().associateBy { it.id }
        for (id in remoteNotebooks.keys + localNotebooks.keys) {
            result = result + syncNotebook(localNotebooks[id], remoteNotebooks[id]?.let { remoteOf(it, null, null) }, id)
        }

        val remoteNotes = files.filter { it.appProperties?.get(P_KIND) == KIND_NOTE }
            .mapNotNull { f -> f.appProperties?.get(P_ID) }.distinct()
            .associateWith { id ->
                pick(NoteFormat.noteMetaName(id))?.let { remoteOf(it, pick(NoteFormat.noteBodyName(id)), pick(NoteFormat.noteInkName(id))) }
            }
        val localNotes = noteDao.all().associateBy { it.id }
        for (id in remoteNotes.keys + localNotes.keys) {
            result = result + syncNote(localNotes[id], remoteNotes[id], id)
        }
        return result
    }

    // --- notes ---

    private suspend fun syncNote(local: NoteEntity?, remote: Remote?, id: String): DriveSyncResult {
        val localState = local?.let { LocalState(it.version, it.updatedAt, it.dirty, it.remoteVersion) }
        val remoteState = remote?.let { RemoteState(it.version, it.updatedAt) }
        val action = SyncDecision.decide(localState, remoteState)
        return when (action) {
            SyncAction.NONE -> DriveSyncResult()
            SyncAction.UPLOAD -> { uploadNote(local!!, remote, local.version); DriveSyncResult(uploaded = 1) }
            SyncAction.DOWNLOAD -> if (downloadNote(remote!!, backupOf = null)) DriveSyncResult(downloaded = 1) else DriveSyncResult(skipped = 1)
            SyncAction.CONFLICT_KEEP_LOCAL -> {
                backupRemoteNote(remote!!, id)
                val version = SyncDecision.uploadVersion(localState!!, remoteState, action)
                val bumped = local!!.copy(version = version)
                noteDao.upsert(bumped)
                uploadNote(bumped, remote, version)
                DriveSyncResult(uploaded = 1, conflicts = 1)
            }
            SyncAction.CONFLICT_TAKE_REMOTE ->
                if (downloadNote(remote!!, backupOf = local)) DriveSyncResult(downloaded = 1, conflicts = 1) else DriveSyncResult(skipped = 1)
        }
    }

    private suspend fun uploadNote(note: NoteEntity, remote: Remote?, version: Long) {
        val links = linkDao.allForNote(note.id)
        val body = if (note.deletedAt != null) "" else note.body
        val ink = note.ink?.takeIf { note.deletedAt == null && it.isNotBlank() }
        val file = NoteFileV1(
            formatVersion = NoteFormat.formatVersionFor(ink != null),
            id = note.id,
            notebookId = note.notebookId,
            title = note.title,
            createdAt = NoteFormat.iso(note.createdAt),
            updatedAt = NoteFormat.iso(note.updatedAt),
            version = version,
            deletedAt = NoteFormat.isoOrNull(note.deletedAt),
            bodySha256 = NoteFormat.sha256(body),
            inkSha256 = ink?.let { NoteFormat.sha256(it) },
            links = links.map { it.toFile() },
        )
        val props = props(KIND_NOTE, note.id, version, note.updatedAt, note.deletedAt != null, file.formatVersion)
        // Body (and ink) first, metadata last: the metadata's hashes tell readers whether the rest is complete.
        upsertFile(remote?.body?.id, NoteFormat.noteBodyName(note.id), props, body, "text/markdown; charset=UTF-8")
        if (ink != null) upsertFile(remote?.ink?.id, NoteFormat.noteInkName(note.id), props, ink, "application/json; charset=UTF-8")
        val meta = upsertFile(remote?.meta?.id, NoteFormat.noteMetaName(note.id), props, NoteFormat.encodeNote(file), "application/json; charset=UTF-8")
        noteDao.markSynced(note.id, version, meta.id)
    }

    /** Returns false (and changes nothing) if the cloud copy is unreadable or incomplete. */
    private suspend fun downloadNote(remote: Remote, backupOf: NoteEntity?): Boolean {
        val file = NoteFormat.decodeNote(api.download(remote.meta.id).string()) ?: return false
        val body = remote.body?.let { api.download(it.id).string() } ?: ""
        if (NoteFormat.sha256(body) != file.bodySha256) return false // body upload not finished yet
        val ink = file.inkSha256?.let { expected ->
            val text = remote.ink?.let { api.download(it.id).string() } ?: return false
            if (NoteFormat.sha256(text) != expected) return false
            text
        }

        db.withTransaction {
            if (backupOf != null) backup(backupOf, linkDao.allForNote(backupOf.id), "conflict-local")
            noteDao.upsert(
                NoteEntity(
                    id = file.id,
                    accountId = account.accountId,
                    notebookId = file.notebookId,
                    title = file.title,
                    body = body,
                    createdAt = NoteFormat.millis(file.createdAt),
                    updatedAt = NoteFormat.millis(file.updatedAt),
                    deletedAt = NoteFormat.millisOrNull(file.deletedAt),
                    version = file.version,
                    dirty = false,
                    remoteFileId = remote.meta.id,
                    remoteVersion = file.version,
                    ink = ink,
                ),
            )
            linkDao.deleteAllForNote(file.id)
            linkDao.upsertAll(file.links.map { it.toEntity(file.id) })
        }
        return true
    }

    private suspend fun backupRemoteNote(remote: Remote, noteId: String) {
        val file = NoteFormat.decodeNote(api.download(remote.meta.id).string()) ?: return
        val body = remote.body?.let { api.download(it.id).string() } ?: ""
        backupDao.insert(
            NoteBackupEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                version = file.version,
                title = file.title,
                body = body,
                metaJson = NoteFormat.encodeNote(file),
                reason = "conflict-remote",
                createdAt = clock.millis(),
            ),
        )
    }

    private suspend fun backup(note: NoteEntity, links: List<EventLinkEntity>, reason: String) {
        val meta = NoteFileV1(
            id = note.id, notebookId = note.notebookId, title = note.title,
            createdAt = NoteFormat.iso(note.createdAt), updatedAt = NoteFormat.iso(note.updatedAt),
            version = note.version, deletedAt = NoteFormat.isoOrNull(note.deletedAt),
            bodySha256 = NoteFormat.sha256(note.body), links = links.map { it.toFile() },
        )
        backupDao.insert(
            NoteBackupEntity(
                id = UUID.randomUUID().toString(),
                noteId = note.id,
                version = note.version,
                title = note.title,
                body = note.body,
                metaJson = NoteFormat.encodeNote(meta),
                reason = reason,
                createdAt = clock.millis(),
            ),
        )
    }

    // --- notebooks ---

    private suspend fun syncNotebook(local: NotebookEntity?, remote: Remote?, id: String): DriveSyncResult {
        val localState = local?.let { LocalState(it.version, it.updatedAt, it.dirty, it.remoteVersion) }
        val remoteState = remote?.let { RemoteState(it.version, it.updatedAt) }
        val action = SyncDecision.decide(localState, remoteState)
        return when (action) {
            SyncAction.NONE -> DriveSyncResult()
            SyncAction.UPLOAD -> { uploadNotebook(local!!, remote, local.version); DriveSyncResult(uploaded = 1) }
            SyncAction.CONFLICT_KEEP_LOCAL -> {
                val version = SyncDecision.uploadVersion(localState!!, remoteState, action)
                notebookDao.upsert(local!!.copy(version = version))
                uploadNotebook(local.copy(version = version), remote, version)
                DriveSyncResult(uploaded = 1, conflicts = 1)
            }
            SyncAction.DOWNLOAD, SyncAction.CONFLICT_TAKE_REMOTE -> {
                // Notebook conflicts only concern name/color/links; the newer copy wins without a backup.
                if (downloadNotebook(remote!!)) {
                    DriveSyncResult(downloaded = 1, conflicts = if (action == SyncAction.CONFLICT_TAKE_REMOTE) 1 else 0)
                } else {
                    DriveSyncResult(skipped = 1)
                }
            }
        }
    }

    private suspend fun uploadNotebook(notebook: NotebookEntity, remote: Remote?, version: Long) {
        val file = NotebookFileV1(
            id = notebook.id,
            title = notebook.title,
            color = notebook.color,
            createdAt = NoteFormat.iso(notebook.createdAt),
            updatedAt = NoteFormat.iso(notebook.updatedAt),
            version = version,
            deletedAt = NoteFormat.isoOrNull(notebook.deletedAt),
            eventLinks = notebookDao.allLinksFor(notebook.id).map { it.toFile() },
        )
        val props = props(KIND_NOTEBOOK, notebook.id, version, notebook.updatedAt, notebook.deletedAt != null)
        val meta = upsertFile(remote?.meta?.id, NoteFormat.notebookName(notebook.id), props,
            NoteFormat.encodeNotebook(file), "application/json; charset=UTF-8")
        notebookDao.markSynced(notebook.id, version, meta.id)
    }

    private suspend fun downloadNotebook(remote: Remote): Boolean {
        val file = NoteFormat.decodeNotebook(api.download(remote.meta.id).string()) ?: return false
        db.withTransaction {
            notebookDao.upsert(
                NotebookEntity(
                    id = file.id,
                    accountId = account.accountId,
                    title = file.title,
                    color = file.color,
                    createdAt = NoteFormat.millis(file.createdAt),
                    updatedAt = NoteFormat.millis(file.updatedAt),
                    deletedAt = NoteFormat.millisOrNull(file.deletedAt),
                    version = file.version,
                    dirty = false,
                    remoteFileId = remote.meta.id,
                    remoteVersion = file.version,
                ),
            )
            notebookDao.deleteAllLinksFor(file.id)
            notebookDao.upsertLinks(file.eventLinks.map { it.toEntity(file.id) })
        }
        return true
    }

    // --- Drive helpers ---

    private suspend fun listAll(): List<DriveFile> = buildList {
        var page: String? = null
        do {
            val res = api.list(pageToken = page)
            addAll(res.files)
            page = res.nextPageToken
        } while (page != null)
    }

    private suspend fun upsertFile(existingId: String?, name: String, props: Map<String, String>, content: String, type: String): DriveFile {
        return if (existingId == null) {
            val meta = DriveFileMetadata(name = name, parents = listOf("appDataFolder"), appProperties = props)
            api.create(DriveApi.multipart(json.encodeToString(DriveFileMetadata.serializer(), meta), content, type))
        } else {
            val meta = DriveFileMetadata(appProperties = props)
            api.update(existingId, DriveApi.multipart(json.encodeToString(DriveFileMetadata.serializer(), meta), content, type))
        }
    }

    private fun remoteOf(meta: DriveFile, body: DriveFile?, ink: DriveFile?): Remote? {
        val p = meta.appProperties ?: return null
        val version = p[P_VERSION]?.toLongOrNull() ?: return null
        val updatedAt = p[P_UPDATED]?.toLongOrNull() ?: 0
        return Remote(meta, body, ink, version, updatedAt)
    }

    private fun props(kind: String, id: String, version: Long, updatedAt: Long, deleted: Boolean, format: Int = 1) = mapOf(
        P_KIND to kind,
        P_ID to id,
        P_VERSION to version.toString(),
        P_UPDATED to updatedAt.toString(),
        P_DELETED to deleted.toString(),
        P_FORMAT to format.toString(),
    )

    // --- mapping ---

    private fun EventLinkEntity.toFile() = NoteLinkV1(
        id = id, calendarId = calendarId, eventId = eventId, recurringEventId = recurringEventId,
        instanceStartTime = NoteFormat.isoOrNull(instanceStartTime), scope = scope.name,
        eventTitleSnapshot = eventTitleSnapshot, eventStartSnapshot = NoteFormat.iso(eventStartSnapshot),
        eventDeleted = eventDeleted, createdAt = NoteFormat.iso(createdAt), updatedAt = NoteFormat.iso(updatedAt),
        deletedAt = NoteFormat.isoOrNull(deletedAt),
    )

    private fun NoteLinkV1.toEntity(noteId: String) = EventLinkEntity(
        id = id, noteId = noteId, accountId = account.accountId, calendarId = calendarId, eventId = eventId,
        recurringEventId = recurringEventId, instanceStartTime = NoteFormat.millisOrNull(instanceStartTime),
        scope = scopeOf(scope), eventTitleSnapshot = eventTitleSnapshot,
        eventStartSnapshot = NoteFormat.millis(eventStartSnapshot), eventDeleted = eventDeleted,
        createdAt = NoteFormat.millis(createdAt), updatedAt = NoteFormat.millis(updatedAt),
        deletedAt = NoteFormat.millisOrNull(deletedAt),
    )

    private fun NotebookLinkEntity.toFile() = NotebookLinkV1(
        id = id, calendarId = calendarId, eventId = eventId, recurringEventId = recurringEventId, scope = scope.name,
        eventTitleSnapshot = eventTitleSnapshot, createdAt = NoteFormat.iso(createdAt), deletedAt = NoteFormat.isoOrNull(deletedAt),
    )

    private fun NotebookLinkV1.toEntity(notebookId: String) = NotebookLinkEntity(
        id = id, notebookId = notebookId, accountId = account.accountId, calendarId = calendarId, eventId = eventId,
        recurringEventId = recurringEventId, scope = scopeOf(scope), eventTitleSnapshot = eventTitleSnapshot,
        createdAt = NoteFormat.millis(createdAt), deletedAt = NoteFormat.millisOrNull(deletedAt),
    )

    private fun scopeOf(value: String) = runCatching { EventLinkScope.valueOf(value) }.getOrDefault(EventLinkScope.INSTANCE)

    private operator fun DriveSyncResult.plus(o: DriveSyncResult) =
        DriveSyncResult(uploaded + o.uploaded, downloaded + o.downloaded, conflicts + o.conflicts, skipped + o.skipped)

    companion object {
        const val P_KIND = "kind"
        const val P_ID = "entityId"
        const val P_VERSION = "version"
        const val P_UPDATED = "updatedAt"
        const val P_DELETED = "deleted"
        const val P_FORMAT = "formatVersion"
        const val KIND_NOTE = "note"
        const val KIND_NOTEBOOK = "notebook"
    }
}
