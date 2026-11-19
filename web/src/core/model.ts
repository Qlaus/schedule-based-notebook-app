/** Shared domain types. Mirrors core/model in the Android app. */
export type EventLinkScope = 'INSTANCE' | 'SERIES'

export interface EventRef {
  calendarId: string
  /** Event id; for an expanded recurring occurrence this is the instance id. */
  eventId: string
  recurringEventId?: string | null
  originalStartMillis?: number | null
}

export interface LinkKey {
  calendarId: string
  eventId: string
  recurringEventId?: string | null
  scope: EventLinkScope
}

/** True when a link applies to the occurrence: same instance, or a series link for its series. */
export function linkMatches(link: LinkKey, event: EventRef): boolean {
  if (link.calendarId !== event.calendarId) return false
  if (link.scope === 'INSTANCE') return link.eventId === event.eventId
  const series = event.recurringEventId ?? event.eventId
  return (link.recurringEventId ?? link.eventId) === series
}
