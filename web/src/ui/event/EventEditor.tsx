import { useEffect, useState } from 'react'
import { t } from '../../i18n/tr'
import { db, isWritable, type CalendarRow } from '../../data/db'
import { repeatRules, type RepeatRule } from '../../core/googleEventColors'
import { createEvent, updateEvent } from '../../repo/calendar'
import { draftEnd, draftStart, type EventDraft } from '../../repo/eventMapping'
import { pushChangesSoon } from '../../repo/sync'
import { addDays, dayOf, localMillis, pad, timeOf } from '../../repo/time'
import { PaneHead } from '../common/PaneHead'
import { ScopeDialog } from '../common/Dialogs'

export function EventEditor({ calendarId, eventId, presetDate, presetTime, canGoBack, onBack, onSaved }: {
  calendarId?: string; eventId?: string; presetDate?: string; presetTime?: string
  canGoBack: boolean; onBack: () => void; onSaved: () => void
}) {
  const [draft, setDraft] = useState<EventDraft | null>(null)
  const [calendars, setCalendars] = useState<CalendarRow[]>([])
  const [isRecurring, setRecurring] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [askScope, setAskScope] = useState(false)
  const isNew = !eventId

  useEffect(() => {
    void (async () => {
      const writable = (await db.calendars.toArray()).filter(isWritable)
      setCalendars(writable)
      const existing = calendarId && eventId ? await db.events.get([calendarId, eventId]) : undefined
      if (existing) {
        setRecurring(!!existing.recurringEventId)
        const endDay = dayOf(existing.endMillis)
        setDraft({
          calendarId: existing.calendarId, title: existing.title, description: existing.description, location: existing.location,
          allDay: existing.allDay, startDate: dayOf(existing.startMillis), startTime: timeOf(existing.startMillis),
          // All-day ends are exclusive in storage; the editor shows the inclusive last day.
          endDate: existing.allDay ? (addDays(endDay, -1) < dayOf(existing.startMillis) ? dayOf(existing.startMillis) : addDays(endDay, -1)) : endDay,
          endTime: timeOf(existing.endMillis), repeat: 'NONE',
        })
      } else {
        const date = presetDate ?? dayOf(Date.now())
        const startTime = presetTime ?? `${pad(Math.min(new Date().getHours() + 1, 23))}:00`
        const end = localMillis(date, startTime) + 3_600_000
        const cal = writable.find((c) => c.isPrimary) ?? writable[0]
        setDraft({
          calendarId: cal?.id ?? '', title: '', description: '', location: '', allDay: false,
          startDate: date, startTime, endDate: dayOf(end), endTime: timeOf(end), repeat: 'NONE',
        })
      }
    })()
  }, [calendarId, eventId, presetDate, presetTime])

  if (!draft) return <PaneHead canGoBack={canGoBack} onBack={onBack} />

  /** Moving the start keeps the duration, like most calendar apps. */
  const update = (patch: Partial<EventDraft>) => {
    let next = { ...draft, ...patch }
    if (patch.startDate !== undefined || patch.startTime !== undefined) {
      const duration = Math.max(0, localMillis(draft.endDate, draft.endTime) - localMillis(draft.startDate, draft.startTime))
      const end = localMillis(next.startDate, next.startTime) + duration
      next = { ...next, endDate: dayOf(end), endTime: timeOf(end) }
    }
    setDraft(next)
    setError(null)
  }

  const save = async (wholeSeries: boolean) => {
    if (!draft.calendarId) return setError(t.errorNoCalendar)
    if (draftEnd(draft) < draftStart(draft)) return setError(t.errorEndBeforeStart)
    const clean = { ...draft, title: draft.title.trim() }
    if (isNew) await createEvent(clean)
    else await updateEvent(eventId!, clean, wholeSeries)
    pushChangesSoon(500)
    onSaved()
  }

  return (
    <>
      <PaneHead canGoBack={canGoBack} onBack={onBack}>
        <button className="btn primary" onClick={() => (!isNew && isRecurring ? setAskScope(true) : void save(false))}>{t.save}</button>
      </PaneHead>
      <div className="scroll section-pad" style={{ paddingTop: 0 }}>
        <h2>{isNew ? t.editorNew : t.editorEdit}</h2>
        <input className="input" placeholder={t.fieldTitle} value={draft.title} autoFocus={isNew} onChange={(e) => update({ title: e.target.value })} />
        <label className="row">
          <span className="grow">{t.allDay}</span>
          <span className="switch"><input type="checkbox" checked={draft.allDay} onChange={(e) => update({ allDay: e.target.checked })} /><span /></span>
        </label>
        <div className="field">
          <label>{t.fieldStarts}</label>
          <div className="row">
            <input className="input grow" type="date" value={draft.startDate} onChange={(e) => e.target.value && update({ startDate: e.target.value })} />
            {!draft.allDay && <input className="input" style={{ width: 120 }} type="time" value={draft.startTime} onChange={(e) => e.target.value && update({ startTime: e.target.value })} />}
          </div>
        </div>
        <div className="field">
          <label>{t.fieldEnds}</label>
          <div className="row">
            <input className="input grow" type="date" value={draft.endDate} onChange={(e) => e.target.value && update({ endDate: e.target.value })} />
            {!draft.allDay && <input className="input" style={{ width: 120 }} type="time" value={draft.endTime} onChange={(e) => e.target.value && update({ endTime: e.target.value })} />}
          </div>
        </div>
        {error && <div className="error">{error}</div>}
        {isNew ? (
          <>
            <div className="field">
              <label>{t.fieldCalendar}</label>
              <select className="input" value={draft.calendarId} onChange={(e) => update({ calendarId: e.target.value })}>
                {calendars.map((c) => <option key={c.id} value={c.id}>{c.summary}</option>)}
              </select>
            </div>
            <div className="field">
              <label>{t.fieldRepeat}</label>
              <select className="input" value={draft.repeat} onChange={(e) => update({ repeat: e.target.value as RepeatRule })}>
                {repeatRules.map((r) => <option key={r} value={r}>{t.repeat[r]}</option>)}
              </select>
            </div>
          </>
        ) : isRecurring && <div className="muted small">{t.repeatEditHint}</div>}
        <input className="input" placeholder={t.fieldLocation} value={draft.location} onChange={(e) => update({ location: e.target.value })} />
        <textarea className="input" rows={5} placeholder={t.fieldDescription} value={draft.description} onChange={(e) => update({ description: e.target.value })} />
      </div>
      {askScope && (
        <ScopeDialog title={t.scopeEditTitle} hint={t.scopeEditSeriesHint} onClose={() => setAskScope(false)}
          onInstance={() => { setAskScope(false); void save(false) }} onSeries={() => { setAskScope(false); void save(true) }} />
      )}
    </>
  )
}
