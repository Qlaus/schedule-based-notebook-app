import { useLiveQuery } from 'dexie-react-hooks'
import { db, type EventRow } from '../../data/db'
import { linkMatches } from '../../core/model'
import { eventColorHex } from '../../core/googleEventColors'
import { eventRef } from '../../repo/notes'
import { addDays, dayOf, localMillis, monthGridStart, weekStart } from '../../repo/time'
import type { Mode } from '../store'

export interface EventUi { event: EventRow; calendarName: string; color: string; hasNotes: boolean }

export const AGENDA_DAYS = 60

export function rangeFor(mode: Mode, anchor: string): { start: string; days: number } {
  switch (mode) {
    case 'day': return { start: anchor, days: 1 }
    case 'week': return { start: weekStart(anchor), days: 7 }
    case 'month': return { start: monthGridStart(anchor), days: 42 }
    case 'agenda': return { start: anchor, days: AGENDA_DAYS }
  }
}

export const rangeMillis = (r: { start: string; days: number }) => ({
  from: localMillis(r.start),
  to: localMillis(addDays(r.start, r.days)),
})

/** Events of visible calendars overlapping [from, to), with colors and note markers; live from IndexedDB. */
export function useEvents(from: number, to: number): EventUi[] {
  return useLiveQuery(async () => {
    const calendars = new Map((await db.calendars.toArray()).filter((c) => c.visible).map((c) => [c.id, c]))
    const rows = await db.events.where('startMillis').below(to)
      .filter((e) => e.endMillis > from && e.status !== 'cancelled' && calendars.has(e.calendarId)).toArray()
    const links = await db.eventLinks.filter((l) => l.deletedAt == null).toArray()
    return rows
      .sort((a, b) => Number(b.allDay) - Number(a.allDay) || a.startMillis - b.startMillis || b.endMillis - a.endMillis)
      .map((e) => {
        const cal = calendars.get(e.calendarId)!
        return {
          event: e,
          calendarName: cal.summary,
          color: eventColorHex(e.colorId) ?? cal.colorHex,
          hasNotes: links.some((l) => linkMatches(l, eventRef(e))),
        }
      })
  }, [from, to]) ?? []
}

/** Groups events by every local day they touch, within [start, start + days). */
export function byDay(events: EventUi[], start: string, days: number): Map<string, EventUi[]> {
  const out = new Map<string, EventUi[]>()
  const end = addDays(start, days)
  for (const e of events) {
    let day = dayOf(e.event.startMillis)
    const last = dayOf(Math.max(e.event.startMillis, e.event.endMillis - 1))
    if (day < start) day = start
    while (day <= last && day < end) {
      out.set(day, [...(out.get(day) ?? []), e])
      day = addDays(day, 1)
    }
  }
  return out
}

/** Translucent background for an event color. */
export const soft = (hex: string, alpha = 0.18) => {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex)
  if (!m) return hex
  const n = parseInt(m[1], 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`
}
