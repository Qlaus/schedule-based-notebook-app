@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.qlaus.calnotes.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.auth.AuthConfig
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(viewModel: AuthViewModel, needsReauth: Boolean) {
    val ui by viewModel.ui.collectAsState()
    val scope = rememberCoroutineScope()
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onSignInResult(result.data)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.signin_title), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(if (needsReauth) R.string.signin_reauth else R.string.signin_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { scope.launch { launcher.launch(viewModel.signInIntent()) } },
                    enabled = !ui.inProgress && AuthConfig.isClientConfigured,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    if (ui.inProgress) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.signin_in_progress))
                    } else {
                        Text(stringResource(R.string.signin_button), style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (!AuthConfig.isClientConfigured) {
                    Text(stringResource(R.string.signin_client_missing), color = MaterialTheme.colorScheme.error)
                }
                ui.error?.let { Text(stringResource(R.string.signin_failed, it), color = MaterialTheme.colorScheme.error) }
                if (ui.cancelled) {
                    Text(stringResource(R.string.signin_cancelled), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(stringResource(R.string.signin_advanced)) }
                if (showAdvanced) {
                    Text(stringResource(R.string.signin_redirect_label), style = MaterialTheme.typography.titleSmall)
                    val options = listOf(
                        AuthConfig.Redirect.PACKAGE to R.string.signin_redirect_package,
                        AuthConfig.Redirect.REVERSE_CLIENT_ID to R.string.signin_redirect_reverse,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (option, label) ->
                            SegmentedButton(
                                selected = ui.redirect == option,
                                onClick = { viewModel.setRedirect(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                    Row { Text(ui.redirect.uri.toString(), style = MaterialTheme.typography.bodySmall) }
                    Text(
                        stringResource(R.string.signin_redirect_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

