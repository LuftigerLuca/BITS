package eu.luftiger.bits.backend.externalapi.internal

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The public documentation of the third-party API at `/external/docs`, see `springdoc` in `application.yml`. What each
 * endpoint does and which scope it needs comes from its [ExternalScope], so the documentation cannot drift from the
 * check.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {
    @Bean
    fun externalOpenApi(properties: ExternalApiProperties): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("BITS API")
                    .version("v1")
                    .description(description(properties.requestsPerMinute)),
            )
            // Relative, so it works behind any proxy.
            .servers(listOf(Server().url("/")))
            .components(
                Components()
                    .addSecuritySchemes(
                        SECURITY_SCHEME,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .description("Ein API-Token aus dem Dashboard, z. B. `Authorization: Bearer bits_…`"),
                    ).addSchemas(PROBLEM, problemSchema()),
            )

    @Bean
    fun externalOperationCustomizer(properties: ExternalApiProperties): OperationCustomizer =
        OperationCustomizer { operation, handler ->
            val scope = handler.getMethodAnnotation(ExternalEndpoint::class.java)?.scope ?: return@OperationCustomizer operation
            operation
                .summary(scope.summary)
                .description("Benötigter Scope: `${scope.id}`")
                .addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME, scope.id))
            operation.responses.apply {
                if (handler.methodParameters.isNotEmpty()) addApiResponse("400", problem("Ungültige Parameter"))
                addApiResponse("401", problem("Token fehlt, ist ungültig, widerrufen oder abgelaufen"))
                addApiResponse("403", problem("Dem Token fehlt der Scope `${scope.id}`"))
                if ('{' in scope.path) addApiResponse("404", problem("Nicht gefunden"))
                addApiResponse(
                    "429",
                    problem("Mehr als ${properties.requestsPerMinute} Anfragen in dieser Minute")
                        .addHeaderObject("Retry-After", Header().description("Sekunden bis zur nächsten Minute").schema(IntegerSchema())),
                )
            }
            operation
        }

    private fun problem(description: String) =
        ApiResponse()
            .description(description)
            .content(Content().addMediaType("application/problem+json", MediaType().schema(Schema<Any>().`$ref`(PROBLEM))))

    private fun problemSchema(): Schema<*> =
        ObjectSchema()
            .description("Fehler nach RFC 9457")
            .addProperty("type", StringSchema())
            .addProperty("title", StringSchema())
            .addProperty("status", IntegerSchema())
            .addProperty("detail", StringSchema())

    private companion object {
        const val SECURITY_SCHEME = "apiToken"
        const val PROBLEM = "Problem"

        fun description(requestsPerMinute: Int) =
            """
            Lesender Zugriff auf Daten von BITS, dem Bot des deutschen WarEra-Discords.

            **Authentifizierung:** Jede Anfrage braucht ein API-Token im Header `Authorization: Bearer <token>`. Tokens
            legen Admins im Dashboard unter „API-Tokens“ an. Jedes Token hat ein Ablaufdatum und darf genau die
            Endpunkte aufrufen, deren Scope es hat.

            **Limits:** Höchstens $requestsPerMinute Anfragen pro Minute und Token; darüber antwortet die API mit 429 und `Retry-After`.
            Listen kommen seitenweise mit `page` (ab 0) und `size` (bis 100).

            **Formate:** Zeiten in ISO 8601 (UTC), Tage als `YYYY-MM-DD`. Fehler kommen als `application/problem+json`.
            """.trimIndent()
    }
}
