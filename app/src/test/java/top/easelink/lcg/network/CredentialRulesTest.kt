package top.easelink.lcg.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialRulesTest {

    @Test
    fun baseName_stripsCookieprePrefix() {
        assertEquals("auth", CredentialRules.baseName("htVC_2132_auth"))
        assertEquals("saltkey", CredentialRules.baseName("htVC_2132_saltkey"))
    }

    @Test
    fun baseName_noUnderscoreReturnsLowercase() {
        assertEquals("saltkey", CredentialRules.baseName("SALTKEY"))
        assertEquals("auth", CredentialRules.baseName("auth"))
    }

    @Test
    fun isNonCredential_matchesVisitorAndTrackerCookies() {
        assertTrue(CredentialRules.isNonCredential("wzws_sid"))
        assertTrue(CredentialRules.isNonCredential("hmaccount"))
        assertTrue(CredentialRules.isNonCredential("Hm_lvt_12345"))
        assertTrue(CredentialRules.isNonCredential("hm_lpvt_67890"))
        assertTrue(CredentialRules.isNonCredential("htVC_2132_lastvisit"))
        assertTrue(CredentialRules.isNonCredential("htVC_2132_lastact"))
        assertTrue(CredentialRules.isNonCredential("htVC_2132_viewid"))
        assertTrue(CredentialRules.isNonCredential("htVC_2132_lastip"))
    }

    @Test
    fun isNonCredential_keepsBusinessCredentials() {
        assertFalse(CredentialRules.isNonCredential("htVC_2132_auth"))
        assertFalse(CredentialRules.isNonCredential("htVC_2132_saltkey"))
        assertFalse(CredentialRules.isNonCredential("auth"))
        assertFalse(CredentialRules.isNonCredential("saltkey"))
    }

    @Test
    fun hasLoginPair_requiresBothNonBlank() {
        assertTrue(CredentialRules.hasLoginPair("abc", "def"))
        assertFalse(CredentialRules.hasLoginPair(null, "def"))
        assertFalse(CredentialRules.hasLoginPair("abc", null))
        assertFalse(CredentialRules.hasLoginPair("", "def"))
        assertFalse(CredentialRules.hasLoginPair("abc", "   "))
        assertFalse(CredentialRules.hasLoginPair(null, null))
    }
}
