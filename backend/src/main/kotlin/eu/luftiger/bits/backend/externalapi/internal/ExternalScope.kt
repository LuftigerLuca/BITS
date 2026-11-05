package eu.luftiger.bits.backend.externalapi.internal

/**
 * One scope per endpoint of the third-party API. A token may call exactly the endpoints of its scopes; an endpoint
 * names its scope with [ExternalEndpoint], an endpoint without one cannot be called at all.
 *
 * [id] is stored with the tokens and is part of the API, so it never changes. [summary] is German, for the dashboard
 * and the documentation.
 */
enum class ExternalScope(
    val id: String,
    val path: String,
    val summary: String,
) {
    ACCOUNTS_LIST("accounts:list", "/external/v1/accounts", "Alle verknüpften Accounts seitenweise abrufen"),
    ACCOUNTS_BY_DISCORD_ID(
        "accounts:by-discord-id",
        "/external/v1/accounts/discord/{discordUserId}",
        "Verknüpften WarEra-Account zu einer Discord-ID abrufen",
    ),
    ACCOUNTS_BY_WARERA_ID(
        "accounts:by-warera-id",
        "/external/v1/accounts/warera/{wareraUserId}",
        "Verknüpften Discord-Account zu einer WarEra-ID abrufen",
    ),
    OVERVIEW_SNAPSHOTS(
        "overview:snapshots",
        "/external/v1/overview/snapshots",
        "Kennzahlen der Übersicht pro Tag abrufen",
    ),
    DSKS_LIST("dsks:list", "/external/v1/dsks", "DSKs mit ihren Militäreinheiten abrufen"),
    VOTES_LIST("votes:list", "/external/v1/votes", "Abstimmungen seitenweise abrufen, Ergebnisse nur von ausgewerteten"),
    VOTES_GET("votes:get", "/external/v1/votes/{id}", "Eine Abstimmung abrufen, ihr Ergebnis nur, wenn sie ausgewertet ist"),
    ;

    companion object {
        private val BY_ID = entries.associateBy { it.id }

        fun byId(id: String): ExternalScope? = BY_ID[id]
    }
}

/**
 * Marks an endpoint of the third-party API with the scope a token needs for it. [ExternalApiInterceptor] denies every
 * endpoint under `/external/v1/` without it, and the documentation shows the scope with the endpoint.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExternalEndpoint(
    val scope: ExternalScope,
)
