package eu.luftiger.bits.backend.auth.internal

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
        // HS256 needs a key of at least 256 bits.
        require(jwtSecret.toByteArray().size >= 32) { "bits.auth.jwt-secret must be at least 32 bytes long" }
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
            require(!enabled || BCRYPT_HASH.matches(passwordHash)) { "bits.auth.admin.password-hash must be a BCrypt hash" }
        }

        private companion object {
            val BCRYPT_HASH = Regex("""^\$2[aby]?\$\d{2}\$[./A-Za-z0-9]{53}$""")
        }
    }
}
