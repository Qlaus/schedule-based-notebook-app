import { describe, expect, it } from 'vitest'
import { linkMatches, type EventRef } from './model'
import { layoutColumns } from './eventColumns'
import { applyDescription, applyTitle, DESCRIPTION_BLOCK, encodeNoteIds, stripDescription, stripTitle } from './calendarMarker'
import { textMatches } from './textSearch'
import { decide, uploadVersion } from './syncDecision'
import { decodeNote, encodeNote, iso, sha256, type NoteFileV1 } from './noteFormat'

describe('linkMatches (EventLinkMatcherTest)', () => {
  const monday: EventRef = { calendarId: 'cal', eventId: 'lecture_20261116T090000Z', recurringEventId: 'lecture' }
  const tuesday: EventRef = { calendarId: 'cal', eventId: 'lecture_20261117T090000Z', recurringEventId: 'lecture' }
  it('instance link matches only that occurrence', () => {
    const link = { calendarId: 'cal', eventId: monday.eventId, recurringEventId: 'lecture', scope: 'INSTANCE' as const }
    expect(linkMatches(link, monday)).toBe(true)
    expect(linkMatches(link, tuesday)).toBe(false)
  })
  it('series link matches every occurrence', () => {
    const link = { calendarId: 'cal', eventId: 'lecture', recurringEventId: 'lecture', scope: 'SERIES' as const }
    expect(linkMatches(link, monday)).toBe(true)
    expect(linkMatches(link, tuesday)).toBe(true)
  })
  it('different calendar never matches', () => {
    expect(linkMatches({ calendarId: 'x', eventId: 'lecture', recurringEventId: 'lecture', scope: 'SERIES' }, monday)).toBe(false)
  })
})

describe('layoutColumns (EventColumnsTest)', () => {
  const place = (...items: [string, number, number][]) =>
    Object.fromEntries(layoutColumns(items.map(([item, start, end]) => ({ item, start, end }))).map((p) => [p.item, p]))
  it('non-overlapping use full width', () => {
    const r = place(['a', 0, 10], ['b', 10, 20])
    expect(r.a.columnCount).toBe(1)
    expect(r.b.columnCount).toBe(1)
  })
  it('overlapping sit side by side', () => {
    const r = place(['a', 0, 10], ['b', 5, 15])
    expect([r.a.column, r.b.column, r.b.columnCount]).toEqual([0, 1, 2])
  })
  it('freed column is reused', () => {
    const r = place(['a', 0, 30], ['b', 0, 10], ['c', 15, 20])
    expect([r.c.column, r.c.columnCount]).toEqual([1, 2])
  })
})

describe('calendar markers (CalendarMarkerTest)', () => {
  it('title prefix added once and removed', () => {
    expect(applyTitle('Fizik', true)).toBe('📝 Fizik')
    expect(applyTitle('📝 Fizik', true)).toBe('📝 Fizik')
    expect(applyTitle('📝 Fizik', false)).toBe('Fizik')
    expect(stripTitle('📝 Fizik')).toEqual({ text: 'Fizik', hadMarker: true })
    expect(applyTitle('Ders 📝 notlar', false)).toBe('Ders 📝 notlar')
  })
  it('description block keeps user text', () => {
    const user = 'Sınav hazırlığı\nKonu: Optik'
    const marked = applyDescription(user, true)
    expect(marked.endsWith(DESCRIPTION_BLOCK)).toBe(true)
    expect(applyDescription(marked, false)).toBe(user)
    expect(applyDescription('', true)).toBe(DESCRIPTION_BLOCK)
    expect(stripDescription('Konu: Optik<br><br>' + DESCRIPTION_BLOCK)).toEqual({ text: 'Konu: Optik', hadMarker: true })
  })
  it('note ids sorted and bounded', () => {
    expect(encodeNoteIds(['b', 'a'])).toBe('a,b')
    expect(encodeNoteIds(Array.from({ length: 100 }, (_, i) => String(i).padStart(36, '0'))).length).toBeLessThanOrEqual(1024)
  })
})

