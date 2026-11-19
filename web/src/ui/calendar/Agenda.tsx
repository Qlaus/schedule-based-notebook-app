import { t } from '../../i18n/tr'
import { today } from '../../repo/time'
import { dayMedium, time } from '../format'
import { AGENDA_DAYS, byDay, type EventUi } from './data'

export function Agenda({ anchor, events, onEvent }: { anchor: string; events: EventUi[]; onEvent: (e: EventUi) => void }) {
  const groups = [...byDay(events, anchor, AGENDA_DAYS).entries()].sort(([a], [b]) => (a < b ? -1 : 1))
  const now = today()
  if (groups.length === 0) return <div className="empty-hint">{t.agendaEmpty}</div>
  return (
    <div className="scroll" style={{ paddingBottom: 96 }}>
      {groups.map(([day, list]) => (
        <section key={day}>
          <div className={`agenda-day ${day === now ? 'today' : ''}`}>{dayMedium(day)}</div>
          {list.map((e) => (
            <div key={day + e.event.calendarId + e.event.id} className="agenda-row" onClick={() => onEvent(e)}>
              <span className="dot" style={{ background: e.color }} />
              <div className="when">
                {e.event.allDay ? t.allDay : (<>{time(e.event.startMillis)}<div className="muted small">{time(e.event.endMillis)}</div></>)}
              </div>
              <div className="grow">
                <div className="ellipsis">{e.event.title || t.noTitle}</div>
                <div className="muted small ellipsis">{[e.event.location, e.calendarName].filter(Boolean).join(' · ')}</div>
              </div>
              {e.hasNotes && <span>📝</span>}
            </div>
          ))}
        </section>
      ))}
    </div>
  )
}
