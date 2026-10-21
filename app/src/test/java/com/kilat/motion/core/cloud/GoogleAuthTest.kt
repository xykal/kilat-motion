package com.kilat.motion.core.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class GoogleAuthTest {

    @Test
    fun jwtClaims_readsPayload() {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\"}".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"email\":\"a@b.co\",\"aud\":\"x.apps.googleusercontent.com\",\"exp\":123}".toByteArray())
        val claims = GoogleAuth.jwtClaims("$header.$payload.sig")
        assertNotNull(claims)
        assertEquals("a@b.co", claims!!.getString("email"))
        assertEquals(123L, claims.getLong("exp"))
    }

    @Test
    fun jwtClaims_rejectsGarbage() {
        assertNull(GoogleAuth.jwtClaims("bukan-jwt"))
    }

    @Test
    fun formEncode_escapesValues() {
        assertEquals("a=x+y&b=c%26d", Net.formEncode(mapOf("a" to "x y", "b" to "c&d")))
    }
}
