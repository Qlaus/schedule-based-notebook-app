package io.github.qlaus.calnotes.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    /** Add "📝 " to the Google event title when it has notes (default on). */
    val titleMarker: Boolean = true,
    /** Append a marker block to the Google event description (default off). */
    val descriptionMarker: Boolean = false,
)

interface SettingsSource {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings = settings.first()
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) : SettingsSource {

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            titleMarker = prefs[TITLE_MARKER] ?: true,
            descriptionMarker = prefs[DESCRIPTION_MARKER] ?: false,
        )
    }

    suspend fun setTitleMarker(enabled: Boolean) {
        context.settingsDataStore.edit { it[TITLE_MARKER] = enabled }
    }

    suspend fun setDescriptionMarker(enabled: Boolean) {
        context.settingsDataStore.edit { it[DESCRIPTION_MARKER] = enabled }
    }

    private companion object {
        val TITLE_MARKER = booleanPreferencesKey("title_marker")
        val DESCRIPTION_MARKER = booleanPreferencesKey("description_marker")
    }
}