describe('textMatches (TextSearchTest)', () => {
  const hay = 'Fizik · 17 Kas | 17 Kasım 2026 Pazartesi 2026-11-17 17.11.2026 | Işık ve optik'
  it('all words must match', () => {
    expect(textMatches(hay, 'fizik kasım')).toBe(true)
    expect(textMatches(hay, 'fizik aralık')).toBe(false)
  })
  it('turkish folding', () => {
    expect(textMatches(hay, 'kasim')).toBe(true)
    expect(textMatches(hay, 'ISIK')).toBe(true)
    expect(textMatches(hay, 'ışık', 'en')).toBe(true)
  })
  it('blank matches nothing', () => expect(textMatches(hay, '  ')).toBe(false))
})

describe('decide (SyncDecisionTest)', () => {
  const L = (v: number, at: number, dirty: boolean, rv: number | null) => ({ version: v, updatedAt: at, dirty, remoteVersion: rv })
  const R = (v: number, at: number) => ({ version: v, updatedAt: at })
  it('covers the table', () => {
    expect(decide(L(1, 1, true, null), null)).toBe('UPLOAD')
    expect(decide(null, R(3, 1))).toBe('DOWNLOAD')
    expect(decide(L(3, 1, false, 3), R(3, 1))).toBe('NONE')
    expect(decide(L(4, 2, true, 3), R(3, 1))).toBe('UPLOAD')
    expect(decide(L(3, 1, false, 3), R(5, 9))).toBe('DOWNLOAD')
    expect(decide(L(4, 20, true, 3), R(4, 10))).toBe('CONFLICT_KEEP_LOCAL')
    expect(decide(L(4, 10, true, 3), R(4, 20))).toBe('CONFLICT_TAKE_REMOTE')
    expect(decide(L(6, 10, true, 3), R(5, 10))).toBe('CONFLICT_KEEP_LOCAL')
    expect(decide(L(5, 10, true, 3), R(5, 10))).toBe('CONFLICT_TAKE_REMOTE')
    expect(decide(L(3, 1, false, 3), null)).toBe('NONE')
    expect(uploadVersion(L(4, 20, true, 3), R(7, 10), 'CONFLICT_KEEP_LOCAL')).toBe(8)
  })
})

describe('note format (NoteFormatTest + Android compatibility)', () => {
  // Written exactly as the Android app (kotlinx.serialization, prettyPrint) writes it.
  const fromAndroid = `{
    "formatVersion": 1,
    "id": "n1",
    "notebookId": null,
    "title": "Fizik · 17 Kas",
    "createdAt": "1970-01-01T00:00:00Z",
    "updatedAt": "1970-01-01T00:00:01Z",
    "version": 3,
    "deletedAt": null,
    "bodySha256": "abc",
    "links": [
        {
            "id": "l1",
            "calendarId": "primary",
            "eventId": "ev",
            "recurringEventId": null,
            "instanceStartTime": null,
            "scope": "INSTANCE",
            "eventTitleSnapshot": "Fizik",
            "eventStartSnapshot": "1970-01-01T00:00:05Z",
            "eventDeleted": false,
            "createdAt": "1970-01-01T00:00:00Z",
            "updatedAt": "1970-01-01T00:00:00Z",
            "deletedAt": null
        }
    ]
}`
  it('reads Android files and writes them back identically', () => {
    const file = decodeNote(fromAndroid) as NoteFileV1
    expect(file.title).toBe('Fizik · 17 Kas')
    expect(file.links[0].scope).toBe('INSTANCE')
    expect(encodeNote(file)).toBe(fromAndroid)
  })
  it('rejects newer format versions and ignores unknown fields', () => {
    expect(decodeNote(fromAndroid.replace('"formatVersion": 1', '"formatVersion": 2'))).toBeNull()
    expect(decodeNote(fromAndroid.replace('{', '{"future": 1,'))?.id).toBe('n1')
  })
  it('iso matches java Instant.toString', () => {
    expect(iso(0)).toBe('1970-01-01T00:00:00Z')
    expect(iso(1500)).toBe('1970-01-01T00:00:01.500Z')
  })
  it('sha256 matches Kotlin', async () => {
    expect(await sha256('')).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
  })
})
