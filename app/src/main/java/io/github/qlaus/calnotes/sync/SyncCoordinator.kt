package io.github.qlaus.calnotes.sync

import io.github.qlaus.calnotes.auth.AuthManager
import io.github.qlaus.calnotes.auth.AuthStatus
import io.github.qlaus.calnotes.auth.NotSignedInException
import io.github.qlaus.calnotes.data.repo.CalendarRepository
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

/**
 * Runs calendar sync. Triggered on app start/foreground, on sign-in, on pull-to-refresh and
 * by a periodic WorkManager job (best-effort on Huawei, which may kill background work).
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val auth: AuthManager,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    init {
        // Sync right after a successful sign-in.
        scope.launch {
            auth.status.filterIsInstance<AuthStatus.SignedIn>().map { it.accountId }.distinctUntilChanged()
                .collect { requestSync() }
        }
    }

    fun requestSync() {
        scope.launch { syncNow() }
    }

    /** Full sync: flush outbox, refresh calendar list and the cache window. Skips if one is running. */
    suspend fun syncNow(): Boolean {
        if (auth.status.value !is AuthStatus.SignedIn) return false
        if (mutex.isLocked) return true
        return mutex.withLock { guarded { syncWindow() } }
    }

    /** Refreshes just [from, to) (e.g. the range the user is looking at). */
    suspend fun refreshRange(from: Long, to: Long): Boolean {
        if (auth.status.value !is AuthStatus.SignedIn) return false
        return guarded {
            calendarRepository.flushOutbox()
            calendarRepository.refreshRange(from, to)
        }
    }

    /** Sends queued event changes right away (called after edits). */
    fun pushChanges() {
        scope.launch {
            if (auth.status.value is AuthStatus.SignedIn) guarded { calendarRepository.flushOutbox() }
        }
    }

    private suspend fun syncWindow() {
        calendarRepository.flushOutbox()
        calendarRepository.refreshCalendars()
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
        }
    }

    private companion object {
        val WINDOW_BACK: Duration = Duration.ofDays(92)
        val WINDOW_AHEAD: Duration = Duration.ofDays(366)
    }
}
