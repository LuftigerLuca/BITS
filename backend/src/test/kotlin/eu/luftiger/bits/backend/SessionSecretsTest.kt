package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.auth.internal.AuthProperties
import eu.luftiger.bits.backend.auth.internal.SessionSecrets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SessionSecretsTest {
    private val secrets = SessionSecrets(properties("first-random-value-of-at-least-32-chars"))
    private val rotated = SessionSecrets(properties("second-random-value-of-at-least-32-chars"))

    @Test
    fun `the refresh token is encrypted with a fresh nonce each time`() {
        val first = secrets.encrypt("refresh-token")
        val second = secrets.encrypt("refresh-token")

        assertNotEquals(first, second)
        assertEquals("refresh-token", secrets.decrypt(first))
    }

    @Test
    fun `a new secret cannot read what the old one encrypted`() {
        assertNull(rotated.decrypt(secrets.encrypt("refresh-token")))
        assertNull(secrets.decrypt("not encrypted"))
    }

    @Test
    fun `the fingerprint depends on the secret`() {
        assertEquals(secrets.fingerprint("hash"), secrets.fingerprint("hash"))
        assertNotEquals(secrets.fingerprint("hash"), secrets.fingerprint("other hash"))
        assertNotEquals(secrets.fingerprint("hash"), rotated.fingerprint("hash"))
    }

    private fun properties(jwtSecret: String) =
        AuthProperties(
            publicUrl = "https://bits.test",
            jwtSecret = jwtSecret,
            discord = AuthProperties.Discord("client", "secret", "100"),
            admin = AuthProperties.Admin(),
        )
}
