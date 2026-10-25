package eu.luftiger.bits.backend.auth.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.auth.domain.Role
import kotlinx.serialization.Serializable
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MeController(
    private val accountLinks: AccountLinks,
) {
    /** The user of the current session; the WarEra link is looked up live, so it is current even within a session. */
    @GetMapping("/web/me")
    fun me(
        @AuthenticationPrincipal jwt: Jwt,
    ): MeResponse {
        val user = SessionTokens.user(jwt)
        return MeResponse(
            name = user.name,
            avatarUrl = user.avatarUrl,
            role = WebRole.of(user.role),
            wareraUsername = user.discordUserId?.let(accountLinks::findByDiscordUserId)?.wareraUsername,
            discordUser = user.discordUserId != null,
        )
    }
}

@Serializable
data class MeResponse(
    val name: String,
    val avatarUrl: String?,
    val role: WebRole,
    /** WarEra account the Discord account is linked to; always `null` for the admin account. */
    val wareraUsername: String?,
    /** Whether the session belongs to a Discord account; the admin account from the environment has none. */
    val discordUser: Boolean,
)

/** The API's names for [Role]; the dashboard spells them out in `frontend/src/lib/api.ts`. */
@Serializable
enum class WebRole {
    USER,
    ADMIN,
    ;

    companion object {
        fun of(role: Role) =
            when (role) {
                Role.USER -> USER
                Role.ADMIN -> ADMIN
            }
    }
}
