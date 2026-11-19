import { useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { t } from '../../i18n/tr'
import { db, type EventRow } from '../../data/db'
import type { EventLinkScope } from '../../core/model'
import { eventColorHex } from '../../core/googleEventColors'
import { createForEvent, eventRef, notesForEvent } from '../../repo/notes'
import { activeNotebooks, createNotebook, linkEvent, notebookLinkFor, unlink } from '../../repo/notebooks'
import { deleteEvent } from '../../repo/calendar'
import { pushChangesSoon } from '../../repo/sync'
import { dayOf } from '../../repo/time'
import { dayLong, dayShort, time } from '../format'
import { Icon, type IconName } from '../common/Icons'
import { ConfirmDialog, ScopeDialog } from '../common/Dialogs'
import { PaneHead } from '../common/PaneHead'
import { NotebookPickerDialog } from '../notebooks/NotebookDialogs'

const defaultTitle = (e: EventRow) => [e.title || null, dayShort(e.startMillis)].filter(Boolean).join(' · ')

export function EventDetail({ calendarId, eventId, canGoBack, onBack, onEdit, onOpenNote, onDeleted }: {
  calendarId: string; eventId: string; canGoBack: boolean; onBack: () => void; onEdit: () => void
  onOpenNote: (id: string) => void; onDeleted: () => void
}) {
  const data = useLiveQuery(async () => {
    const event = await db.events.get([calendarId, eventId])
    if (!event) return { event: null }
    const cal = await db.calendars.get(calendarId)
    return {
      event,
      calendarName: cal?.summary ?? '',
      color: eventColorHex(event.colorId) ?? cal?.colorHex ?? 'var(--accent)',
      notes: await notesForEvent(eventRef(event)),
      notebookLink: await notebookLinkFor(eventRef(event)),
      notebooks: await activeNotebooks(),
    }
  }, [calendarId, eventId])

  const [ask, setAsk] = useState<null | 'noteScope' | 'delete' | 'pickNotebook'>(null)
  const [pendingNotebook, setPendingNotebook] = useState<{ id: string | null; title: string | null } | null>(null)

  if (!data) return <PaneHead canGoBack={canGoBack} onBack={onBack} />
  const e = data.event
  if (!e) {
    return (<><PaneHead canGoBack={canGoBack} onBack={onBack} /><div className="empty-hint">{t.eventNotFound}</div></>)
  }
  const { notes = [], notebookLink, notebooks = [] } = data

  const createNote = async (scope: EventLinkScope) => {
    const notebookId = (await notebookLinkFor(eventRef(e)))?.notebook.id ?? null
    const id = await createForEvent(e, scope, defaultTitle(e), notebookId)
    pushChangesSoon()
    onOpenNote(id)
  }
  const link = async (notebookId: string | null, title: string | null, scope: EventLinkScope) => {
    const target = notebookId ?? (title ? await createNotebook(title, null) : null)
    if (!target) return
    await linkEvent(e, scope, target)
    const id = await createForEvent(e, 'INSTANCE', defaultTitle(e), target)
    pushChangesSoon()
    onOpenNote(id)
  }
  const remove = async (wholeSeries: boolean) => {
    setAsk(null)
    await deleteEvent(e.calendarId, e.id, wholeSeries)
    pushChangesSoon(500)
    onDeleted()
  }
  const isRecurring = !!e.recurringEventId

  return (
    <>
      <PaneHead canGoBack={canGoBack} onBack={onBack}>
        {e.htmlLink && <a className="icon-btn" href={e.htmlLink} target="_blank" rel="noreferrer" aria-label={t.openInGoogle}><Icon name="external" /></a>}
        {e.canEdit && <button className="icon-btn" onClick={onEdit} aria-label={t.edit}><Icon name="edit" /></button>}
        {e.canEdit && <button className="icon-btn error" onClick={() => setAsk('delete')} aria-label={t.delete}><Icon name="trash" /></button>}
      </PaneHead>
      <div className="scroll section-pad" style={{ paddingTop: 0 }}>
        <h1>{e.title || t.noTitle}</h1>
        <div className="row muted">
          <span className="dot" style={{ background: data.color }} />
          <span>{data.calendarName}</span>
          {e.pendingSync && <span>· {t.pendingSync}</span>}
        </div>
        <Info icon="clock" text={timeText(e)} />
        {isRecurring && <Info icon="repeat" text={t.recurring} />}
        {e.location && <Info icon="place" text={e.location} />}
        {e.description && <Info icon="text" text={e.description} />}
        {!e.canEdit && <div className="muted small">{t.readOnly}</div>}
        <hr style={{ border: 0, borderTop: '0.5px solid var(--line)', width: '100%' }} />

        {notebookLink ? (
          <div className="row">
            <span className="dot" style={{ background: notebookLink.notebook.color ?? 'var(--accent)' }} />
            <span className="grow">
              {notebookLink.link.scope === 'SERIES' ? t.linkedNotebookSeries(notebookLink.notebook.title) : t.linkedNotebook(notebookLink.notebook.title)}
            </span>
            <button className="btn text" onClick={() => void unlink(notebookLink.link.id).then(() => pushChangesSoon())}>{t.unlinkNotebook}</button>
          </div>
        ) : (
          <button className="btn outline" style={{ alignSelf: 'flex-start' }} onClick={() => setAsk('pickNotebook')}>
            <Icon name="book" /> {t.linkNotebook}
          </button>
        )}

        <h3>{t.notes}</h3>
        {notes.length === 0 && <div className="muted">{t.noNotesYet}</div>}
        {notes.map((n) => (
          <div key={n.id} className="card flat click" onClick={() => onOpenNote(n.id)}>
            <div style={{ fontWeight: 600 }}>{n.title || t.noTitle}</div>
            {n.body && <div className="muted small" style={{ display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{n.body}</div>}
          </div>
        ))}
        <button className={`btn block ${notes.length === 0 ? 'primary' : 'tonal'}`}
          onClick={() => (isRecurring ? setAsk('noteScope') : void createNote('INSTANCE'))}>
          <Icon name="plus" /> {notes.length === 0 ? t.createNote : t.addAnotherNote}
        </button>
      </div>

      {ask === 'noteScope' && (
        <ScopeDialog title={t.scopeNoteTitle} onClose={() => setAsk(null)}
          onInstance={() => { setAsk(null); void createNote('INSTANCE') }}
          onSeries={() => { setAsk(null); void createNote('SERIES') }} />
      )}
      {ask === 'pickNotebook' && (
        <NotebookPickerDialog notebooks={notebooks} onClose={() => setAsk(null)}
          onPick={(id) => { setAsk(null); if (isRecurring) setPendingNotebook({ id, title: null }); else void link(id, null, 'INSTANCE') }}
          onCreate={(title) => { setAsk(null); if (isRecurring) setPendingNotebook({ id: null, title }); else void link(null, title, 'INSTANCE') }} />
      )}
      {pendingNotebook && (
        <ScopeDialog title={t.scopeNotebookTitle} hint={t.scopeNotebookSeriesHint} onClose={() => setPendingNotebook(null)}
          onInstance={() => { const p = pendingNotebook; setPendingNotebook(null); void link(p.id, p.title, 'INSTANCE') }}
          onSeries={() => { const p = pendingNotebook; setPendingNotebook(null); void link(p.id, p.title, 'SERIES') }} />
      )}
      {ask === 'delete' && (isRecurring ? (
        <ScopeDialog title={t.scopeDeleteTitle} hint={t.deleteEventBody} onClose={() => setAsk(null)}
          onInstance={() => void remove(false)} onSeries={() => void remove(true)} />
      ) : (
        <ConfirmDialog title={t.deleteEventTitle} body={t.deleteEventBody} confirm={t.delete} onClose={() => setAsk(null)} onConfirm={() => void remove(false)} />
      ))}
    </>
  )
}

function Info({ icon, text }: { icon: IconName; text: string }) {
  return (
    <div className="row" style={{ alignItems: 'flex-start', gap: 14 }}>
      <span className="muted" style={{ flex: 'none' }}><Icon name={icon} /></span>
      <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{text}</span>
    </div>
  )
}

function timeText(e: EventRow): string {
  const startDay = dayOf(e.startMillis)
  if (e.allDay) {
    const last = dayOf(e.endMillis - 1)
    return last === startDay ? `${dayLong(startDay)} · ${t.allDay}` : `${dayLong(startDay)} – ${dayLong(last)}`
  }
  const endDay = dayOf(e.endMillis)
  return endDay === startDay
    ? `${dayLong(startDay)}\n${time(e.startMillis)} – ${time(e.endMillis)}`
    : `${dayLong(startDay)} ${time(e.startMillis)} –\n${dayLong(endDay)} ${time(e.endMillis)}`
}
