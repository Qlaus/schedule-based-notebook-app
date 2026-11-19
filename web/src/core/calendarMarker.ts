/**
 * "This event has notes" markers written to Google Calendar (port of CalendarMarker.kt).
 * Only these functions add/remove them, so the user's own text is never changed.
 */
export const TITLE_PREFIX = '📝 '
export const DESCRIPTION_BLOCK = '📝 Notes attached (Not Takvimi)'
export const NOTE_IDS_PROPERTY = 'calnotesNoteIds'
const MAX_PROPERTY_LENGTH = 1024

export interface Stripped { text: string; hadMarker: boolean }

export function stripTitle(title: string): Stripped {
  return title.startsWith(TITLE_PREFIX)
    ? { text: title.slice(TITLE_PREFIX.length), hadMarker: true }
    : { text: title, hadMarker: false }
}

export function applyTitle(title: string, marked: boolean): string {
  const base = stripTitle(title).text
  return marked ? TITLE_PREFIX + base : base
}

export function stripDescription(description: string): Stripped {
  const trimmed = description.replace(/\s+$/, '')
  if (!trimmed.endsWith(DESCRIPTION_BLOCK)) return { text: description, hadMarker: false }
  let rest = trimmed.slice(0, trimmed.length - DESCRIPTION_BLOCK.length)
  for (;;) {
    const next = rest.replace(/[ \n\r]+$/, '').replace(/(<br>|<br\/>|<br \/>)$/, '')
    if (next === rest) break
    rest = next
  }
  return { text: rest, hadMarker: true }
}

export function applyDescription(description: string, marked: boolean): string {
  const base = stripDescription(description).text
  if (!marked) return base
  return base.length === 0 ? DESCRIPTION_BLOCK : base + '\n\n' + DESCRIPTION_BLOCK
}

/** Kotlin's String.sorted() order is by UTF-16 code unit, same as JS default sort. */
export function encodeNoteIds(ids: string[]): string {
  let out = ''
  for (const id of [...ids].sort()) {
    const extra = out.length === 0 ? id.length : id.length + 1
    if (out.length + extra > MAX_PROPERTY_LENGTH) break
    out = out.length === 0 ? id : out + ',' + id
  }
  return out
}
