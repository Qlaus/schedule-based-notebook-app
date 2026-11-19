import * as drive from '../api/drive'
import type { DriveFile } from '../api/drive'
import {
  decodeNote, decodeNotebook, encodeNote, encodeNotebook, FORMAT_VERSION, iso, isoOrNull, millis, millisOrNull,
  noteBodyName, noteMetaName, notebookName, sha256, type NoteFileV1, type NoteLinkV1, type NotebookFileV1,
} from '../core/noteFormat'
import { decide, uploadVersion, type LocalState, type RemoteState } from '../core/syncDecision'
import { db, now, uuid, type EventLink, type Note, type Notebook } from '../data/db'
import { accountId } from '../auth/session'

/**
 * Two-way sync with the Drive appDataFolder; port of DriveSync.kt (same format, same rules), so the
 * web app and the Android app can edit the same notes.
 */
export interface DriveSyncResult { uploaded: number; downloaded: number; conflicts: number; skipped: number }

interface Remote { meta: DriveFile; body: DriveFile | null; version: number; updatedAt: number }

const P = { kind: 'kind', id: 'entityId', version: 'version', updated: 'updatedAt', deleted: 'deleted', format: 'formatVersion' }

export async function syncDrive(): Promise<DriveSyncResult> {
  const files = await drive.listAppData()
  const byName = new Map<string, DriveFile[]>()
  for (const f of files) byName.set(f.name ?? '', [...(byName.get(f.name ?? '') ?? []), f])
  const pick = (name: string) =>
    (byName.get(name) ?? []).reduce<DriveFile | null>((best, f) => (!best || ver(f) > ver(best) ? f : best), null)
  const r: DriveSyncResult = { uploaded: 0, downloaded: 0, conflicts: 0, skipped: 0 }
  const add = (o: Partial<DriveSyncResult>) => {
    r.uploaded += o.uploaded ?? 0
    r.downloaded += o.downloaded ?? 0
    r.conflicts += o.conflicts ?? 0
    r.skipped += o.skipped ?? 0
  }

  const remoteNotebookIds = new Set(files.filter((f) => f.appProperties?.[P.kind] === 'notebook').map((f) => f.appProperties![P.id]))
  const localNotebooks = new Map((await db.notebooks.toArray()).map((n) => [n.id, n]))
  for (const id of new Set([...remoteNotebookIds, ...localNotebooks.keys()])) {
    const meta = remoteNotebookIds.has(id) ? pick(notebookName(id)) : null
    add(await syncNotebook(localNotebooks.get(id) ?? null, meta ? remoteOf(meta, null) : null))
  }

  const remoteNoteIds = new Set(files.filter((f) => f.appProperties?.[P.kind] === 'note').map((f) => f.appProperties![P.id]))
  const localNotes = new Map((await db.notes.toArray()).map((n) => [n.id, n]))
  for (const id of new Set([...remoteNoteIds, ...localNotes.keys()])) {
    const meta = remoteNoteIds.has(id) ? pick(noteMetaName(id)) : null
    add(await syncNote(localNotes.get(id) ?? null, meta ? remoteOf(meta, pick(noteBodyName(id))) : null))
  }
  return r
}

const ver = (f: DriveFile) => Number(f.appProperties?.[P.version] ?? -1)

function remoteOf(meta: DriveFile, body: DriveFile | null): Remote | null {
  const v = meta.appProperties?.[P.version]
  if (v == null || Number.isNaN(Number(v))) return null
  return { meta, body, version: Number(v), updatedAt: Number(meta.appProperties?.[P.updated] ?? 0) }
}

const localState = (x: Note | Notebook): LocalState => ({ version: x.version, updatedAt: x.updatedAt, dirty: x.dirty === 1, remoteVersion: x.remoteVersion })
const remoteState = (x: Remote): RemoteState => ({ version: x.version, updatedAt: x.updatedAt })

function props(kind: string, id: string, version: number, updatedAt: number, deleted: boolean) {
  return {
    [P.kind]: kind, [P.id]: id, [P.version]: String(version), [P.updated]: String(updatedAt),
    [P.deleted]: String(deleted), [P.format]: String(FORMAT_VERSION),
  }
}

// --- notes ---

