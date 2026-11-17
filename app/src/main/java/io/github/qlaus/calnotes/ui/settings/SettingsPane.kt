package io.github.qlaus.calnotes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.qlaus.calnotes.BuildConfig
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.ui.common.ThinDivider

@Composable
fun SettingsPane(accountEmail: String?, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = hiltViewModel()
    val settings by vm.settings.collectAsState()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)

        SectionCard(stringResource(R.string.settings_calendar_section)) {
            ToggleRow(
                title = stringResource(R.string.settings_title_marker),
                hint = stringResource(R.string.settings_title_marker_hint),
                checked = settings.titleMarker,
                onChange = vm::setTitleMarker,
            )
            ThinDivider()
            ToggleRow(
                title = stringResource(R.string.settings_description_marker),
                hint = stringResource(R.string.settings_description_marker_hint),
                checked = settings.descriptionMarker,
                onChange = vm::setDescriptionMarker,
            )
        }

        SectionCard(stringResource(R.string.settings_account_section)) {
            accountEmail?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
        }

        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
