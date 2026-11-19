import * as api from '../api/calendar'
import { HttpError } from '../api/http'
import { db, isWritable, now, type EventRow, type PendingOp } from '../data/db'
import { accountId } from '../auth/session'
import { draftEnd, draftStart, toPayload, toRow, type EventDraft } from './eventMapping'
import { touchNotes } from './notes'
import { touchNotebooks } from './notebooks'
import { timeZone } from './time'

/** Google Calendar cache + outbox; port of CalendarRepository.kt. */
export const LOCAL_PREFIX = 'local'

let lastOutboxError: string | null = null
export const getOutboxError = () => lastOutboxError
export const clearOutboxError = () => {
  lastOutboxError = null
}

export async function refreshCalendars() {
  const entries = await api.listCalendars()
  const existing = new Map((await db.calendars.toArray()).map((c) => [c.id, c]))
  const rows = entries
    .filter((e) => !e.hidden)
    .map((e) => ({
      id: e.id,
      accountId: accountId(),
      summary: e.summaryOverride ?? e.summary ?? e.id,
      colorHex: e.backgroundColor ?? '#3B6FE0',
      accessRole: e.accessRole ?? 'reader',
      isPrimary: !!e.primary,
      // Keep the in-app choice; first time follow Google's "selected" flag.
      visible: existing.get(e.id)?.visible ?? (!!e.selected || !!e.primary),
    }))
  await db.transaction('rw', db.calendars, db.events, async () => {
    await db.calendars.bulkPut(rows)
    const keep = new Set(rows.map((r) => r.id))
    await db.calendars.filter((c) => !keep.has(c.id)).delete()
    await db.events.filter((e) => !keep.has(e.calendarId)).delete()
  })
}

/** Re-fetches visible calendars' events overlapping [from, to) and replaces that part of the cache. */
export async function refreshRange(from: number, to: number) {
  const calendars = (await db.calendars.toArray()).filter((c) => c.visible)
  for (const cal of calendars) {
    const items = await api.listEvents(cal.id, new Date(from).toISOString(), new Date(to).toISOString())
    const rows = items
      .filter((e) => e.status !== 'cancelled')
      .map((e) => toRow(e, cal.id, isWritable(cal)))
      .filter((r): r is EventRow => r != null)
    const fetched = new Set(rows.map((r) => r.id))
    const pending = new Set((await db.pendingOps.toArray()).filter((o) => o.calendarId === cal.id).map((o) => o.eventId))
    await db.transaction('rw', db.events, async () => {
      const stale = await db.events
        .where('calendarId').equals(cal.id)
        .filter((e) => e.startMillis < to && e.endMillis > from && !fetched.has(e.id) && !pending.has(e.id) && !e.id.startsWith(LOCAL_PREFIX))
        .primaryKeys()
      await db.events.bulkDelete(stale)
      await db.events.bulkPut(rows.filter((r) => !pending.has(r.id)))
    })
    await detectDeletedLinkedEvents(cal.id, from, to, rows)
  }
}

async function detectDeletedLinkedEvents(calendarId: string, from: number, to: number, rows: EventRow[]) {
  const ids = new Set(rows.map((r) => r.id))
  const series = new Set(rows.map((r) => r.recurringEventId).filter((s): s is string => !!s))
  const links = await db.eventLinks
    .filter((l) => l.deletedAt == null && !l.eventDeleted && l.calendarId === calendarId)
    .toArray()
  for (const l of links) {
    if (l.eventStartSnapshot < from || l.eventStartSnapshot >= to || l.eventId.startsWith(LOCAL_PREFIX)) continue
    const target = l.scope === 'SERIES' ? (l.recurringEventId ?? l.eventId) : l.eventId
    if (l.scope === 'SERIES' ? series.has(target) : ids.has(target)) continue
    const ev = await api.getEvent(calendarId, target)
    if (!ev || ev.status === 'cancelled') await markLinksDeleted(calendarId, target)
  }
}

/** Marks links to a vanished event and bumps their notes so other devices learn about it. */
async function markLinksDeleted(calendarId: string, eventId: string) {
  const t = now()
  const links = await db.eventLinks
    .filter((l) => l.calendarId === calendarId && (l.eventId === eventId || (l.scope === 'SERIES' && l.recurringEventId === eventId)))
    .toArray()
  for (const l of links) await db.eventLinks.put({ ...l, eventDeleted: true, updatedAt: t })
  await touchNotes(links.map((l) => l.noteId))
}

function draftRow(d: EventDraft, id: string, recurringEventId: string | null): EventRow {
  const start = draftStart(d)
  return {
    calendarId: d.calendarId, id, recurringEventId, originalStartMillis: null, title: d.title,
    description: d.description, location: d.location, startMillis: start, endMillis: Math.max(draftEnd(d), start),
    allDay: d.allDay, timeZone: timeZone(), colorId: null, status: 'confirmed', canEdit: true, htmlLink: null,
    pendingSync: true, markerInTitle: false, markerInDescription: false,
  }
}

const enqueue = (op: Omit<PendingOp, 'id' | 'attempts' | 'lastError' | 'createdAt'>) =>
  db.pendingOps.add({ ...op, createdAt: now(), attempts: 0, lastError: null })

/** Creates an event locally and queues it; returns the temporary id. */
export async function createEvent(d: EventDraft): Promise<string> {
  const id = LOCAL_PREFIX + crypto.randomUUID().replace(/-/g, '')
  await db.transaction('rw', db.events, db.pendingOps, async () => {
    await db.events.put(draftRow(d, id, null))
    await enqueue({ type: 'CREATE', calendarId: d.calendarId, eventId: id, payload: toPayload(d, { includeTimes: true, includeRecurrence: true }) })
  })
  return id
}

