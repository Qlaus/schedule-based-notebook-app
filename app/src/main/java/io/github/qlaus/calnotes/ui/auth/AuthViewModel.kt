package io.github.qlaus.calnotes.ui.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.qlaus.calnotes.auth.AuthConfig
import io.github.qlaus.calnotes.auth.AuthManager
import io.github.qlaus.calnotes.auth.AuthStatus
import io.github.qlaus.calnotes.data.repo.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import javax.inject.Inject

data class SignInUiState(
    val inProgress: Boolean = false,
    val error: String? = null,
    val cancelled: Boolean = false,
    val redirect: AuthConfig.Redirect = AuthConfig.Redirect.PACKAGE,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthManager,
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    val status: StateFlow<AuthStatus> = auth.status

    private val _ui = MutableStateFlow(SignInUiState())
    val ui: StateFlow<SignInUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { _ui.value = _ui.value.copy(redirect = auth.redirect()) }
    }

    suspend fun signInIntent(): Intent {
        _ui.value = _ui.value.copy(inProgress = true, error = null, cancelled = false)
        return auth.buildSignInIntent()
    }

    fun onSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                auth.handleSignInResult(data)
                _ui.value = _ui.value.copy(inProgress = false)
            } catch (e: AuthorizationException) {
                val cancelled = e == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW ||
                    e == AuthorizationException.GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW
                _ui.value = _ui.value.copy(
                    inProgress = false,
                    cancelled = cancelled,
                    // Only error codes/descriptions from Google are shown, never tokens.
                    error = if (cancelled) null else (e.errorDescription ?: e.error ?: "code ${e.code}"),
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(inProgress = false, error = e.javaClass.simpleName)
            }
        }
    }

    fun setRedirect(redirect: AuthConfig.Redirect) {
        viewModelScope.launch {
            auth.setRedirect(redirect)
            _ui.value = _ui.value.copy(redirect = redirect)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            calendarRepository.clearCache()
        }
    }
}
