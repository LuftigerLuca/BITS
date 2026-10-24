package eu.luftiger.bits.backend.auth.internal

import eu.luftiger.bits.backend.shared.Secrets
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("bits.auth")
data class AuthProperties(
    /** Origin the dashboard is reached under, e.g. `https://bits.example.org`; redirects and the Discord redirect URI start with it. */
    val publicUrl: String,
    /** HMAC key the session tokens are signed with. */
    val jwtSecret: String,
    val discord: Discord,
    val admin: Admin,
) {
    init {
        require(publicUrl.isNotBlank()) { "bits.auth.public-url must not be blank" }
        Secrets.requireStrong(jwtSecret, "BITS_JWT_SECRET (bits.auth.jwt-secret)")
    }

    /** OAuth2 client of the Discord application; only members of [guildId] may log in. */
    data class Discord(
        val clientId: String,
        val clientSecret: String,
        val guildId: String,
    ) {
        init {
            require(clientId.isNotBlank()) { "bits.auth.discord.client-id must not be blank" }
            require(clientSecret.isNotBlank()) { "bits.auth.discord.client-secret must not be blank" }
            require(guildId.isNotBlank()) { "bits.auth.discord.guild-id must not be blank" }
        }
    }

    /** Technical admin account that works without Discord. It is disabled unless both values are set. */
    data class Admin(
        val username: String = "",
        val passwordHash: String = "",
    ) {
        val enabled: Boolean
            get() = username.isNotBlank() && passwordHash.isNotBlank()

        init {
            if (enabled) {
                val cost =
                    BCRYPT_HASH
                        .matchEntire(passwordHash)
                        ?.groupValues
                        ?.get(1)
                        ?.toInt()
                requireNotNull(cost) { "BITS_ADMIN_PASSWORD_HASH (bits.auth.admin.password-hash) must be a BCrypt hash" }
                require(cost >= MIN_BCRYPT_COST) {
                    "BITS_ADMIN_PASSWORD_HASH (bits.auth.admin.password-hash) needs a BCrypt cost of at least $MIN_BCRYPT_COST, it has $cost"
                }
            }
        }

        companion object {
            /** Below it, a stolen hash is cracked too quickly. */
            const val MIN_BCRYPT_COST = 10
            private val BCRYPT_HASH = Regex("""^\$2[aby]?\$(\d{2})\$[./A-Za-z0-9]{53}$""")
        }
    }
}
