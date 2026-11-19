import * as api from '../api/calendar'
import { HttpError } from '../api/http'
import { applyDescription, applyTitle, encodeNoteIds, NOTE_IDS_PROPERTY } from '../core/calendarMarker'
import { db, isWritable, now, type EventMarker } from '../data/db'
import { toRow } from './eventMapping'
import { LOCAL_PREFIX } from './calendar'
import { getSettings } from './settings'

/**
 * Writes "has notes" markers to Google Calendar (port of MarkerSync.kt): optional title prefix,
 * optional description block, note ids in extendedProperties.private. Patches only differences.
 */
export async function reconcileMarkers() {
  const prefs = getSettings()
  const calendars = new Map((await db.calendars.toArray()).map((c) => [c.id, c]))
  const activeNotes = new Set((await db.notes.filter((n) => n.deletedAt == null).primaryKeys()) as string[])
  const desired = new Map<string, { calendarId: string; eventId: string; noteIds: string[] }>()
  const links = await db.eventLinks.filter((l) => l.deletedAt == null && !l.eventDeleted).toArray()
  for (const l of links) {
    if (!activeNotes.has(l.noteId)) continue
    const id = l.scope === 'SERIES' ? (l.recurringEventId ?? l.eventId) : l.eventId
    if (id.startsWith(LOCAL_PREFIX)) continue
    const key = `${l.calendarId}\u0000${id}`
    const entry = desired.get(key) ?? { calendarId: l.calendarId, eventId: id, noteIds: [] }
    entry.noteIds.push(l.noteId)
    desired.set(key, entry)
  }
  const existing = new Map((await db.eventMarkers.toArray()).map((m) => [`${m.calendarId}\u0000${m.eventId}`, m]))

  for (const key of new Set([...desired.keys(), ...existing.keys()])) {
    const want = desired.get(key)
    const have = existing.get(key)
    const [calendarId, eventId] = key.split('\u0000')
    const cal = calendars.get(calendarId)
    if (!cal) continue
    const wantIds = want ? encodeNoteIds([...new Set(want.noteIds)]) : ''
    const wantTitle = !!want && prefs.titleMarker
    const wantDescription = !!want && prefs.descriptionMarker
    if (have?.skipped && want) continue
    if (have && !have.skipped && have.noteIds === wantIds && have.titleApplied === wantTitle && have.descriptionApplied === wantDescription) continue
    if (!have && !want) continue
    await apply(calendarId, eventId, isWritable(cal), wantTitle, wantDescription, wantIds, !!want)
  }
}

async function apply(calendarId: string, eventId: string, writable: boolean, title: boolean, description: boolean, noteIds: string, keepRow: boolean) {
  const row = (r: Partial<EventMarker>): EventMarker => ({
    calendarId, eventId, titleApplied: false, descriptionApplied: false, noteIds, skipped: false, updatedAt: now(), ...r,
  })
  const ev = await api.getEvent(calendarId, eventId)
  if (!ev || ev.status === 'cancelled') {
    await db.eventMarkers.delete([calendarId, eventId])
    return
  }
  if (!toRow(ev, calendarId, writable)?.canEdit) {
    if (keepRow) await db.eventMarkers.put(row({ skipped: true }))
    else await db.eventMarkers.delete([calendarId, eventId])
    return
  }
  const curTitle = ev.summary ?? ''
  const curDesc = ev.description ?? ''
  const newTitle = applyTitle(curTitle, title)
  const newDesc = applyDescription(curDesc, description)
  const body: Record<string, unknown> = {
    extendedProperties: { private: { [NOTE_IDS_PROPERTY]: noteIds === '' ? null : noteIds } },
  }
  if (newTitle !== curTitle) body.summary = newTitle
  if (newDesc !== curDesc) body.description = newDesc
  try {
    await api.patchEvent(calendarId, eventId, body)
  } catch (e) {
    if (e instanceof HttpError && e.status === 403) {
      if (keepRow) await db.eventMarkers.put(row({ skipped: true }))
      return
    }
    throw e
  }
  if (keepRow) await db.eventMarkers.put(row({ titleApplied: title, descriptionApplied: description }))
  else await db.eventMarkers.delete([calendarId, eventId])
  await db.events
    .where('calendarId').equals(calendarId)
    .filter((e) => e.id === eventId || e.recurringEventId === eventId)
    .modify({ markerInTitle: title, markerInDescription: description })
}
