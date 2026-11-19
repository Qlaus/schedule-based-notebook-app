/** Last-writer-wins sync decision (port of core/sync/SyncDecision.kt). Keep both in step. */
export interface LocalState { version: number; updatedAt: number; dirty: boolean; remoteVersion: number | null }
export interface RemoteState { version: number; updatedAt: number }

export type SyncAction = 'NONE' | 'UPLOAD' | 'DOWNLOAD' | 'CONFLICT_KEEP_LOCAL' | 'CONFLICT_TAKE_REMOTE'

export function decide(local: LocalState | null, remote: RemoteState | null): SyncAction {
  if (!local) return remote ? 'DOWNLOAD' : 'NONE'
  if (!remote) return local.dirty || local.remoteVersion == null ? 'UPLOAD' : 'NONE'
  const remoteChanged = remote.version !== local.remoteVersion
  if (!local.dirty && !remoteChanged) return 'NONE'
  if (local.dirty && !remoteChanged) return 'UPLOAD'
  if (!local.dirty && remoteChanged) return 'DOWNLOAD'
  if (local.updatedAt > remote.updatedAt) return 'CONFLICT_KEEP_LOCAL'
  if (local.updatedAt < remote.updatedAt) return 'CONFLICT_TAKE_REMOTE'
  return local.version > remote.version ? 'CONFLICT_KEEP_LOCAL' : 'CONFLICT_TAKE_REMOTE'
}

export function uploadVersion(local: LocalState, remote: RemoteState | null, action: SyncAction): number {
  return action === 'CONFLICT_KEEP_LOCAL' && remote ? Math.max(local.version, remote.version) + 1 : local.version
}