async function syncNote(local: Note | null, remote: Remote | null): Promise<Partial<DriveSyncResult>> {
  const ls = local ? localState(local) : null
  const rs = remote ? remoteState(remote) : null
  const action = decide(ls, rs)
  switch (action) {
    case 'NONE':
      return {}
    case 'UPLOAD':
      await uploadNote(local!, remote, local!.version)
      return { uploaded: 1 }
    case 'DOWNLOAD':
      return (await downloadNote(remote!, null)) ? { downloaded: 1 } : { skipped: 1 }
    case 'CONFLICT_KEEP_LOCAL': {
      await backupRemoteNote(remote!, local!.id)
      const version = uploadVersion(ls!, rs, action)
      const bumped = { ...local!, version }
      await db.notes.put(bumped)
      await uploadNote(bumped, remote, version)
      return { uploaded: 1, conflicts: 1 }
    }
    case 'CONFLICT_TAKE_REMOTE':
      return (await downloadNote(remote!, local)) ? { downloaded: 1, conflicts: 1 } : { skipped: 1 }
  }
}

const linkToFile = (l: EventLink): NoteLinkV1 => ({
  id: l.id, calendarId: l.calendarId, eventId: l.eventId, recurringEventId: l.recurringEventId,
  instanceStartTime: isoOrNull(l.instanceStartTime), scope: l.scope, eventTitleSnapshot: l.eventTitleSnapshot,
  eventStartSnapshot: iso(l.eventStartSnapshot), eventDeleted: l.eventDeleted, createdAt: iso(l.createdAt),
  updatedAt: iso(l.updatedAt), deletedAt: isoOrNull(l.deletedAt),
})

const linkFromFile = (l: NoteLinkV1, noteId: string): EventLink => ({
  id: l.id, noteId, accountId: accountId(), calendarId: l.calendarId, eventId: l.eventId,
  recurringEventId: l.recurringEventId, instanceStartTime: millisOrNull(l.instanceStartTime), scope: l.scope,
  eventTitleSnapshot: l.eventTitleSnapshot, eventStartSnapshot: millis(l.eventStartSnapshot), eventDeleted: l.eventDeleted,
  createdAt: millis(l.createdAt), updatedAt: millis(l.updatedAt), deletedAt: millisOrNull(l.deletedAt),
})

async function noteFile(note: Note, version: number, body: string): Promise<NoteFileV1> {
  const links = await db.eventLinks.where('noteId').equals(note.id).toArray()
  return {
    formatVersion: FORMAT_VERSION, id: note.id, notebookId: note.notebookId, title: note.title,
    createdAt: iso(note.createdAt), updatedAt: iso(note.updatedAt), version, deletedAt: isoOrNull(note.deletedAt),
    bodySha256: await sha256(body), links: links.map(linkToFile),
  }
}

async function uploadNote(note: Note, remote: Remote | null, version: number) {
  const body = note.deletedAt != null ? '' : note.body
  const file = await noteFile(note, version, body)
  const p = props('note', note.id, version, note.updatedAt, note.deletedAt != null)
  // Body first, metadata last: the metadata's hash tells readers whether the body is complete.
  await drive.uploadFile(remote?.body?.id ?? null, noteBodyName(note.id), p, body, 'text/markdown; charset=UTF-8')
  const meta = await drive.uploadFile(remote?.meta.id ?? null, noteMetaName(note.id), p, encodeNote(file), 'application/json; charset=UTF-8')
  await markSynced('notes', note.id, version, meta.id)
}

/** Records an upload; stays dirty if edited meanwhile (compare on version). */
async function markSynced(table: 'notes' | 'notebooks', id: string, version: number, fileId: string) {
  const t = table === 'notes' ? db.notes : db.notebooks
  await db.transaction('rw', t, async () => {
    const x = await t.get(id)
    if (!x) return
    await t.put({ ...x, remoteVersion: version, remoteFileId: fileId, dirty: x.version === version ? 0 : x.dirty } as never)
  })
}

async function downloadNote(remote: Remote, backupOf: Note | null): Promise<boolean> {
  const file = decodeNote(await drive.downloadText(remote.meta.id))
  if (!file) return false
  const body = remote.body ? await drive.downloadText(remote.body.id) : ''
  if ((await sha256(body)) !== file.bodySha256) return false // upload not finished yet
  // Prepare the backup outside the transaction: awaiting crypto.subtle inside one would end it early.
  const backupRecord = backupOf ? await backupFor(backupOf, 'conflict-local') : null
  await db.transaction('rw', db.notes, db.eventLinks, db.noteBackups, async () => {
    if (backupRecord) await db.noteBackups.put(backupRecord)
    await db.notes.put({
      id: file.id, accountId: accountId(), notebookId: file.notebookId, title: file.title, body,
      createdAt: millis(file.createdAt), updatedAt: millis(file.updatedAt), deletedAt: millisOrNull(file.deletedAt),
      version: file.version, dirty: 0, remoteFileId: remote.meta.id, remoteVersion: file.version,
    })
    await db.eventLinks.where('noteId').equals(file.id).delete()
    await db.eventLinks.bulkPut(file.links.map((l) => linkFromFile(l, file.id)))
  })
  return true
}

