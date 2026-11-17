package io.github.qlaus.calnotes.core

import io.github.qlaus.calnotes.core.auth.IdTokenClaims
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdTokenClaimsTest {
    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("{}".toByteArray()) + "." + enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    @Test
    fun readsSubjectAndEmail() {
        val claims = IdTokenClaims.parse(jwt("""{"sub":"123","email":"a@b.c","aud":"x"}"""))
        assertEquals(IdTokenClaims("123", "a@b.c"), claims)
    }

    @Test
    fun missingSubjectIsNull() {
        assertNull(IdTokenClaims.parse(jwt("""{"email":"a@b.c"}""")))
    }

    @Test
    fun garbageIsNull() {
        assertNull(IdTokenClaims.parse("not-a-jwt"))
        assertNull(IdTokenClaims.parse(null))
    }
}
