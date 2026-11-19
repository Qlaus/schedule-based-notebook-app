import { beforeEach, describe, expect, it } from 'vitest'
import { db, useTestDb, type EventRow } from '../data/db'
import { FakeGoogle, signInForTest } from '../test/fakeGoogle'
import * as notes from './notes'
import * as notebooks from './notebooks'
import * as calendar from './calendar'
import { reconcileMarkers } from './markers'
import { syncDrive } from './driveSync'
import { setSettings } from './settings'
import { noteBodyName, noteMetaName } from '../core/noteFormat'
import { NOTE_IDS_PROPERTY } from '../core/calendarMarker'

let g: FakeGoogle
let dbCounter = 0

const ev = (id: string, start: number, recurringEventId: string | null = null): EventRow => ({
  calendarId: 'cal', id, recurringEventId, originalStartMillis: recurringEventId ? start : null, title: 'Fizik',
  description: '', location: '', startMillis: start, endMillis: start + 3_600_000, allDay: false, timeZone: 'UTC',
  colorId: null, status: 'confirmed', canEdit: true, htmlLink: null, pendingSync: false, markerInTitle: false, markerInDescription: false,
})

beforeEach(async () => {
  useTestDb(`t${dbCounter++}`)
  g = new FakeGoogle()
  g.install()
  signInForTest()
  setSettings({ titleMarker: true, descriptionMarker: false })
  await db.calendars.put({ id: 'cal', accountId: 'acc', summary: 'Ders', colorHex: '#3B6FE0', accessRole: 'owner', isPrimary: true, visible: true })
})

describe('notes & notebooks', () => {
  const monday = ev('lec_1', 1_000_000, 'lec')
  const tuesday = ev('lec_2', 90_000_000, 'lec')

  it('series note shows on every occurrence, instance note only on one', async () => {
    const s = await notes.createForEvent(monday, 'SERIES', 'S', null)
    const i = await notes.createForEvent(monday, 'INSTANCE', 'I', null)
    expect((await notes.notesForEvent(notes.eventRef(tuesday))).map((n) => n.id)).toEqual([s])
    expect((await notes.notesForEvent(notes.eventRef(monday))).map((n) => n.id).sort()).toEqual([s, i].sort())
  })

  it('update bumps version only when changed; delete is a tombstone', async () => {
    const id = await notes.createStandalone(null, 'A')
    await notes.updateContent(id, 'A', '')
    expect((await db.notes.get(id))!.version).toBe(1)
    await notes.updateContent(id, 'A', 'x')
    expect((await db.notes.get(id))!.version).toBe(2)
    await notes.deleteNote(id)
    expect(await notes.noteWithLinks(id)).toBeNull()
    expect((await db.notes.get(id))!.deletedAt).not.toBeNull()
  })

  it('series linked to a notebook; deleting the notebook keeps notes', async () => {
    const nb = await notebooks.createNotebook('Fizik', null)
    await notebooks.linkEvent(monday, 'SERIES', nb)
    expect((await notebooks.notebookLinkFor(notes.eventRef(tuesday)))?.notebook.id).toBe(nb)
    const n = await notes.createForEvent(tuesday, 'INSTANCE', 'Ders 2', nb)
    expect((await notebooks.notebooksWithCounts())[0].noteCount).toBe(1)
    await notebooks.deleteNotebook(nb)
    expect(await notebooks.notebookLinkFor(notes.eventRef(tuesday))).toBeNull()
    expect((await db.notes.get(n))!.notebookId).toBeNull()
  })
})

describe('calendar outbox', () => {
  const draft = {
    calendarId: 'cal', title: 'Yeni', description: '', location: '', allDay: false,
    startDate: '2026-11-17', startTime: '09:00', endDate: '2026-11-17', endTime: '10:00', repeat: 'NONE' as const,
  }

  it('offline create is queued, uploaded and remapped (with note links)', async () => {
    const localId = await calendar.createEvent(draft)
    const local = (await db.events.get(['cal', localId]))!
    const noteId = await notes.createForEvent(local, 'INSTANCE', 'Yeni', null)
    expect(await calendar.flushOutbox()).toBe(true)
    expect(await db.pendingOps.count()).toBe(0)
    expect(await db.events.get(['cal', localId])).toBeUndefined()
    const link = (await notes.noteWithLinks(noteId))!.links[0]
    expect(link.eventId).toBe('srv1')
    expect(g.events.get('srv1')!.summary).toBe('Yeni')
  })

  it('server error keeps op, rejection drops it', async () => {
    await calendar.createEvent(draft)
    g.failNext = 503
    expect(await calendar.flushOutbox()).toBe(false)
    expect(await db.pendingOps.count()).toBe(1)
    g.failNext = 400
    expect(await calendar.flushOutbox()).toBe(true)
    expect(await db.pendingOps.count()).toBe(0)
    expect(calendar.getOutboxError()).toBe('HTTP 400')
  })

  it('refresh replaces range and marks notes of deleted events', async () => {
    g.events.set('a', { id: 'a', summary: '📝 Fizik', start: { dateTime: '2026-11-17T09:00:00Z' }, end: { dateTime: '2026-11-17T10:00:00Z' } })
    const from = Date.parse('2026-11-16T00:00:00Z')
    const to = Date.parse('2026-11-23T00:00:00Z')
    await calendar.refreshRange(from, to)
    const row = (await db.events.get(['cal', 'a']))!
    expect(row.title).toBe('Fizik')
    expect(row.markerInTitle).toBe(true)
    const noteId = await notes.createForEvent(row, 'INSTANCE', 'Fizik', null)
    g.events.delete('a')
    await calendar.refreshRange(from, to)
    expect(await db.events.get(['cal', 'a'])).toBeUndefined()
    expect((await notes.noteWithLinks(noteId))!.links[0].eventDeleted).toBe(true)
  })
})

