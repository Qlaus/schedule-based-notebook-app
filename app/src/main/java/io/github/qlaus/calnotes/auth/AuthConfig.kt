package io.github.qlaus.calnotes.auth

import android.net.Uri
import io.github.qlaus.calnotes.BuildConfig
import net.openid.appauth.AuthorizationServiceConfiguration

/** Static Google OAuth configuration. No client secret: native apps are public clients (PKCE). */
object AuthConfig {
    val clientId: String = BuildConfig.GOOGLE_CLIENT_ID

    val isClientConfigured: Boolean get() = !clientId.startsWith("REPLACE_ME")

    /** Redirect variants. Both schemes are registered in the manifest; the user can switch if one is rejected. */
    enum class Redirect(val uri: Uri) {
        PACKAGE(Uri.parse("${BuildConfig.APPLICATION_ID}:/oauth2redirect")),
        REVERSE_CLIENT_ID(
            Uri.parse("com.googleusercontent.apps.${clientId.removeSuffix(".apps.googleusercontent.com")}:/oauth2redirect"),
        ),
    }

    val scopes = listOf(
        "openid",
        "email",
        "https://www.googleapis.com/auth/calendar.events",
        "https://www.googleapis.com/auth/calendar.calendarlist.readonly",
        "https://www.googleapis.com/auth/drive.appdata",
    )

    val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token"),
    )

    const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
}
