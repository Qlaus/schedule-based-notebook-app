import { db, now, uuid, type EventRow, type Notebook, type NotebookLink } from '../data/db'
import { linkMatches, type EventLinkScope, type EventRef } from '../core/model'
import { accountId } from '../auth/session'

export const NOTEBOOK_PALETTE = ['#3B6FE0', '#5E8C6A', '#C4703B', '#B5478C', '#7A5CC4', '#3B9FB0', '#B59A3B', '#7D7D85']

export interface NotebookWithCount { notebook: Notebook; noteCount: number }

export async function activeNotebooks(): Promise<Notebook[]> {
  const list = await db.notebooks.filter((n) => n.deletedAt == null).toArray()
  return list.sort((a, b) => a.title.localeCompare(b.title, 'tr', { sensitivity: 'base' }))
}

export async function notebooksWithCounts(): Promise<NotebookWithCount[]> {
  const notes = await db.notes.filter((n) => n.deletedAt == null && n.notebookId != null).toArray()
  const counts = new Map<string, number>()
  for (const n of notes) counts.set(n.notebookId!, (counts.get(n.notebookId!) ?? 0) + 1)
  return (await activeNotebooks()).map((notebook) => ({ notebook, noteCount: counts.get(notebook.id) ?? 0 }))
}

export async function createNotebook(title: string, color: string | null): Promise<string> {
  const t = now()
  const nb: Notebook = {
    id: uuid(), accountId: accountId(), title: title.trim() || 'Defter', color, createdAt: t, updatedAt: t,
    deletedAt: null, version: 1, dirty: 1, remoteFileId: null, remoteVersion: null,
  }
  await db.notebooks.put(nb)
  return nb.id
}

export async function updateNotebook(id: string, title: string, color: string | null) {
  const nb = await db.notebooks.get(id)
  if (!nb) return
  const cleaned = title.trim() || nb.title
  if (cleaned === nb.title && color === nb.color) return
  await db.notebooks.put({ ...nb, title: cleaned, color, updatedAt: now(), version: nb.version + 1, dirty: 1 })
}

/** Soft-deletes the notebook; its notes are kept and become unfiled, event links are removed. */
export async function deleteNotebook(id: string) {
  const t = now()
  await db.transaction('rw', db.notebooks, db.notebookLinks, db.notes, async () => {
    const nb = await db.notebooks.get(id)
    if (!nb) return
    await db.notebooks.put({ ...nb, deletedAt: t, updatedAt: t, version: nb.version + 1, dirty: 1 })
    await db.notebookLinks.where('notebookId').equals(id).modify({ deletedAt: t })
    const notes = await db.notes.where('notebookId').equals(id).toArray()
    for (const n of notes) {
      if (n.deletedAt == null) await db.notes.put({ ...n, notebookId: null, updatedAt: t, version: n.version + 1, dirty: 1 })
    }
  })
}

async function touchNotebook(id: string) {
  const nb = await db.notebooks.get(id)
  if (nb) await db.notebooks.put({ ...nb, dirty: 1, version: nb.version + 1, updatedAt: now() })
}

export interface LinkWithNotebook { link: NotebookLink; notebook: Notebook }

/** Notebook an occurrence belongs to (instance link wins over series link). */
export async function notebookLinkFor(event: EventRef): Promise<LinkWithNotebook | null> {
  const notebooks = new Map((await activeNotebooks()).map((n) => [n.id, n]))
  const links = (await db.notebookLinks.filter((l) => l.deletedAt == null).toArray())
    .filter((l) => linkMatches(l, event) && notebooks.has(l.notebookId))
    .sort((a, b) => (a.scope === 'INSTANCE' ? 0 : 1) - (b.scope === 'INSTANCE' ? 0 : 1))
  return links.length ? { link: links[0], notebook: notebooks.get(links[0].notebookId)! } : null
}

export async function linkEvent(event: EventRow, scope: EventLinkScope, notebookId: string) {
  const effective: EventLinkScope = event.recurringEventId ? scope : 'INSTANCE'
  const targetId = effective === 'SERIES' ? event.recurringEventId! : event.id
  const t = now()
  await db.transaction('rw', db.notebookLinks, db.notebooks, async () => {
    const old = await db.notebookLinks
      .filter((l) => l.deletedAt == null && l.calendarId === event.calendarId && l.eventId === targetId && l.scope === effective)
      .toArray()
    for (const l of old) await db.notebookLinks.put({ ...l, deletedAt: t })
    await db.notebookLinks.put({
      id: uuid(), notebookId, accountId: accountId(), calendarId: event.calendarId, eventId: targetId,
      recurringEventId: event.recurringEventId, scope: effective, eventTitleSnapshot: event.title,
      createdAt: t, deletedAt: null,
    })
    await touchNotebook(notebookId)
  })
}

export async function unlink(linkId: string) {
  await db.transaction('rw', db.notebookLinks, db.notebooks, async () => {
    const l = await db.notebookLinks.get(linkId)
    if (!l) return
    await db.notebookLinks.put({ ...l, deletedAt: now() })
    await touchNotebook(l.notebookId)
  })
}

export async function touchNotebooks(ids: string[]) {
  for (const id of new Set(ids)) await touchNotebook(id)
}