describe('markers', () => {
  it('adds and removes the title prefix and note ids', async () => {
    g.events.set('a', { id: 'a', summary: 'Fizik', description: 'Konu', organizer: { self: true }, start: { dateTime: '2026-11-17T09:00:00Z' }, end: { dateTime: '2026-11-17T10:00:00Z' } })
    await db.events.put(ev('a', Date.parse('2026-11-17T09:00:00Z')))
    const id = await notes.createForEvent((await db.events.get(['cal', 'a']))!, 'INSTANCE', 'Fizik', null)
    await reconcileMarkers()
    const e = g.events.get('a') as unknown as { summary: string; description: string; extendedProperties: { private: Record<string, string | null> } }
    expect(e.summary).toBe('📝 Fizik')
    expect(e.description).toBe('Konu')
    expect(e.extendedProperties.private[NOTE_IDS_PROPERTY]).toBe(id)
    const count = g.requests.length
    await reconcileMarkers()
    expect(g.requests.length).toBe(count) // nothing changed → no requests
    await notes.deleteNote(id)
    await reconcileMarkers()
    expect(e.summary).toBe('Fizik')
    expect(e.extendedProperties.private[NOTE_IDS_PROPERTY]).toBeNull()
  })

  it('skips events the user cannot edit', async () => {
    g.events.set('a', { id: 'a', summary: 'Toplantı', organizer: { self: false, email: 'x@y.z' }, start: { dateTime: '2026-11-17T09:00:00Z' }, end: { dateTime: '2026-11-17T10:00:00Z' } })
    await db.events.put(ev('a', 1))
    await notes.createForEvent((await db.events.get(['cal', 'a']))!, 'INSTANCE', 'T', null)
    await reconcileMarkers()
    expect(g.requests.filter((r) => r.startsWith('PATCH'))).toHaveLength(0)
    expect((await db.eventMarkers.toArray())[0].skipped).toBe(true)
  })
})

describe('drive sync', () => {
  it('uploads body before metadata and is idle afterwards', async () => {
    const id = await notes.createStandalone(null, 'Fizik')
    await notes.updateContent(id, 'Fizik', 'Newton')
    expect((await syncDrive()).uploaded).toBe(1)
    const body = g.file(noteBodyName(id))
    const meta = g.file(noteMetaName(id))
    expect(body.content).toBe('Newton')
    expect(Number(body.id.slice(1))).toBeLessThan(Number(meta.id.slice(1)))
    expect(meta.appProperties.version).toBe('2')
    expect((await db.notes.get(id))!.dirty).toBe(0)
    g.requests = []
    await syncDrive()
    expect(g.requests).toEqual(['GET /drive/v3/files'])
  })

  it('second device receives notes, notebooks, series links and tombstones', async () => {
    const nb = await notebooks.createNotebook('Fizik', '#3B6FE0')
    const lec = ev('lec_1', 1_000_000, 'lec')
    await notebooks.linkEvent(lec, 'SERIES', nb)
    const n1 = await notes.createForEvent(lec, 'INSTANCE', 'Ders 1', nb)
    const n2 = await notes.createStandalone(null, 'Silinecek')
    await syncDrive()
    await notes.deleteNote(n2)
    await syncDrive()

    useTestDb(`t${dbCounter++}`) // a fresh device with the same Google account
    const r = await syncDrive()
    expect(r.downloaded).toBe(3)
    expect((await notebooks.notebooksWithCounts())[0]).toMatchObject({ noteCount: 1 })
    expect((await notebooks.notebookLinkFor(notes.eventRef(ev('lec_9', 9, 'lec'))))?.notebook.id).toBe(nb)
    expect((await notes.noteWithLinks(n1))!.links[0].eventId).toBe('lec_1')
    expect(await notes.noteWithLinks(n2)).toBeNull()
  })

  it('conflict: newer remote wins, local kept as backup', async () => {
    const id = await notes.createStandalone(null, 'A')
    await syncDrive()
    await notes.updateContent(id, 'A', 'local edit')
    // Simulate the other device writing later.
    const later = Date.now() + 60_000
    const meta = g.file(noteMetaName(id))
    const body = g.file(noteBodyName(id))
    body.content = 'remote edit'
    const file = JSON.parse(meta.content)
    const { sha256, iso } = await import('../core/noteFormat')
    meta.content = JSON.stringify({ ...file, version: 3, updatedAt: iso(later), bodySha256: await sha256('remote edit') })
    meta.appProperties = { ...meta.appProperties, version: '3', updatedAt: String(later) }
    const r = await syncDrive()
    expect(r.conflicts).toBe(1)
    expect((await db.notes.get(id))!.body).toBe('remote edit')
    expect((await notes.backupsFor(id))[0].body).toBe('local edit')
  })

  it('half-finished upload (hash mismatch) is skipped', async () => {
    const id = await notes.createStandalone(null, 'A')
    await notes.updateContent(id, 'A', 'x')
    await syncDrive()
    g.file(noteBodyName(id)).content = 'partial'
    useTestDb(`t${dbCounter++}`)
    expect((await syncDrive()).skipped).toBe(1)
    expect(await db.notes.get(id)).toBeUndefined()
  })
})
