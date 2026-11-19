import { useEffect, useRef, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { t } from '../../i18n/tr'
import { db } from '../../data/db'
import { clearOutboxError, getOutboxError } from '../../repo/calendar'
import { pendingNoteChanges, syncAll, syncRange } from '../../repo/sync'
import { renewSilently } from '../../auth/session'
import { addDays, today, weekStart } from '../../repo/time'
import { dayLong, monthYear, weekRange } from '../format'
import { useSyncState } from '../hooks'
import { nav, useNav, type Mode } from '../store'
import { calendarLabel, driveLabel, isError } from '../statusLabels'
import { Icon } from '../common/Icons'
import { MonthView } from './MonthView'
import { TimeGrid } from './TimeGrid'
import { Agenda } from './Agenda'
import { AGENDA_DAYS, rangeFor, rangeMillis, useEvents, type EventUi } from './data'

const MODES: [Mode, string][] = [['day', t.modeDay], ['week', t.modeWeek], ['month', t.modeMonth], ['agenda', t.modeAgenda]]

export function CalendarPane() {
  const { mode, anchor } = useNav()
  const range = rangeFor(mode, anchor)
  const { from, to } = rangeMillis(range)
  const events = useEvents(from, to)
  const onEvent = (e: EventUi) => nav.show({ kind: 'event', calendarId: e.event.calendarId, eventId: e.event.id })

  // Fetch the visible range when navigating (debounced).
  useEffect(() => {
    const id = setTimeout(() => void syncRange(from, to), 400)
    return () => clearTimeout(id)
  }, [from, to])

  const step = (dir: 1 | -1) => {
    const n = { day: 1, week: 7, agenda: AGENDA_DAYS }[mode as 'day' | 'week' | 'agenda']
    if (mode === 'month') {
      const [y, m] = anchor.split('-').map(Number)
      const d = new Date(y, m - 1 + dir, 1)
      nav.anchor(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`)
    } else nav.anchor(addDays(anchor, dir * n))
  }

  // Horizontal swipe changes the period (phones/tablets).
  const touch = useRef<{ x: number; y: number } | null>(null)
  const swipe = {
    onTouchStart: (e: React.TouchEvent) => { touch.current = { x: e.touches[0].clientX, y: e.touches[0].clientY } },
    onTouchEnd: (e: React.TouchEvent) => {
      const s = touch.current
      touch.current = null
      if (!s) return
      const dx = e.changedTouches[0].clientX - s.x
      const dy = e.changedTouches[0].clientY - s.y
      if (Math.abs(dx) > 70 && Math.abs(dy) < 50) step(dx < 0 ? 1 : -1)
    },
  }

  return (
    <>
      <Header mode={mode} anchor={anchor} onStep={step} />
      <div className="cal-body" {...swipe}>
        {mode === 'month' && <MonthView anchor={anchor} start={range.start} events={events} onDay={nav.openDay} onEvent={onEvent} />}
        {mode === 'week' && (
          <TimeGrid days={Array.from({ length: 7 }, (_, i) => addDays(weekStart(anchor), i))} events={events} onEvent={onEvent}
            onSlot={(d, tm) => nav.show({ kind: 'editEvent', presetDate: d, presetTime: tm })} onDayHead={nav.openDay} />
        )}
        {mode === 'day' && (
          <TimeGrid days={[anchor]} events={events} onEvent={onEvent}
            onSlot={(d, tm) => nav.show({ kind: 'editEvent', presetDate: d, presetTime: tm })} onDayHead={() => {}} />
        )}
        {mode === 'agenda' && <Agenda anchor={anchor} events={events} onEvent={onEvent} />}
      </div>
      <button className="fab" onClick={() => nav.show({ kind: 'editEvent', presetDate: anchor })}>
        <Icon name="plus" /> {t.newEvent}
      </button>
    </>
  )
}

function Header({ mode, anchor, onStep }: { mode: Mode; anchor: string; onStep: (d: 1 | -1) => void }) {
  const sync = useSyncState()
  const pendingOps = useLiveQuery(() => db.pendingOps.count(), []) ?? 0
  const pendingNotes = useLiveQuery(pendingNoteChanges, []) ?? 0
  const calendars = useLiveQuery(() => db.calendars.toArray(), []) ?? []
  const [menu, setMenu] = useState(false)
  const outboxError = getOutboxError()

  const title = mode === 'day' ? dayLong(anchor) : mode === 'week' ? weekRange(weekStart(anchor)) : monthYear(anchor)
  const needsAuth = sync.calendar.kind === 'auth' || sync.drive.kind === 'auth'

  return (
    <div className="cal-head" style={{ position: 'relative' }}>
      <div className="row">
        <div className="grow">
          <div className="cal-title ellipsis">{title}</div>
          <div className={`small ${isError(sync.calendar, sync.drive) ? 'error' : 'muted'}`}>
            {calendarLabel(sync.calendar, pendingOps)} · {driveLabel(sync.drive, pendingNotes)}
          </div>
        </div>
        {needsAuth && <button className="btn tonal" onClick={() => renewSilently() || window.location.reload()}>{t.renewSession}</button>}
        <button className="icon-btn" aria-label={t.refresh} onClick={() => void syncAll()}><Icon name="refresh" /></button>
        <button className="icon-btn" aria-label={t.calendars} onClick={() => setMenu(!menu)}><Icon name="calendar" /></button>
      </div>
      {menu && (
        <div className="menu" onMouseLeave={() => setMenu(false)}>
          {calendars.map((c) => (
            <button key={c.id} onClick={() => void db.calendars.update(c.id, { visible: !c.visible })}>
              <span className="dot" style={{ background: c.colorHex }} />
              <span className="grow ellipsis">{c.summary}</span>
              <input type="checkbox" readOnly checked={c.visible} />
            </button>
          ))}
        </div>
      )}
      {outboxError && <button className="btn text error" onClick={clearOutboxError}>{t.outboxRejected(outboxError)}</button>}
      <div className="cal-controls">
        <div className="segmented">
          {MODES.map(([m, label]) => <button key={m} className={mode === m ? 'active' : ''} onClick={() => nav.mode(m)}>{label}</button>)}
        </div>
        <div className="cal-nav">
          <button className="icon-btn" aria-label={t.previous} onClick={() => onStep(-1)}><Icon name="left" /></button>
          <button className="btn outline" onClick={() => nav.anchor(today())}>{t.today}</button>
          <button className="icon-btn" aria-label={t.next} onClick={() => onStep(1)}><Icon name="right" /></button>
        </div>
      </div>
    </div>
  )
}
