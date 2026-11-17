package io.github.qlaus.calnotes.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/** The few OpenID Connect ID-token claims the app needs. The token itself is never logged. */
data class IdTokenClaims(val subject: String, val email: String?) {
    companion object {
        /**
         * Reads the claims from a JWT payload without verifying the signature. This is safe here
         * because the token comes straight from Google's token endpoint over TLS.
         */
        fun parse(idToken: String?): IdTokenClaims? {
            val payload = idToken?.split('.')?.getOrNull(1) ?: return null
            return runCatching {
                val bytes = Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '='))
                val obj = Json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as JsonObject
                val sub = obj["sub"]?.jsonPrimitive?.contentOrNull ?: return null
                IdTokenClaims(sub, obj["email"]?.jsonPrimitive?.contentOrNull)
            }.getOrNull()
        }
    }
}
