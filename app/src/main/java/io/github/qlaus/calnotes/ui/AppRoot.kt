package io.github.qlaus.calnotes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.qlaus.calnotes.diagnostics.CrashReporter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.qlaus.calnotes.auth.AuthStatus
import io.github.qlaus.calnotes.ui.auth.AuthViewModel
import io.github.qlaus.calnotes.ui.auth.SignInScreen

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashReporter.readLastCrash(context)) }
    crash?.let { report ->
        CrashReportScreen(report) {
            CrashReporter.clearLastCrash(context)
            crash = null
        }
        return
    }
    val authViewModel: AuthViewModel = hiltViewModel()
    val status by authViewModel.status.collectAsState()
    when (val s = status) {
        AuthStatus.Loading -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        AuthStatus.SignedOut -> SignInScreen(authViewModel, needsReauth = false)
        AuthStatus.NeedsReauth -> SignInScreen(authViewModel, needsReauth = true)
        is AuthStatus.SignedIn -> MainScreen(s.email, onSignOut = authViewModel::signOut)
    }
}
