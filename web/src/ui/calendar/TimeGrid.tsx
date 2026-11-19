import { useEffect, useRef } from 'react'
import { t } from '../../i18n/tr'
import { layoutColumns } from '../../core/eventColumns'
import { addDays, localMillis, pad, parseDateKey, today } from '../../repo/time'
import { time, weekdayShort } from '../format'
import { useMinuteTick } from '../hooks'
import { byDay, soft, type EventUi } from './data'

const HOUR = 56

/** Day (1 column) or week (7 columns) time grid with all-day strip and side-by-side overlaps. */
export function TimeGrid({ days, events, onEvent, onSlot, onDayHead }: {
  days: string[]; events: EventUi[]; onEvent: (e: EventUi) => void; onSlot: (date: string, time: string) => void; onDayHead: (d: string) => void
}) {
  const scroller = useRef<HTMLDivElement>(null)
  const nowMs = useMinuteTick()
  const now = today()
  const allDay = events.filter((e) => e.event.allDay)
  const timed = events.filter((e) => !e.event.allDay)
  const allDayByDay = byDay(allDay, days[0], days.length)

  useEffect(() => {
    scroller.current?.scrollTo({ top: HOUR * 7 })
  }, [days[0]])

  return (
    <>
      <div className="grid-head">
        <div className="gutter" />
        {days.map((d) => (
          <div key={d} className="grid-day-head" onClick={() => onDayHead(d)}>
            <span style={{ color: d === now ? 'var(--accent)' : undefined }}>{weekdayShort(d)}</span>
            <div className={`day-num ${d === now ? 'today' : ''}`}>{parseDateKey(d).getDate()}</div>
          </div>
        ))}
      </div>
      {allDay.length > 0 && (
        <div className="allday-row">
          <div className="gutter">{t.allDay}</div>
          {days.map((d) => (
            <div key={d} className="allday-col">
              {(allDayByDay.get(d) ?? []).map((e) => (
                <div key={e.event.calendarId + e.event.id} className="ev-chip" style={{ background: soft(e.color, 0.22) }} onClick={() => onEvent(e)}>
                  {(e.hasNotes ? '📝 ' : '') + (e.event.title || t.noTitle)}
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
      <div className="grid-scroll" ref={scroller}>
        <div className="grid">
          <div className="hours">
            {Array.from({ length: 23 }, (_, i) => <span key={i} style={{ top: (i + 1) * HOUR }}>{pad(i + 1)}:00</span>)}
          </div>
          {days.map((d) => <DayColumn key={d} day={d} events={timed} nowMs={nowMs} isToday={d === now} onEvent={onEvent} onSlot={onSlot} />)}
        </div>
      </div>
    </>
  )
}

function DayColumn({ day, events, nowMs, isToday, onEvent, onSlot }: {
  day: string; events: EventUi[]; nowMs: number; isToday: boolean; onEvent: (e: EventUi) => void; onSlot: (date: string, time: string) => void
}) {
  const start = localMillis(day)
  const end = localMillis(addDays(day, 1))
  const items = events
    .filter((e) => e.event.startMillis < end && e.event.endMillis > start)
    .map((e) => {
      const s = Math.max(e.event.startMillis, start)
      return { item: e, start: s, end: Math.max(Math.min(e.event.endMillis, end), s + 20 * 60_000) }
    })
  const placed = layoutColumns(items)
  const px = (ms: number) => ((ms - start) / 3_600_000) * HOUR

  return (
    <div
      className="day-col"
      onClick={(ev) => {
        const rect = (ev.currentTarget as HTMLDivElement).getBoundingClientRect()
        const hour = Math.min(23, Math.max(0, Math.floor((ev.clientY - rect.top) / HOUR)))
        onSlot(day, `${pad(hour)}:00`)
      }}
    >
      {placed.map((p) => (
        <div
          key={p.item.event.calendarId + p.item.event.id}
          className="tevent"
          style={{
            top: px(p.start) + 1, height: Math.max(px(p.end) - px(p.start) - 2, 18),
            left: `${(p.column / p.columnCount) * 100}%`, width: `${100 / p.columnCount}%`,
          }}
          onClick={(ev) => { ev.stopPropagation(); onEvent(p.item) }}
        >
          <div style={{ display: 'flex', width: '100%', borderRadius: 8, background: soft(p.item.color, 0.16), overflow: 'hidden' }}>
            <div className="bar" style={{ background: p.item.color }} />
            <div className="body">
              <b>{(p.item.hasNotes ? '📝 ' : '') + (p.item.event.title || t.noTitle)}</b>
              <span className="muted">{time(p.item.event.startMillis)}–{time(p.item.event.endMillis)}</span>
            </div>
          </div>
        </div>
      ))}
      {isToday && nowMs >= start && nowMs < end && <div className="now-line" style={{ top: px(nowMs) }} />}
    </div>
  )
}
