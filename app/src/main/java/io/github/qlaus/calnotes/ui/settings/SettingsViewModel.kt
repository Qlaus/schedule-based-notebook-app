package io.github.qlaus.calnotes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.qlaus.calnotes.data.settings.AppSettings
import io.github.qlaus.calnotes.data.settings.SettingsRepository
import io.github.qlaus.calnotes.sync.DriveStatus
import io.github.qlaus.calnotes.sync.SyncCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    val driveStatus: StateFlow<DriveStatus> = syncCoordinator.driveStatus
    val pendingNoteChanges: StateFlow<Int> =
        syncCoordinator.pendingNoteChanges.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun syncNotesNow() = syncCoordinator.requestNotesSync()

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setTitleMarker(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTitleMarker(enabled)
            syncCoordinator.pushChanges() // add/remove markers on Google right away
        }
    }

    fun setDescriptionMarker(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDescriptionMarker(enabled)
            syncCoordinator.pushChanges()
        }
    }
}
