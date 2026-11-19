import { t } from '../../i18n/tr'
import { addDays, parseDateKey, today } from '../../repo/time'
import { weekdayShort } from '../format'
import { byDay, soft, type EventUi } from './data'

export function MonthView({ anchor, start, events, onDay, onEvent }: {
  anchor: string; start: string; events: EventUi[]; onDay: (d: string) => void; onEvent: (e: EventUi) => void
}) {
  const groups = byDay(events, start, 42)
  const month = parseDateKey(anchor).getMonth()
  const now = today()
  return (
    <>
      <div className="weekdays">{Array.from({ length: 7 }, (_, i) => <div key={i}>{weekdayShort(addDays(start, i))}</div>)}</div>
      <div className="month">
        {Array.from({ length: 6 }, (_, w) => (
          <div className="month-row" key={w}>
            {Array.from({ length: 7 }, (_, d) => {
              const date = addDays(start, w * 7 + d)
              const list = groups.get(date) ?? []
              const shown = list.slice(0, 3)
              return (
                <div key={date} className={`day-cell ${parseDateKey(date).getMonth() !== month ? 'out' : ''}`} onClick={() => onDay(date)}>
                  <div className={`day-num ${date === now ? 'today' : ''}`}>{parseDateKey(date).getDate()}</div>
                  {shown.map((e) => (
                    <div key={e.event.calendarId + e.event.id} className="ev-chip" style={{ background: soft(e.color) }}
                      onClick={(ev) => { ev.stopPropagation(); onEvent(e) }}>
                      {(e.hasNotes ? '📝 ' : '') + (e.event.title || t.noTitle)}
                    </div>
                  ))}
                  {list.length > shown.length && <div className="muted" style={{ fontSize: 11 }}>{t.moreEvents(list.length - shown.length)}</div>}
                </div>
              )
            })}
          </div>
        ))}
      </div>
    </>
  )
}
