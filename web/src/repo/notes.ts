import { db, now, uuid, type EventLink, type EventRow, type Note, type NoteBackup } from '../data/db'
import { linkMatches, type EventLinkScope, type EventRef } from '../core/model'
import { accountId } from '../auth/session'

export interface NoteWithLinks { note: Note; links: EventLink[] }

export const eventRef = (e: EventRow): EventRef => ({
  calendarId: e.calendarId,
  eventId: e.id,
  recurringEventId: e.recurringEventId,
  originalStartMillis: e.originalStartMillis,
})

export const activeLinks = () => db.eventLinks.filter((l) => l.deletedAt == null).toArray()

export async function notesForEvent(event: EventRef): Promise<Note[]> {
  const links = (await activeLinks()).filter((l) => linkMatches(l, event))
  const ids = [...new Set(links.map((l) => l.noteId))]
  const notes = (await db.notes.bulkGet(ids)).filter((n): n is Note => !!n && n.deletedAt == null)
  return notes.sort((a, b) => a.createdAt - b.createdAt)
}

export async function noteWithLinks(noteId: string): Promise<NoteWithLinks | null> {
  const note = await db.notes.get(noteId)
  if (!note || note.deletedAt != null) return null
  const links = (await db.eventLinks.where('noteId').equals(noteId).toArray()).filter((l) => l.deletedAt == null)
  return { note, links }
}

function newNote(notebookId: string | null, title: string): Note {
  const t = now()
  return {
    id: uuid(), accountId: accountId(), notebookId, title, body: '', createdAt: t, updatedAt: t,
    deletedAt: null, version: 1, dirty: 1, remoteFileId: null, remoteVersion: null,
  }
}

/** New note attached to an event occurrence (or its series). Returns the note id. */
export async function createForEvent(event: EventRow, scope: EventLinkScope, title: string, notebookId: string | null) {
  const note = newNote(notebookId, title)
  const effective: EventLinkScope = event.recurringEventId ? scope : 'INSTANCE'
  const t = note.createdAt
  await db.transaction('rw', db.notes, db.eventLinks, async () => {
    await db.notes.put(note)
    await db.eventLinks.put({
      id: uuid(), noteId: note.id, accountId: accountId(), calendarId: event.calendarId,
      eventId: effective === 'SERIES' ? event.recurringEventId! : event.id,
      recurringEventId: event.recurringEventId,
      instanceStartTime: effective === 'INSTANCE' ? (event.originalStartMillis ?? event.startMillis) : null,
      scope: effective, eventTitleSnapshot: event.title, eventStartSnapshot: event.startMillis,
      eventDeleted: false, createdAt: t, updatedAt: t, deletedAt: null,
    })
  })
  return note.id
}

export async function createStandalone(notebookId: string | null, title = '') {
  const note = newNote(notebookId, title)
  await db.notes.put(note)
  return note.id
}

/** Saves title/body; no version bump when nothing changed. */
export async function updateContent(noteId: string, title: string, body: string) {
  await db.transaction('rw', db.notes, async () => {
    const n = await db.notes.get(noteId)
    if (!n || n.deletedAt != null || (n.title === title && n.body === body)) return
    await db.notes.put({ ...n, title, body, updatedAt: now(), version: n.version + 1, dirty: 1 })
  })
}

export async function moveToNotebook(noteId: string, notebookId: string | null) {
  await db.transaction('rw', db.notes, async () => {
    const n = await db.notes.get(noteId)
    if (!n || n.notebookId === notebookId) return
    await db.notes.put({ ...n, notebookId, updatedAt: now(), version: n.version + 1, dirty: 1 })
  })
}

/** Soft delete (tombstone) of the note and its links. */
export async function deleteNote(noteId: string) {
  const t = now()
  await db.transaction('rw', db.notes, db.eventLinks, async () => {
    const n = await db.notes.get(noteId)
    if (!n) return
    await db.notes.put({ ...n, deletedAt: t, updatedAt: t, version: n.version + 1, dirty: 1 })
    await db.eventLinks.where('noteId').equals(noteId).modify({ deletedAt: t, updatedAt: t })
  })
}

/** Notes in a notebook (or unfiled for null), newest event/creation first. */
export async function notesIn(notebookId: string | null): Promise<NoteWithLinks[]> {
  const notes = await db.notes.filter((n) => n.deletedAt == null && n.notebookId === notebookId).toArray()
  const links = await activeLinks()
  return withLinks(notes, links).sort((a, b) => sortKey(b) - sortKey(a))
}

export async function allNotesWithLinks(): Promise<NoteWithLinks[]> {
  const notes = await db.notes.filter((n) => n.deletedAt == null).toArray()
  return withLinks(notes, await activeLinks())
}

function withLinks(notes: Note[], links: EventLink[]): NoteWithLinks[] {
  const by = new Map<string, EventLink[]>()
  for (const l of links) by.set(l.noteId, [...(by.get(l.noteId) ?? []), l])
  return notes.map((note) => ({ note, links: by.get(note.id) ?? [] }))
}

const sortKey = (n: NoteWithLinks) => Math.max(n.note.createdAt, ...n.links.map((l) => l.eventStartSnapshot))

export const backupsFor = (noteId: string) =>
  db.noteBackups.where('noteId').equals(noteId).reverse().sortBy('createdAt')

/** Restores a backup; the current text becomes a backup in turn. */
export async function restoreBackup(backup: NoteBackup) {
  const t = now()
  await db.transaction('rw', db.notes, db.noteBackups, async () => {
    const n = await db.notes.get(backup.noteId)
    if (!n) return
    await db.noteBackups.put({ ...backup, id: uuid(), version: n.version, title: n.title, body: n.body, metaJson: '{}', reason: 'before-restore', createdAt: t })
    await db.notes.put({ ...n, title: backup.title, body: backup.body, updatedAt: t, version: n.version + 1, dirty: 1 })
    await db.noteBackups.delete(backup.id)
  })
}

export const deleteBackup = (id: string) => db.noteBackups.delete(id)

/** Bumps notes whose links changed outside the editor, so the change reaches the cloud. */
export async function touchNotes(ids: string[]) {
  const t = now()
  for (const id of new Set(ids)) {
    const n = await db.notes.get(id)
    if (n) await db.notes.put({ ...n, dirty: 1, version: n.version + 1, updatedAt: t })
  }
}
