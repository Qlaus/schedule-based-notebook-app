package io.github.qlaus.calnotes.core.sync

/** Local bookkeeping of one synced item. [remoteVersion] is the version last exchanged with the cloud. */
data class LocalState(val version: Long, val updatedAt: Long, val dirty: Boolean, val remoteVersion: Long?)

/** Cloud copy as seen in the file listing. */
data class RemoteState(val version: Long, val updatedAt: Long)

enum class SyncAction {
    NONE,
    UPLOAD,
    DOWNLOAD,
    /** Both changed; local is newer: back up the remote copy, upload local. */
    CONFLICT_KEEP_LOCAL,
    /** Both changed; remote is newer: back up the local copy, take remote. */
    CONFLICT_TAKE_REMOTE,
}

/**
 * Last-writer-wins with version tracking. A conflict is when both sides changed since the last
 * exchange; the later `updatedAt` wins (ties: higher version, then remote). The loser is kept as a
 * backup by the caller, so nothing is silently lost.
 */
object SyncDecision {
    fun decide(local: LocalState?, remote: RemoteState?): SyncAction {
        if (local == null) return if (remote == null) SyncAction.NONE else SyncAction.DOWNLOAD
        if (remote == null) return if (local.dirty || local.remoteVersion == null) SyncAction.UPLOAD else SyncAction.NONE
        val remoteChanged = remote.version != local.remoteVersion
        return when {
            !local.dirty && !remoteChanged -> SyncAction.NONE
            local.dirty && !remoteChanged -> SyncAction.UPLOAD
            !local.dirty && remoteChanged -> SyncAction.DOWNLOAD
            local.updatedAt > remote.updatedAt -> SyncAction.CONFLICT_KEEP_LOCAL
            local.updatedAt < remote.updatedAt -> SyncAction.CONFLICT_TAKE_REMOTE
            local.version > remote.version -> SyncAction.CONFLICT_KEEP_LOCAL
            else -> SyncAction.CONFLICT_TAKE_REMOTE
        }
    }

    /** Version to write when uploading after [action] so every client sees a change. */
    fun uploadVersion(local: LocalState, remote: RemoteState?, action: SyncAction): Long =
        if (action == SyncAction.CONFLICT_KEEP_LOCAL && remote != null) maxOf(local.version, remote.version) + 1 else local.version
}
