package io.github.qlaus.calnotes.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

/**
 * Persists AppAuth's AuthState JSON encrypted with Tink AEAD (AES-256-GCM). The Tink keyset is
 * itself wrapped by a key in the Android Keystore, so tokens are never stored in plain text.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val aead: Aead by lazy {
        AeadConfig.register()
        @Suppress("DEPRECATION")
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "calnotes_keyset", "calnotes_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://calnotes_master_key")
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    suspend fun readAuthState(): String? = withContext(Dispatchers.IO) {
        val stored = context.authDataStore.data.first()[AUTH_STATE] ?: return@withContext null
        // A Keystore reset (e.g. after a device restore) makes old data unreadable: treat as signed out.
        runCatching {
            String(aead.decrypt(Base64.decode(stored, Base64.NO_WRAP), ASSOCIATED_DATA), Charsets.UTF_8)
        }.getOrNull()
    }

    suspend fun writeAuthState(json: String?) = withContext(Dispatchers.IO) {
        context.authDataStore.edit { prefs ->
            if (json == null) {
                prefs.remove(AUTH_STATE)
            } else {
                val encrypted = aead.encrypt(json.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
                prefs[AUTH_STATE] = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            }
        }
    }

    suspend fun readRedirect(): AuthConfig.Redirect =
        context.authDataStore.data.first()[REDIRECT]
            ?.let { runCatching { AuthConfig.Redirect.valueOf(it) }.getOrNull() }
            ?: AuthConfig.Redirect.PACKAGE

    suspend fun writeRedirect(redirect: AuthConfig.Redirect) {
        context.authDataStore.edit { it[REDIRECT] = redirect.name }
    }

    private companion object {
        val AUTH_STATE = stringPreferencesKey("auth_state_enc")
        val REDIRECT = stringPreferencesKey("redirect_variant")
        val ASSOCIATED_DATA = "calnotes-auth-state".toByteArray()
    }
}
