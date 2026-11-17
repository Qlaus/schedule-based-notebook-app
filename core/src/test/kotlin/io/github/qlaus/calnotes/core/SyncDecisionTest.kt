package io.github.qlaus.calnotes.core

import io.github.qlaus.calnotes.core.sync.LocalState
import io.github.qlaus.calnotes.core.sync.RemoteState
import io.github.qlaus.calnotes.core.sync.SyncAction
import io.github.qlaus.calnotes.core.sync.SyncDecision
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncDecisionTest {
    private fun local(v: Long, at: Long, dirty: Boolean, rv: Long?) = LocalState(v, at, dirty, rv)
    private fun remote(v: Long, at: Long) = RemoteState(v, at)

    @Test fun newLocalIsUploaded() = assertEquals(SyncAction.UPLOAD, SyncDecision.decide(local(1, 1, true, null), null))
    @Test fun newRemoteIsDownloaded() = assertEquals(SyncAction.DOWNLOAD, SyncDecision.decide(null, remote(3, 1)))
    @Test fun unchangedDoesNothing() = assertEquals(SyncAction.NONE, SyncDecision.decide(local(3, 1, false, 3), remote(3, 1)))
    @Test fun localEditIsUploaded() = assertEquals(SyncAction.UPLOAD, SyncDecision.decide(local(4, 2, true, 3), remote(3, 1)))
    @Test fun remoteEditIsDownloaded() = assertEquals(SyncAction.DOWNLOAD, SyncDecision.decide(local(3, 1, false, 3), remote(5, 9)))

    @Test fun conflictLaterLocalWins() =
        assertEquals(SyncAction.CONFLICT_KEEP_LOCAL, SyncDecision.decide(local(4, 20, true, 3), remote(4, 10)))

    @Test fun conflictLaterRemoteWins() =
        assertEquals(SyncAction.CONFLICT_TAKE_REMOTE, SyncDecision.decide(local(4, 10, true, 3), remote(4, 20)))

    @Test fun conflictTieUsesVersionThenRemote() {
        assertEquals(SyncAction.CONFLICT_KEEP_LOCAL, SyncDecision.decide(local(6, 10, true, 3), remote(5, 10)))
        assertEquals(SyncAction.CONFLICT_TAKE_REMOTE, SyncDecision.decide(local(5, 10, true, 3), remote(5, 10)))
    }

    @Test fun syncedLocalMissingRemotelyIsNotReuploaded() =
        assertEquals(SyncAction.NONE, SyncDecision.decide(local(3, 1, false, 3), null))

    @Test fun keptLocalGetsVersionAboveBoth() {
        val l = local(4, 20, true, 3)
        val r = remote(7, 10)
        assertEquals(8, SyncDecision.uploadVersion(l, r, SyncAction.CONFLICT_KEEP_LOCAL))
        assertEquals(4, SyncDecision.uploadVersion(l, r, SyncAction.UPLOAD))
    }
}
