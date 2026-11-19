/** Local-time helpers. Dates are 'YYYY-MM-DD', times 'HH:mm', all in the browser's time zone. */
export const pad = (n: number) => String(n).padStart(2, '0')

export function dateKey(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function parseDateKey(key: string): Date {
  const [y, m, d] = key.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function localMillis(date: string, time = '00:00'): number {
  const [y, m, d] = date.split('-').map(Number)
  const [hh, mm] = time.split(':').map(Number)
  return new Date(y, m - 1, d, hh, mm).getTime()
}

export function addDays(date: string, days: number): string {
  const d = parseDateKey(date)
  d.setDate(d.getDate() + days)
  return dateKey(d)
}

export function timeOf(millis: number): string {
  const d = new Date(millis)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** RFC 3339 with the local UTC offset, e.g. 2026-11-17T09:00:00+03:00. */
export function rfc3339Local(millis: number): string {
  const d = new Date(millis)
  const off = -d.getTimezoneOffset()
  const sign = off >= 0 ? '+' : '-'
  const abs = Math.abs(off)
  return `${dateKey(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
}

export const timeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone

/** Monday-based week start. */
export function weekStart(date: string): string {
  const d = parseDateKey(date)
  const dow = (d.getDay() + 6) % 7
  return addDays(date, -dow)
}

export function monthGridStart(date: string): string {
  const d = parseDateKey(date)
  return weekStart(dateKey(new Date(d.getFullYear(), d.getMonth(), 1)))
}

export const today = () => dateKey(new Date())
export const dayOf = (millis: number) => dateKey(new Date(millis))
