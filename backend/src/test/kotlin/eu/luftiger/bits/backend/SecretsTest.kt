package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.auth.internal.AuthProperties
import eu.luftiger.bits.backend.shared.BitsProperties
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class SecretsTest {
    private val warera = BitsProperties.Warera(baseUrl = "https://warera.test")

    @Test
    fun `placeholders and short service tokens are rejected, naming the variable`() {
        // The placeholders from an older .env.example, and others like them.
        listOf("change-me", "", "0123456789abcdef", "my-very-long-secret-service-token-value").forEach { token ->
            val error = assertFailsWith<IllegalArgumentException> { BitsProperties(serviceToken = token, warera = warera) }
            assertContains(error.message!!, "BITS_SERVICE_TOKEN")
        }
        BitsProperties(serviceToken = "3f9c2a7e1b5d4c8f9e0a6b2d7c1f4e8a", warera = warera)
    }

    @Test
    fun `placeholder jwt secret is rejected, naming the variable`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                AuthProperties(
                    publicUrl = "https://bits.test",
                    jwtSecret = "change-me-to-a-random-secret-of-32-chars",
                    discord = AuthProperties.Discord("client", "secret", "100"),
                    admin = AuthProperties.Admin(),
                )
            }
        assertContains(error.message!!, "BITS_JWT_SECRET")
    }

    @Test
    fun `admin password hash needs a BCrypt cost of at least 10`() {
        val error = assertFailsWith<IllegalArgumentException> { AuthProperties.Admin("admin", BCryptPasswordEncoder(4).encode("x")!!) }
        assertContains(error.message!!, "at least 10")
        assertFailsWith<IllegalArgumentException> { AuthProperties.Admin("admin", "plain-text") }

        AuthProperties.Admin("admin", BCryptPasswordEncoder(10).encode("x")!!)
    }
}
