import { dayOf, parseDateKey } from '../repo/time'

const loc = 'tr-TR'
const f = (o: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat(loc, o)
const cap = (s: string) => s.charAt(0).toLocaleUpperCase(loc) + s.slice(1)

const fmtTime = f({ hour: '2-digit', minute: '2-digit', hour12: false })
const fmtMonthYear = f({ month: 'long', year: 'numeric' })
const fmtDayLong = f({ day: 'numeric', month: 'long', year: 'numeric', weekday: 'long' })
const fmtDayMedium = f({ day: 'numeric', month: 'long', weekday: 'long' })
const fmtDayShort = f({ day: 'numeric', month: 'short' })
const fmtWeekdayShort = f({ weekday: 'short' })

export const time = (ms: number) => fmtTime.format(ms)
export const monthYear = (date: string) => cap(fmtMonthYear.format(parseDateKey(date)))
export const dayLong = (date: string) => fmtDayLong.format(parseDateKey(date))
export const dayMedium = (date: string) => cap(fmtDayMedium.format(parseDateKey(date)))
export const dayShort = (ms: number) => fmtDayShort.format(ms)
export const weekdayShort = (date: string) => cap(fmtWeekdayShort.format(parseDateKey(date)))

export function weekRange(start: string): string {
  const s = parseDateKey(start)
  const e = new Date(s)
  e.setDate(e.getDate() + 6)
  if (s.getMonth() === e.getMonth()) return `${s.getDate()} – ${f({ day: 'numeric', month: 'long', year: 'numeric' }).format(e)}`
  return `${fmtDayShort.format(s)} – ${f({ day: 'numeric', month: 'short', year: 'numeric' }).format(e)}`
}

/** Several spellings of a date so searches like "12 kasım", "2026-11" or "12.11.2026" match. */
export function searchDates(ms: number): string {
  const d = new Date(ms)
  const key = dayOf(ms)
  return [
    fmtDayLong.format(d), fmtDayShort.format(d), key,
    `${String(d.getDate()).padStart(2, '0')}.${String(d.getMonth() + 1).padStart(2, '0')}.${d.getFullYear()}`,
    `${d.getDate()}.${d.getMonth() + 1}.${d.getFullYear()}`, f({ month: 'long' }).format(d),
  ].join(' ')
}
