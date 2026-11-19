/**
 * Drive storage format v1 (docs/note-format.md), shared with the Android app.
 * Files written here must be byte-for-byte readable by the Kotlin implementation and vice versa.
 */
export const FORMAT_VERSION = 1

export interface NoteLinkV1 {
  id: string
  calendarId: string
  eventId: string
  recurringEventId: string | null
  instanceStartTime: string | null
  scope: 'INSTANCE' | 'SERIES'
  eventTitleSnapshot: string
  eventStartSnapshot: string
  eventDeleted: boolean
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export interface NoteFileV1 {
  formatVersion: number
  id: string
  notebookId: string | null
  title: string
  createdAt: string
  updatedAt: string
  version: number
  deletedAt: string | null
  bodySha256: string
  links: NoteLinkV1[]
}

export interface NotebookLinkV1 {
  id: string
  calendarId: string
  eventId: string
  recurringEventId: string | null
  scope: 'INSTANCE' | 'SERIES'
  eventTitleSnapshot: string
  createdAt: string
  deletedAt: string | null
}

export interface NotebookFileV1 {
  formatVersion: number
  id: string
  title: string
  color: string | null
  createdAt: string
  updatedAt: string
  version: number
  deletedAt: string | null
  eventLinks: NotebookLinkV1[]
}

export const noteMetaName = (id: string) => `note-${id}.json`
export const noteBodyName = (id: string) => `note-${id}.md`
export const notebookName = (id: string) => `notebook-${id}.json`

/** Same field order and 4-space pretty print as kotlinx.serialization's prettyPrint. */
export function encodeNote(file: NoteFileV1): string {
  const ordered: NoteFileV1 = {
    formatVersion: file.formatVersion, id: file.id, notebookId: file.notebookId, title: file.title,
    createdAt: file.createdAt, updatedAt: file.updatedAt, version: file.version, deletedAt: file.deletedAt,
    bodySha256: file.bodySha256,
    links: file.links.map((l) => ({
      id: l.id, calendarId: l.calendarId, eventId: l.eventId, recurringEventId: l.recurringEventId,
      instanceStartTime: l.instanceStartTime, scope: l.scope, eventTitleSnapshot: l.eventTitleSnapshot,
      eventStartSnapshot: l.eventStartSnapshot, eventDeleted: l.eventDeleted, createdAt: l.createdAt,
      updatedAt: l.updatedAt, deletedAt: l.deletedAt,
    })),
  }
  return JSON.stringify(ordered, null, 4)
}

export function encodeNotebook(file: NotebookFileV1): string {
  const ordered: NotebookFileV1 = {
    formatVersion: file.formatVersion, id: file.id, title: file.title, color: file.color,
    createdAt: file.createdAt, updatedAt: file.updatedAt, version: file.version, deletedAt: file.deletedAt,
    eventLinks: file.eventLinks.map((l) => ({
      id: l.id, calendarId: l.calendarId, eventId: l.eventId, recurringEventId: l.recurringEventId,
      scope: l.scope, eventTitleSnapshot: l.eventTitleSnapshot, createdAt: l.createdAt, deletedAt: l.deletedAt,
    })),
  }
  return JSON.stringify(ordered, null, 4)
}

function supported(text: string): Record<string, unknown> | null {
  let obj: unknown
  try {
    obj = JSON.parse(text)
  } catch {
    return null
  }
  if (typeof obj !== 'object' || obj === null) return null
  const v = (obj as Record<string, unknown>).formatVersion
  if (typeof v !== 'number' || v > FORMAT_VERSION) return null // never overwrite newer formats
  return obj as Record<string, unknown>
}

const str = (v: unknown, fallback = ''): string => (typeof v === 'string' ? v : fallback)
const strOrNull = (v: unknown): string | null => (typeof v === 'string' ? v : null)
const scopeOf = (v: unknown): 'INSTANCE' | 'SERIES' => (v === 'SERIES' ? 'SERIES' : 'INSTANCE')

/** Returns null for unreadable files or files from a newer format version. Unknown fields are ignored. */
export function decodeNote(text: string): NoteFileV1 | null {
  const o = supported(text)
  if (!o || typeof o.id !== 'string') return null
  const links = Array.isArray(o.links) ? (o.links as Record<string, unknown>[]) : []
  return {
    formatVersion: o.formatVersion as number,
    id: o.id,
    notebookId: strOrNull(o.notebookId),
    title: str(o.title),
    createdAt: str(o.createdAt),
    updatedAt: str(o.updatedAt),
    version: Number(o.version ?? 0),
    deletedAt: strOrNull(o.deletedAt),
    bodySha256: str(o.bodySha256),
    links: links.map((l) => ({
      id: str(l.id), calendarId: str(l.calendarId), eventId: str(l.eventId),
      recurringEventId: strOrNull(l.recurringEventId), instanceStartTime: strOrNull(l.instanceStartTime),
      scope: scopeOf(l.scope), eventTitleSnapshot: str(l.eventTitleSnapshot), eventStartSnapshot: str(l.eventStartSnapshot),
      eventDeleted: l.eventDeleted === true, createdAt: str(l.createdAt), updatedAt: str(l.updatedAt),
      deletedAt: strOrNull(l.deletedAt),
    })),
  }
}

export function decodeNotebook(text: string): NotebookFileV1 | null {
  const o = supported(text)
  if (!o || typeof o.id !== 'string') return null
  const links = Array.isArray(o.eventLinks) ? (o.eventLinks as Record<string, unknown>[]) : []
  return {
    formatVersion: o.formatVersion as number,
    id: o.id,
    title: str(o.title),
    color: strOrNull(o.color),
    createdAt: str(o.createdAt),
    updatedAt: str(o.updatedAt),
    version: Number(o.version ?? 0),
    deletedAt: strOrNull(o.deletedAt),
    eventLinks: links.map((l) => ({
      id: str(l.id), calendarId: str(l.calendarId), eventId: str(l.eventId),
      recurringEventId: strOrNull(l.recurringEventId), scope: scopeOf(l.scope),
      eventTitleSnapshot: str(l.eventTitleSnapshot), createdAt: str(l.createdAt), deletedAt: strOrNull(l.deletedAt),
    })),
  }
}

/** ISO-8601 UTC like java.time.Instant.toString(): no millis when they are zero. */
export function iso(millis: number): string {
  const s = new Date(millis).toISOString()
  return s.endsWith('.000Z') ? s.slice(0, -5) + 'Z' : s
}
export const isoOrNull = (m: number | null | undefined) => (m == null ? null : iso(m))
export const millis = (s: string) => Date.parse(s)
export const millisOrNull = (s: string | null | undefined) => (s == null ? null : Date.parse(s))

export async function sha256(text: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
}
