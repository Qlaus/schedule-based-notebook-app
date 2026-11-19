import Dexie, { type Table } from 'dexie'
import type { EventLinkScope } from '../core/model'

/** Local IndexedDB schema; mirrors the Android Room entities. Booleans used in indexes are 0/1. */
export interface Note {
  id: string
  accountId: string
  notebookId: string | null
  title: string
  body: string
  createdAt: number
  updatedAt: number
  deletedAt: number | null
  version: number
  dirty: 0 | 1
  remoteFileId: string | null
  remoteVersion: number | null
}

export interface EventLink {
  id: string
  noteId: string
  accountId: string
  calendarId: string
  eventId: string
  recurringEventId: string | null
  instanceStartTime: number | null
  scope: EventLinkScope
  eventTitleSnapshot: string
  eventStartSnapshot: number
  eventDeleted: boolean
  createdAt: number
  updatedAt: number
  deletedAt: number | null
}

export interface Notebook {
  id: string
  accountId: string
  title: string
  color: string | null
  createdAt: number
  updatedAt: number
  deletedAt: number | null
  version: number
  dirty: 0 | 1
  remoteFileId: string | null
  remoteVersion: number | null
}

export interface NotebookLink {
  id: string
  notebookId: string
  accountId: string
  calendarId: string
  eventId: string
  recurringEventId: string | null
  scope: EventLinkScope
  eventTitleSnapshot: string
  createdAt: number
  deletedAt: number | null
}

export interface NoteBackup {
  id: string
  noteId: string
  version: number
  title: string
  body: string
  metaJson: string
  reason: string
  createdAt: number
}

export interface CalendarRow {
  id: string
  accountId: string
  summary: string
  colorHex: string
  accessRole: string
  isPrimary: boolean
  visible: boolean
}

export interface EventRow {
  calendarId: string
  id: string
  recurringEventId: string | null
  originalStartMillis: number | null
  title: string
  description: string
  location: string
  startMillis: number
  endMillis: number
  allDay: boolean
  timeZone: string | null
  colorId: string | null
  status: string
  canEdit: boolean
  htmlLink: string | null
  pendingSync: boolean
  markerInTitle: boolean
  markerInDescription: boolean
}

export interface PendingOp {
  id?: number
  type: 'CREATE' | 'UPDATE' | 'DELETE'
  calendarId: string
  eventId: string
  payload: Record<string, unknown> | null
  createdAt: number
  attempts: number
  lastError: string | null
}

export interface EventMarker {
  calendarId: string
  eventId: string
  titleApplied: boolean
  descriptionApplied: boolean
  noteIds: string
  skipped: boolean
  updatedAt: number
}

export interface KV { key: string; value: unknown }

export const isWritable = (c: CalendarRow) => c.accessRole === 'owner' || c.accessRole === 'writer'

export class AppDb extends Dexie {
  notes!: Table<Note, string>
  eventLinks!: Table<EventLink, string>
  notebooks!: Table<Notebook, string>
  notebookLinks!: Table<NotebookLink, string>
  noteBackups!: Table<NoteBackup, string>
  calendars!: Table<CalendarRow, string>
  events!: Table<EventRow, [string, string]>
  pendingOps!: Table<PendingOp, number>
  eventMarkers!: Table<EventMarker, [string, string]>
  kv!: Table<KV, string>

  constructor(name = 'calnotes') {
    super(name)
    this.version(1).stores({
      notes: 'id, notebookId, dirty',
      eventLinks: 'id, noteId, [calendarId+eventId], [calendarId+recurringEventId]',
      notebooks: 'id, dirty',
      notebookLinks: 'id, notebookId',
      noteBackups: 'id, noteId',
      calendars: 'id',
      events: '[calendarId+id], calendarId, startMillis, [calendarId+recurringEventId]',
      pendingOps: '++id',
      eventMarkers: '[calendarId+eventId]',
      kv: 'key',
    })
  }
}

export let db = new AppDb()

/** Tests use a fresh database per case. */
export function useTestDb(name: string): AppDb {
  db = new AppDb(name)
  return db
}

export const uuid = () => crypto.randomUUID()
export const now = () => Date.now()
