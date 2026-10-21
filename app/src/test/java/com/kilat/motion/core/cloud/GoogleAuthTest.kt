package com.kilat.motion.core.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class GoogleAuthTest {

    @Test
    fun pkce_matchesRfc7636Vector() {
        // Vektor uji dari RFC 7636 Appendix B
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", GoogleAuth.codeChallenge(verifier))
    }

    @Test
    fun randomUrlSafe_hasNoPaddingOrUnsafeChars() {
        val v = GoogleAuth.randomUrlSafe(48)
        assert(v.none { it == '=' || it == '+' || it == '/' })
        assert(v.length >= 60)
    }

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
        val s = Net.formEncode(mapOf("a" to "x y", "b" to "c&d"))
        assertEquals("a=x+y&b=c%26d", s)
    }
}