/** Updates an event; whole-series edits change text fields only (on the series master). */
export async function updateEvent(eventId: string, d: EventDraft, wholeSeries: boolean) {
  const current = await db.events.get([d.calendarId, eventId])
  if (!current) return
  const markers = { markerInTitle: current.markerInTitle, markerInDescription: current.markerInDescription }
  await db.transaction('rw', db.events, db.pendingOps, async () => {
    if (wholeSeries && current.recurringEventId) {
      const seriesId = current.recurringEventId
      await db.events
        .where('calendarId').equals(d.calendarId)
        .filter((e) => e.id === seriesId || e.recurringEventId === seriesId)
        .modify({ title: d.title, description: d.description, location: d.location, pendingSync: true })
      await enqueue({ type: 'UPDATE', calendarId: d.calendarId, eventId: seriesId, payload: toPayload(d, { includeTimes: false, includeRecurrence: false, ...markers }) })
    } else {
      await db.events.put({
        ...draftRow(d, eventId, current.recurringEventId),
        originalStartMillis: current.originalStartMillis, colorId: current.colorId, htmlLink: current.htmlLink, ...markers,
      })
      await enqueue({ type: 'UPDATE', calendarId: d.calendarId, eventId, payload: toPayload(d, { includeTimes: true, includeRecurrence: false, ...markers }) })
    }
  })
}

/** Deletes an occurrence or the whole series; linked notes are kept and marked. */
export async function deleteEvent(calendarId: string, eventId: string, wholeSeries: boolean) {
  const current = await db.events.get([calendarId, eventId])
  if (!current) return
  const target = wholeSeries && current.recurringEventId ? current.recurringEventId : eventId
  await db.events.where('calendarId').equals(calendarId).filter((e) => e.id === target || e.recurringEventId === target).delete()
  await markLinksDeleted(calendarId, target)
  if (target.startsWith(LOCAL_PREFIX)) {
    await db.pendingOps.filter((o) => o.calendarId === calendarId && o.eventId === target).delete()
  } else {
    await enqueue({ type: 'DELETE', calendarId, eventId: target, payload: null })
  }
}

/** Sends queued changes in order; false if it stopped early (offline / server error). */
export async function flushOutbox(): Promise<boolean> {
  const ops = await db.pendingOps.orderBy('id').toArray()
  for (const op of ops) {
    try {
      if (op.type === 'CREATE') {
        const created = await api.insertEvent(op.calendarId, op.payload ?? {})
        await remapId(op.calendarId, op.eventId, created.id)
        const cal = await db.calendars.get(op.calendarId)
        await db.events.delete([op.calendarId, op.eventId])
        if (!created.recurrence?.length && cal) {
          const row = toRow(created, op.calendarId, isWritable(cal))
          if (row) await db.events.put(row)
        }
      } else if (op.type === 'UPDATE') {
        const updated = await api.patchEvent(op.calendarId, op.eventId, op.payload ?? {})
        const cal = await db.calendars.get(op.calendarId)
        if (!updated.recurrence?.length && cal) {
          const row = toRow(updated, op.calendarId, isWritable(cal))
          if (row) await db.events.put(row)
        }
      } else {
        await api.deleteEvent(op.calendarId, op.eventId)
      }
      await db.pendingOps.delete(op.id!)
    } catch (e) {
      if (e instanceof HttpError && e.status >= 400 && e.status < 500 && ![401, 408, 429].includes(e.status)) {
        await db.pendingOps.delete(op.id!) // rejected by Google: server state wins
        lastOutboxError = `HTTP ${e.status}`
        continue
      }
      await db.pendingOps.update(op.id!, { attempts: op.attempts + 1, lastError: String((e as Error).message) })
      if (e instanceof HttpError) return false
      throw e
    }
  }
  return true
}

/** Replaces a temporary local id everywhere once Google returns the real one. */
async function remapId(calendarId: string, oldId: string, newId: string) {
  const t = now()
  await db.transaction('rw', [db.eventLinks, db.notebookLinks, db.pendingOps, db.notes, db.notebooks], async () => {
    const links = await db.eventLinks.filter((l) => l.calendarId === calendarId && (l.eventId === oldId || l.recurringEventId === oldId)).toArray()
    for (const l of links) {
      await db.eventLinks.put({
        ...l,
        eventId: l.eventId === oldId ? newId : l.eventId,
        recurringEventId: l.recurringEventId === oldId ? newId : l.recurringEventId,
        updatedAt: t,
      })
    }
    await touchNotes(links.map((l) => l.noteId))
    const nbLinks = await db.notebookLinks.filter((l) => l.calendarId === calendarId && l.eventId === oldId).toArray()
    for (const l of nbLinks) await db.notebookLinks.put({ ...l, eventId: newId })
    await touchNotebooks(nbLinks.map((l) => l.notebookId))
    await db.pendingOps.filter((o) => o.calendarId === calendarId && o.eventId === oldId).modify({ eventId: newId })
  })
}

export async function clearCalendarCache() {
  await db.transaction('rw', [db.events, db.calendars, db.pendingOps, db.eventMarkers], async () => {
    await db.events.clear()
    await db.calendars.clear()
    await db.pendingOps.clear()
    await db.eventMarkers.clear()
  })
}
