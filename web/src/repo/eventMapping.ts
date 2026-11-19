import type { GEvent, EventDateTime } from '../api/calendar'
import type { EventRow } from '../data/db'
import { applyDescription, applyTitle, stripDescription, stripTitle } from '../core/calendarMarker'
import { toRecurrence, type RepeatRule } from '../core/googleEventColors'
import { addDays, localMillis, rfc3339Local, timeZone } from './time'

/** What the event editor edits. endDate is inclusive for all-day events. */
export interface EventDraft {
  calendarId: string
  title: string
  description: string
  location: string
  allDay: boolean
  startDate: string
  startTime: string
  endDate: string
  endTime: string
  repeat: RepeatRule
}

export const draftStart = (d: EventDraft) => (d.allDay ? localMillis(d.startDate) : localMillis(d.startDate, d.startTime))
export const draftEnd = (d: EventDraft) => (d.allDay ? localMillis(addDays(d.endDate, 1)) : localMillis(d.endDate, d.endTime))

export function parseMillis(dt?: EventDateTime | null): number | null {
  if (!dt) return null
  if (dt.dateTime) {
    const v = Date.parse(dt.dateTime)
    return Number.isNaN(v) ? null : v
  }
  if (dt.date) return localMillis(dt.date)
  return null
}

/** API event → cache row (markers stripped, flags kept), or null without a usable start. */
export function toRow(dto: GEvent, calendarId: string, calendarWritable: boolean): EventRow | null {
  const start = parseMillis(dto.start)
  if (start == null) return null
  const end = parseMillis(dto.end) ?? start
  const organizerIsUs = !dto.organizer || dto.organizer.self === true || dto.organizer.email === calendarId
  const readOnlyType = dto.eventType === 'birthday' || dto.eventType === 'fromGmail'
  const title = stripTitle(dto.summary ?? '')
  const description = stripDescription(dto.description ?? '')
  return {
    calendarId,
    id: dto.id,
    recurringEventId: dto.recurringEventId ?? null,
    originalStartMillis: parseMillis(dto.originalStartTime),
    title: title.text,
    description: description.text,
    location: dto.location ?? '',
    startMillis: start,
    endMillis: Math.max(end, start),
    allDay: !!dto.start?.date,
    timeZone: dto.start?.timeZone ?? null,
    colorId: dto.colorId ?? null,
    status: dto.status ?? 'confirmed',
    canEdit: calendarWritable && !readOnlyType && (organizerIsUs || dto.guestsCanModify === true),
    htmlLink: dto.htmlLink ?? null,
    pendingSync: false,
    markerInTitle: title.hadMarker,
    markerInDescription: description.hadMarker,
  }
}

function dateTimeOf(allDay: boolean, date: string, time: string, isEnd: boolean) {
  return allDay
    ? { date: isEnd ? addDays(date, 1) : date, dateTime: null, timeZone: timeZone() }
    : { date: null, dateTime: rfc3339Local(localMillis(date, time)), timeZone: timeZone() }
}

/**
 * Insert/patch body. Unused date fields are explicit nulls so PATCH can switch all-day ↔ timed.
 * Markers are re-added so in-app edits keep them.
 */
export function toPayload(
  d: EventDraft,
  opts: { includeTimes: boolean; includeRecurrence: boolean; markerInTitle?: boolean; markerInDescription?: boolean },
): Record<string, unknown> {
  const body: Record<string, unknown> = {
    summary: applyTitle(d.title, !!opts.markerInTitle),
    description: applyDescription(d.description, !!opts.markerInDescription),
    location: d.location,
  }
  if (opts.includeTimes) {
    body.start = dateTimeOf(d.allDay, d.startDate, d.startTime, false)
    body.end = dateTimeOf(d.allDay, d.endDate, d.endTime, true)
  }
  if (opts.includeRecurrence) {
    const rec = toRecurrence(d.repeat)
    if (rec) body.recurrence = rec
  }
  return body
}
