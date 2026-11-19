import { useDeferredValue, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { t } from '../../i18n/tr'
import { db } from '../../data/db'
import { textMatches } from '../../core/textSearch'
import { eventColorHex } from '../../core/googleEventColors'
import { allNotesWithLinks } from '../../repo/notes'
import { activeNotebooks } from '../../repo/notebooks'
import { dayOf } from '../../repo/time'
import { dayLong, searchDates, time } from '../format'
import { Icon } from '../common/Icons'
import { nav } from '../store'
import { NoteRow } from '../notebooks/NotebooksPane'

/** Searches notes (title, notebook, linked event name, dates) and cached events (title, location, dates). */
export function SearchPane() {
  const [query, setQuery] = useState('')
  const q = useDeferredValue(query)
  const results = useLiveQuery(async () => {
    if (!q.trim()) return null
    const names = new Map((await activeNotebooks()).map((n) => [n.id, n.title]))
    const notes = (await allNotesWithLinks()).filter((n) =>
      textMatches(
        [n.note.title, n.note.notebookId ? names.get(n.note.notebookId) ?? '' : '', searchDates(n.note.createdAt),
          ...n.links.flatMap((l) => [l.eventTitleSnapshot, searchDates(l.eventStartSnapshot)])].join(' '),
        q,
      ),
    ).sort((a, b) => Math.max(b.note.updatedAt, ...b.links.map((l) => l.eventStartSnapshot)) - Math.max(a.note.updatedAt, ...a.links.map((l) => l.eventStartSnapshot)))
    const calendars = new Map((await db.calendars.toArray()).filter((c) => c.visible).map((c) => [c.id, c]))
    const nowMs = Date.now()
    const events = (await db.events.filter((e) => calendars.has(e.calendarId) && e.status !== 'cancelled').toArray())
      .filter((e) => textMatches(`${e.title} ${e.location} ${searchDates(e.startMillis)}`, q))
      .sort((a, b) => Math.abs(a.startMillis - nowMs) - Math.abs(b.startMillis - nowMs))
      .slice(0, 50)
      .sort((a, b) => a.startMillis - b.startMillis)
      .map((e) => ({ e, color: eventColorHex(e.colorId) ?? calendars.get(e.calendarId)!.colorHex }))
    return { notes, events }
  }, [q])

  return (
    <div className="scroll section-pad">
      <div className="row" style={{ position: 'relative' }}>
        <span className="muted" style={{ position: 'absolute', left: 12, top: 11 }}><Icon name="search" /></span>
        <input className="input" style={{ paddingLeft: 44, borderRadius: 14 }} autoFocus placeholder={t.searchHint}
          value={query} onChange={(e) => setQuery(e.target.value)} type="search" />
      </div>
      {!query.trim() && <div className="muted">{t.searchStart}</div>}
      {results && results.notes.length === 0 && results.events.length === 0 && <div className="muted">{t.searchNone}</div>}
      {results && results.notes.length > 0 && <h3>{t.searchNotes}</h3>}
      {results?.notes.map((n) => <NoteRow key={n.note.id} n={n} onClick={() => nav.show({ kind: 'note', noteId: n.note.id })} />)}
      {results && results.events.length > 0 && <h3>{t.searchEvents}</h3>}
      {results?.events.map(({ e, color }) => (
        <div key={e.calendarId + e.id} className="list-row" onClick={() => nav.show({ kind: 'event', calendarId: e.calendarId, eventId: e.id })}>
          <span className="dot" style={{ background: color }} />
          <div className="grow">
            <div className="ellipsis">{e.title || t.noTitle}</div>
            <div className="muted small">{dayLong(dayOf(e.startMillis))}{e.allDay ? '' : ' · ' + time(e.startMillis)}</div>
          </div>
        </div>
      ))}
    </div>
  )
}