async function backupRemoteNote(remote: Remote, noteId: string) {
  const file = decodeNote(await drive.downloadText(remote.meta.id))
  if (!file) return
  const body = remote.body ? await drive.downloadText(remote.body.id) : ''
  await db.noteBackups.put({
    id: uuid(), noteId, version: file.version, title: file.title, body, metaJson: encodeNote(file),
    reason: 'conflict-remote', createdAt: now(),
  })
}

async function backupFor(note: Note, reason: string) {
  const file = await noteFile(note, note.version, note.body)
  return {
    id: uuid(), noteId: note.id, version: note.version, title: note.title, body: note.body,
    metaJson: encodeNote(file), reason, createdAt: now(),
  }
}

// --- notebooks ---

async function syncNotebook(local: Notebook | null, remote: Remote | null): Promise<Partial<DriveSyncResult>> {
  const ls = local ? localState(local) : null
  const rs = remote ? remoteState(remote) : null
  const action = decide(ls, rs)
  switch (action) {
    case 'NONE':
      return {}
    case 'UPLOAD':
      await uploadNotebook(local!, remote, local!.version)
      return { uploaded: 1 }
    case 'CONFLICT_KEEP_LOCAL': {
      const version = uploadVersion(ls!, rs, action)
      await db.notebooks.put({ ...local!, version })
      await uploadNotebook({ ...local!, version }, remote, version)
      return { uploaded: 1, conflicts: 1 }
    }
    case 'DOWNLOAD':
    case 'CONFLICT_TAKE_REMOTE':
      // Notebook conflicts only concern name/color/links: the newer copy wins without a backup.
      return (await downloadNotebook(remote!))
        ? { downloaded: 1, conflicts: action === 'CONFLICT_TAKE_REMOTE' ? 1 : 0 }
        : { skipped: 1 }
  }
}

async function uploadNotebook(nb: Notebook, remote: Remote | null, version: number) {
  const links = await db.notebookLinks.where('notebookId').equals(nb.id).toArray()
  const file: NotebookFileV1 = {
    formatVersion: FORMAT_VERSION, id: nb.id, title: nb.title, color: nb.color, createdAt: iso(nb.createdAt),
    updatedAt: iso(nb.updatedAt), version, deletedAt: isoOrNull(nb.deletedAt),
    eventLinks: links.map((l) => ({
      id: l.id, calendarId: l.calendarId, eventId: l.eventId, recurringEventId: l.recurringEventId, scope: l.scope,
      eventTitleSnapshot: l.eventTitleSnapshot, createdAt: iso(l.createdAt), deletedAt: isoOrNull(l.deletedAt),
    })),
  }
  const p = props('notebook', nb.id, version, nb.updatedAt, nb.deletedAt != null)
  const meta = await drive.uploadFile(remote?.meta.id ?? null, notebookName(nb.id), p, encodeNotebook(file), 'application/json; charset=UTF-8')
  await markSynced('notebooks', nb.id, version, meta.id)
}

async function downloadNotebook(remote: Remote): Promise<boolean> {
  const file = decodeNotebook(await drive.downloadText(remote.meta.id))
  if (!file) return false
  await db.transaction('rw', db.notebooks, db.notebookLinks, async () => {
    await db.notebooks.put({
      id: file.id, accountId: accountId(), title: file.title, color: file.color, createdAt: millis(file.createdAt),
      updatedAt: millis(file.updatedAt), deletedAt: millisOrNull(file.deletedAt), version: file.version, dirty: 0,
      remoteFileId: remote.meta.id, remoteVersion: file.version,
    })
    await db.notebookLinks.where('notebookId').equals(file.id).delete()
    await db.notebookLinks.bulkPut(file.eventLinks.map((l) => ({
      id: l.id, notebookId: file.id, accountId: accountId(), calendarId: l.calendarId, eventId: l.eventId,
      recurringEventId: l.recurringEventId, scope: l.scope, eventTitleSnapshot: l.eventTitleSnapshot,
      createdAt: millis(l.createdAt), deletedAt: millisOrNull(l.deletedAt),
    })))
  })
  return true
}
