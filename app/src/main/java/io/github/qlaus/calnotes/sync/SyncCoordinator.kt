package io.github.qlaus.calnotes.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.qlaus.calnotes.auth.AuthManager
import io.github.qlaus.calnotes.diagnostics.CrashReporter
import kotlinx.coroutines.CancellationException
import io.github.qlaus.calnotes.auth.AuthStatus
import io.github.qlaus.calnotes.auth.NotSignedInException
import io.github.qlaus.calnotes.data.repo.CalendarRepository
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.repo.DriveSync
import io.github.qlaus.calnotes.data.repo.MarkerSync
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import io.github.qlaus.calnotes.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Done(val atMillis: Long) : SyncStatus
    data object Offline : SyncStatus
    data object AuthError : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/** State of the Google Drive notes sync, shown in the header, note editor and settings. */
sealed interface DriveStatus {
    data object Idle : DriveStatus
    data object Running : DriveStatus
    data class Synced(val atMillis: Long) : DriveStatus
    data object Offline : DriveStatus
    /** Drive refused access, e.g. the Drive permission was unticked on Google's consent screen. */
    data object NoPermission : DriveStatus
    data class Failed(val message: String) : DriveStatus
}

/**
 * Runs calendar and notes sync. Triggered on app start/foreground, on sign-in, on pull-to-refresh and
 * by a periodic WorkManager job (best-effort on Huawei, which may kill background work).
 */
@OptIn(FlowPreview::class)
@Singleton
class SyncCoordinator @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val markerSync: MarkerSync,
    private val driveSync: DriveSync,
    db: AppDatabase,
    private val auth: AuthManager,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val driveMutex = Mutex()
    private val _driveStatus = MutableStateFlow<DriveStatus>(DriveStatus.Idle)
    val driveStatus: StateFlow<DriveStatus> = _driveStatus.asStateFlow()

    /** Notes + notebooks with changes not yet in the cloud. */
    val pendingNoteChanges: Flow<Int> =
        combine(db.noteDao().observeDirtyCount(), db.notebookDao().observeDirtyCount()) { a, b -> a + b }

    init {
        // Sync right after a successful sign-in.
        scope.launch {
            auth.status.filterIsInstance<AuthStatus.SignedIn>().map { it.accountId }.distinctUntilChanged()
                .collect { requestSync() }
        }
        // Push note edits a few seconds after typing stops.
        scope.launch {
            pendingNoteChanges.debounce(NOTE_PUSH_DELAY_MS).filter { it > 0 }.collect { syncNotes() }
        }
    }

    /** Two-way notes sync with Google Drive. Safe to call often; concurrent calls are skipped. */
    suspend fun syncNotes(): Boolean {
        if (auth.status.value !is AuthStatus.SignedIn) return false
        if (driveMutex.isLocked) return true
        return driveMutex.withLock {
            _driveStatus.value = DriveStatus.Running
            try {
                driveSync.sync()
                _driveStatus.value = DriveStatus.Synced(clock.millis())
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotSignedInException) {
                _driveStatus.value = DriveStatus.Failed("auth")
                false
            } catch (e: HttpException) {
                _driveStatus.value = if (e.code() == 403 || e.code() == 401) DriveStatus.NoPermission else DriveStatus.Failed("HTTP ${e.code()}")
                false
            } catch (e: IOException) {
                _driveStatus.value = DriveStatus.Offline
                false
            } catch (e: Exception) {
                CrashReporter.recordNonFatal(context, e)
                _driveStatus.value = DriveStatus.Failed(e.javaClass.simpleName)
                false
            }
        }
    }

    fun requestNotesSync() {
        scope.launch { syncNotes() }
    }

    fun requestSync() {
        scope.launch { syncNow() }
    }

    /** Full sync: flush outbox, refresh calendar list and the cache window. Skips if one is running. */
    suspend fun syncNow(): Boolean {
        if (auth.status.value !is AuthStatus.SignedIn) return false
        if (mutex.isLocked) return true
        val calendarOk = mutex.withLock { guarded { syncWindow() } }
        val notesOk = syncNotes()
        // Notes downloaded from another device may need calendar markers.
        if (notesOk) guarded { markerSync.reconcile() }
        return calendarOk && notesOk
    }

    /** Refreshes just [from, to) (e.g. the range the user is looking at). */
    suspend fun refreshRange(from: Long, to: Long): Boolean {
        if (auth.status.value !is AuthStatus.SignedIn) return false
        return guarded {
            calendarRepository.flushOutbox()
            calendarRepository.refreshRange(from, to)
        }
    }

    /** Sends queued event changes and note markers right away (called after edits). */
    fun pushChanges() {
        scope.launch {
            if (auth.status.value is AuthStatus.SignedIn) {
                guarded {
                    if (calendarRepository.flushOutbox()) markerSync.reconcile()
                }
            }
        }
    }

    private suspend fun syncWindow() {
        calendarRepository.flushOutbox()
        calendarRepository.refreshCalendars()
        markerSync.reconcile()
        val now = clock.instant()
        calendarRepository.refreshRange(
            now.minus(WINDOW_BACK).toEpochMilli(),
            now.plus(WINDOW_AHEAD).toEpochMilli(),
        )
    }

    private suspend fun guarded(block: suspend () -> Unit): Boolean {
        _status.value = SyncStatus.Running
        return try {
            block()
            _status.value = SyncStatus.Done(clock.millis())
            true
        } catch (e: NotSignedInException) {
            _status.value = SyncStatus.AuthError
            false
        } catch (e: HttpException) {
            _status.value = SyncStatus.Failed("HTTP ${e.code()}")
            false
        } catch (e: IOException) {
            _status.value = SyncStatus.Offline
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Unexpected (e.g. parsing) errors must not crash the app; keep the trace for diagnosis.
            CrashReporter.recordNonFatal(context, e)
            _status.value = SyncStatus.Failed(e.javaClass.simpleName + (e.message?.let { ": " + it.take(80) } ?: ""))
            false
        }
    }

    private companion object {
        const val NOTE_PUSH_DELAY_MS = 4_000L
        val WINDOW_BACK: Duration = Duration.ofDays(92)
        val WINDOW_AHEAD: Duration = Duration.ofDays(366)
    }
}
