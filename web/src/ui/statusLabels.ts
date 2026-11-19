import { t } from '../i18n/tr'
import type { CalendarStatus, DriveStatus } from '../repo/sync'
import { time } from './format'

export function calendarLabel(s: CalendarStatus, pendingOps: number): string {
  switch (s.kind) {
    case 'running': return t.syncRunning
    case 'offline': return t.syncOffline
    case 'auth': return t.syncAuth
    case 'failed': return t.syncFailed(s.message)
    default: return pendingOps > 0 ? t.syncPending(pendingOps) : t.syncDone
  }
}

export function driveLabel(s: DriveStatus, pendingNotes: number): string {
  switch (s.kind) {
    case 'running': return t.driveRunning
    case 'offline': return t.driveOffline
    case 'auth': return t.driveAuth
    case 'noPermission': return t.driveNoPermission
    case 'failed': return t.driveFailed(s.message)
    case 'synced': return pendingNotes > 0 ? t.drivePending(pendingNotes) : t.driveSynced(time(s.at))
    default: return pendingNotes > 0 ? t.drivePending(pendingNotes) : t.driveIdle
  }
}

export const isError = (c: CalendarStatus, d: DriveStatus) =>
  c.kind === 'failed' || d.kind === 'failed' || d.kind === 'noPermission'
