import { AuthRequiredError, HttpError, OfflineError } from '../api/http'
import { getAccessToken } from '../auth/session'
import { db } from '../data/db'
import { flushOutbox, refreshCalendars, refreshRange } from './calendar'
import { syncDrive } from './driveSync'
import { reconcileMarkers } from './markers'

/**
 * Sync coordinator (port of SyncCoordinator.kt). Runs on app start, when the app comes back to the
 * foreground, on the refresh button, every few minutes while open, and a few seconds after edits.
 */
export type CalendarStatus =
  | { kind: 'idle' } | { kind: 'running' } | { kind: 'done'; at: number }
  | { kind: 'offline' } | { kind: 'auth' } | { kind: 'failed'; message: string }

export type DriveStatus =
  | { kind: 'idle' } | { kind: 'running' } | { kind: 'synced'; at: number }
  | { kind: 'offline' } | { kind: 'auth' } | { kind: 'noPermission' } | { kind: 'failed'; message: string }

export interface SyncState { calendar: CalendarStatus; drive: DriveStatus }

let state: SyncState = { calendar: { kind: 'idle' }, drive: { kind: 'idle' } }
const listeners = new Set<() => void>()
const set = (patch: Partial<SyncState>) => {
  state = { ...state, ...patch }
  listeners.forEach((l) => l())
}
export const getSyncState = () => state
export const subscribeSync = (l: () => void) => {
  listeners.add(l)
  return () => listeners.delete(l)
}

/** Records the last unexpected error so Settings can show it (no tokens, no note content). */
export function recordError(e: unknown) {
  const text = e instanceof Error ? `${e.name}: ${e.message}\n${e.stack ?? ''}` : String(e)
  try {
    localStorage.setItem('calnotes.lastError', `${new Date().toISOString()}\n${text}`.slice(0, 4000))
  } catch {
    // ignore
  }
}
export const lastError = () => localStorage.getItem('calnotes.lastError')

const WINDOW_BACK = 92 * 86_400_000
const WINDOW_AHEAD = 366 * 86_400_000

function calendarError(e: unknown): CalendarStatus {
  if (e instanceof AuthRequiredError) return { kind: 'auth' }
  if (e instanceof OfflineError) return { kind: 'offline' }
  if (e instanceof HttpError) return { kind: 'failed', message: `HTTP ${e.status}` }
  recordError(e)
  return { kind: 'failed', message: (e as Error)?.name ?? 'Error' }
}

let calendarRunning = false
let driveRunning = false

async function guardedCalendar(block: () => Promise<void>): Promise<boolean> {
  set({ calendar: { kind: 'running' } })
  try {
    await block()
    set({ calendar: { kind: 'done', at: Date.now() } })
    return true
  } catch (e) {
    set({ calendar: calendarError(e) })
    return false
  }
}

export async function syncNotes(): Promise<boolean> {
  if (!getAccessToken()) {
    set({ drive: { kind: 'auth' } })
    return false
  }
  if (driveRunning) return true
  driveRunning = true
  set({ drive: { kind: 'running' } })
  try {
    await syncDrive()
    set({ drive: { kind: 'synced', at: Date.now() } })
    return true
  } catch (e) {
    if (e instanceof AuthRequiredError) set({ drive: { kind: 'auth' } })
    else if (e instanceof OfflineError) set({ drive: { kind: 'offline' } })
    else if (e instanceof HttpError && (e.status === 403 || e.status === 401)) set({ drive: { kind: 'noPermission' } })
    else if (e instanceof HttpError) set({ drive: { kind: 'failed', message: `HTTP ${e.status}` } })
    else {
      recordError(e)
      set({ drive: { kind: 'failed', message: (e as Error)?.name ?? 'Error' } })
    }
    return false
  } finally {
    driveRunning = false
  }
}

/** Full sync: outbox → calendars → notes (Drive) → markers → cache window. */
export async function syncAll(): Promise<boolean> {
  if (!getAccessToken()) {
    set({ calendar: { kind: 'auth' }, drive: { kind: 'auth' } })
    return false
  }
  if (calendarRunning) return true
  calendarRunning = true
  try {
    const first = await guardedCalendar(async () => {
      await flushOutbox()
      await refreshCalendars()
    })
    const notesOk = await syncNotes()
    const rest = await guardedCalendar(async () => {
      await reconcileMarkers()
      const t = Date.now()
      await refreshRange(t - WINDOW_BACK, t + WINDOW_AHEAD)
    })
    return first && notesOk && rest
  } finally {
    calendarRunning = false
  }
}

/** Refreshes only the visible range (cheap; used when navigating). */
export async function syncRange(from: number, to: number) {
  if (!getAccessToken() || calendarRunning) return
  await guardedCalendar(async () => {
    await flushOutbox()
    await refreshRange(from, to)
  })
}

/** After local edits: send the outbox, notes and markers soon. */
let pushTimer: ReturnType<typeof setTimeout> | undefined
export function pushChangesSoon(delayMs = 3000) {
  clearTimeout(pushTimer)
  pushTimer = setTimeout(async () => {
    if (!getAccessToken()) return
    await guardedCalendar(async () => {
      await flushOutbox()
    })
    if (await syncNotes()) await guardedCalendar(reconcileMarkers)
  }, delayMs)
}

/** Count of notes + notebooks not yet in the cloud. */
export async function pendingNoteChanges(): Promise<number> {
  const [a, b] = await Promise.all([db.notes.where('dirty').equals(1).count(), db.notebooks.where('dirty').equals(1).count()])
  return a + b
}
