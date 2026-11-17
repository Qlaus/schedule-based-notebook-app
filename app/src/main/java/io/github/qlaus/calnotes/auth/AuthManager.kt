package io.github.qlaus.calnotes.auth

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.qlaus.calnotes.core.auth.IdTokenClaims
import io.github.qlaus.calnotes.di.ApplicationScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ResponseTypeValues
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AuthStatus {
    data object Loading : AuthStatus
    data object SignedOut : AuthStatus
    /** The refresh token was revoked or expired; local data is kept, the user must sign in again. */
    data object NeedsReauth : AuthStatus
    data class SignedIn(val accountId: String, val email: String?) : AuthStatus
}

/** Thrown (as IOException, so OkHttp can propagate it) when no valid session exists. */
class NotSignedInException(message: String = "Not signed in") : IOException(message)

/** Identity used to tag rows with an account (single account for now). */
interface CurrentAccount {
    val accountId: String
}

/**
 * Google sign-in via AppAuth (Custom Tab / browser + PKCE) and fresh-token access for API calls.
 * Tokens are never logged.
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
    @ApplicationScope scope: CoroutineScope,
) : CurrentAccount {

    private val service by lazy { AuthorizationService(context) }
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private var authState = AuthState()

    private val _status = MutableStateFlow<AuthStatus>(AuthStatus.Loading)
    val status: StateFlow<AuthStatus> = _status.asStateFlow()

    override val accountId: String
        get() = (status.value as? AuthStatus.SignedIn)?.accountId ?: LOCAL_ACCOUNT

    init {
        scope.launch {
            val json = tokenStore.readAuthState()
            authState = json?.let { runCatching { AuthState.jsonDeserialize(it) }.getOrNull() } ?: AuthState()
            _status.value = statusFromState()
            loaded.complete(Unit)
        }
    }

    suspend fun redirect(): AuthConfig.Redirect = tokenStore.readRedirect()

    suspend fun setRedirect(redirect: AuthConfig.Redirect) = tokenStore.writeRedirect(redirect)

    /** Intent that opens Google's consent page; launch it with an ActivityResult launcher. */
    suspend fun buildSignInIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            AuthConfig.serviceConfig,
            AuthConfig.clientId,
            ResponseTypeValues.CODE,
            tokenStore.readRedirect().uri,
        )
            .setScopes(AuthConfig.scopes)
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    /** Completes sign-in from the result of [buildSignInIntent]. Throws on failure or cancel. */
    suspend fun handleSignInResult(data: Intent?) {
        val response = data?.let { AuthorizationResponse.fromIntent(it) }
        val error = data?.let { AuthorizationException.fromIntent(it) }
        if (response == null) throw error ?: AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW

        mutex.withLock {
            loaded.await()
            val state = AuthState(response, error)
            val tokenResponse = suspendCancellableCoroutine { cont ->
                service.performTokenRequest(response.createTokenExchangeRequest()) { tr, ex ->
                    if (tr != null) cont.resume(tr) else cont.resumeWithException(ex ?: IllegalStateException("token request failed"))
                }
            }
            state.update(tokenResponse, null)
            authState = state
            tokenStore.writeAuthState(state.jsonSerializeString())
            _status.value = statusFromState()
        }
    }

    /**
     * Returns a valid access token, refreshing it when needed (or when [forceRefresh]).
     * Refreshes are serialized so parallel requests don't race on the refresh token.
     */
    suspend fun freshAccessToken(forceRefresh: Boolean = false): String = mutex.withLock {
        loaded.await()
        if (authState.refreshToken == null && !authState.isAuthorized) throw NotSignedInException()
        if (forceRefresh) authState.needsTokenRefresh = true
        val before = authState.accessToken
        val token = try {
            suspendCancellableCoroutine<String> { cont ->
                authState.performActionWithFreshTokens(service) { accessToken, _, ex ->
                    when {
                        accessToken != null -> cont.resume(accessToken)
                        ex != null -> cont.resumeWithException(ex)
                        else -> cont.resumeWithException(IOException("no access token"))
                    }
                }
            }
        } catch (e: AuthorizationException) {
            if (e == AuthorizationException.TokenRequestErrors.INVALID_GRANT) {
                authState = AuthState()
                tokenStore.writeAuthState(null)
                _status.value = AuthStatus.NeedsReauth
                throw NotSignedInException("Session expired")
            }
            throw IOException("Token refresh failed (${e.type}/${e.code})")
        }
        if (token != before) tokenStore.writeAuthState(authState.jsonSerializeString())
        token
    }

    suspend fun signOut() {
        val refresh = mutex.withLock {
            val r = authState.refreshToken
            authState = AuthState()
            tokenStore.writeAuthState(null)
            _status.value = AuthStatus.SignedOut
            r
        }
        if (refresh != null) withContext(Dispatchers.IO) { runCatching { revoke(refresh) } }
    }

    private fun revoke(token: String) {
        val conn = URL(AuthConfig.REVOKE_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write("token=${URLEncoder.encode(token, "UTF-8")}".toByteArray()) }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    private fun statusFromState(): AuthStatus {
        if (authState.refreshToken == null) return AuthStatus.SignedOut
        val claims = IdTokenClaims.parse(authState.idToken)
        return AuthStatus.SignedIn(claims?.subject ?: LOCAL_ACCOUNT, claims?.email)
    }

    companion object {
        const val LOCAL_ACCOUNT = "local"
    }
}
