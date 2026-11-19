import { googleFetch, googleJson, HttpError } from './http'

/** Google Calendar REST v3 (CORS-enabled, bearer token). */
const BASE = 'https://www.googleapis.com/calendar/v3'

export interface CalendarListEntry {
  id: string
  summary?: string
  summaryOverride?: string
  backgroundColor?: string
  accessRole?: string
  primary?: boolean
  selected?: boolean
  hidden?: boolean
}

export interface EventDateTime { date?: string | null; dateTime?: string | null; timeZone?: string | null }

export interface GEvent {
  id: string
  status?: string
  summary?: string
  description?: string
  location?: string
  start?: EventDateTime
  end?: EventDateTime
  recurringEventId?: string
  originalStartTime?: EventDateTime
  recurrence?: string[]
  colorId?: string
  organizer?: { email?: string; self?: boolean }
  guestsCanModify?: boolean
  eventType?: string
  htmlLink?: string
  extendedProperties?: { private?: Record<string, string> }
}

const enc = encodeURIComponent

export async function listCalendars(): Promise<CalendarListEntry[]> {
  const out: CalendarListEntry[] = []
  let page: string | undefined
  do {
    const q = new URLSearchParams(page ? { pageToken: page } : {})
    const res = await googleJson<{ items?: CalendarListEntry[]; nextPageToken?: string }>(`${BASE}/users/me/calendarList?${q}`)
    out.push(...(res.items ?? []))
    page = res.nextPageToken
  } while (page)
  return out
}

export async function listEvents(calendarId: string, timeMin: string, timeMax: string): Promise<GEvent[]> {
  const out: GEvent[] = []
  let page: string | undefined
  do {
    const q = new URLSearchParams({ timeMin, timeMax, singleEvents: 'true', maxResults: '2500' })
    if (page) q.set('pageToken', page)
    const res = await googleJson<{ items?: GEvent[]; nextPageToken?: string }>(`${BASE}/calendars/${enc(calendarId)}/events?${q}`)
    out.push(...(res.items ?? []))
    page = res.nextPageToken
  } while (page)
  return out
}

/** Returns null when the event no longer exists (404/410). */
export async function getEvent(calendarId: string, eventId: string): Promise<GEvent | null> {
  const res = await googleFetch(`${BASE}/calendars/${enc(calendarId)}/events/${enc(eventId)}`)
  if (res.status === 404 || res.status === 410) return null
  if (!res.ok) throw new HttpError(res.status, `HTTP ${res.status}`)
  return (await res.json()) as GEvent
}

export const insertEvent = (calendarId: string, body: object) =>
  googleJson<GEvent>(`${BASE}/calendars/${enc(calendarId)}/events`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

export const patchEvent = (calendarId: string, eventId: string, body: object) =>
  googleJson<GEvent>(`${BASE}/calendars/${enc(calendarId)}/events/${enc(eventId)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

export async function deleteEvent(calendarId: string, eventId: string): Promise<void> {
  const res = await googleFetch(`${BASE}/calendars/${enc(calendarId)}/events/${enc(eventId)}`, { method: 'DELETE' })
  if (!res.ok && res.status !== 404 && res.status !== 410) throw new HttpError(res.status, `HTTP ${res.status}`)
}
