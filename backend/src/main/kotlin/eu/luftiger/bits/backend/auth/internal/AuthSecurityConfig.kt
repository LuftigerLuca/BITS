package eu.luftiger.bits.backend.auth.internal

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.converter.FormHttpMessageConverter
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.savedrequest.NullRequestCache
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Security of the dashboard, kept apart from the bot's API under `/api/`:
 *
 * - `/auth/` is open and handles the logins. The Discord login is the OAuth2 authorization code flow; the HTTP
 *   session holds its state only until Discord redirects back.
 * - `/web/` requires the session cookie issued by [SessionTokens], and `/web/admin/` the role ADMIN.
 *
 * CSRF tokens are not needed: the session cookie is `SameSite=Strict`, so the browser never sends it with requests
 * started by another site. Other origins of the same site, such as a neighbouring subdomain, are kept out by
 * [SameOriginFilter] for every request that changes state. The admin controllers also check the role themselves
 * with `@PreAuthorize`, in case a path is mapped wrongly.
 *
 * The HTTP session of the Discord login has a `Secure` cookie with `SameSite=Lax` (Discord's redirect back is a
 * cross-site navigation) and lives for minutes only, see `server.servlet.session` in `application.yml`. PKCE is used
 * on top of the state, although Spring Security turns it on only for public clients by default.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class AuthSecurityConfig {
    @Bean
    @Order(2)
    fun authSecurityFilterChain(
        http: HttpSecurity,
        clientRegistrations: ClientRegistrationRepository,
        tokenResponseClient: OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>,
        discordLoginService: DiscordLoginService,
        successHandler: DiscordLoginSuccessHandler,
        failureHandler: DiscordLoginFailureHandler,
        properties: AuthProperties,
    ): SecurityFilterChain {
        http {
            securityMatcher("/auth/**")
            csrf { disable() }
            addFilterBefore<CsrfFilter>(SameOriginFilter(properties.publicUrl))
            requestCache { requestCache = NullRequestCache() }
            exceptionHandling { authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED) }
            authorizeHttpRequests { authorize(anyRequest, permitAll) }
            oauth2Login {
                clientRegistrationRepository = clientRegistrations
                // Discord's access token is not kept after the login.
                authorizedClientRepository = DiscardingAuthorizedClientRepository
                loginPage = "/login"
                authorizationEndpoint {
                    authorizationRequestResolver =
                        DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, "/auth/oauth2/authorization").apply {
                            setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())
                        }
                }
                redirectionEndpoint { baseUri = "/auth/oauth2/callback/*" }
                tokenEndpoint { accessTokenResponseClient = tokenResponseClient }
                userInfoEndpoint { userService = discordLoginService }
                authenticationSuccessHandler = successHandler
                authenticationFailureHandler = failureHandler
            }
        }
        return http.build()
    }

    @Bean
    @Order(3)
    fun webSecurityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        properties: AuthProperties,
    ): SecurityFilterChain {
        http {
            securityMatcher("/web/**")
            csrf { disable() }
            addFilterBefore<CsrfFilter>(SameOriginFilter(properties.publicUrl))
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            requestCache { requestCache = NullRequestCache() }
            authorizeHttpRequests {
                authorize("/web/admin/**", hasRole("ADMIN"))
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                bearerTokenResolver = SessionCookieTokenResolver
                jwt {
                    this.jwtDecoder = jwtDecoder
                    jwtAuthenticationConverter = sessionAuthenticationConverter()
                }
            }
        }
        return http.build()
    }

    @Bean
    fun clientRegistrationRepository(properties: AuthProperties): ClientRegistrationRepository =
        InMemoryClientRegistrationRepository(
            ClientRegistration
                .withRegistrationId("discord")
                .clientName("Discord")
                .clientId(properties.discord.clientId)
                .clientSecret(properties.discord.clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("${properties.publicUrl}/auth/oauth2/callback/{registrationId}")
                .scope("identify", "guilds")
                .authorizationUri("https://discord.com/oauth2/authorize")
                .tokenUri("$DISCORD_API_URL/oauth2/token")
                .userInfoUri("$DISCORD_API_URL/users/@me")
                .userNameAttributeName("id")
                .build(),
        )

    /** Built like Spring Security's own client, but from Boot's builder, so that the HTTP timeouts apply. */
    @Bean
    fun discordTokenResponseClient(builder: RestClient.Builder): OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> =
        RestClientAuthorizationCodeTokenResponseClient().apply {
            setRestClient(
                builder
                    .clone()
                    .messageConverters {
                        it.add(0, FormHttpMessageConverter())
                        it.add(0, OAuth2AccessTokenResponseHttpMessageConverter())
                    }.defaultStatusHandler(OAuth2ErrorResponseErrorHandler())
                    .build(),
            )
        }

    @Bean
    fun discordApi(builder: RestClient.Builder): DiscordApi = RestDiscordApi(builder.baseUrl(DISCORD_API_URL).build())

    @Bean
    fun jwtEncoder(properties: AuthProperties): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(signingKey(properties)))

    /**
     * Checks the signature, the expiry against the injected [Clock] (backend and token share a clock, so no skew) and
     * the denylist of logged-out sessions.
     */
    @Bean
    fun jwtDecoder(
        properties: AuthProperties,
        clock: Clock,
        revokedSessions: RevokedSessions,
    ): JwtDecoder =
        NimbusJwtDecoder
            .withSecretKey(signingKey(properties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .apply {
                setJwtValidator(
                    DelegatingOAuth2TokenValidator(JwtTimestampValidator(Duration.ZERO).apply { setClock(clock) }, revokedSessions),
                )
            }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    private fun signingKey(properties: AuthProperties): SecretKey = SecretKeySpec(properties.jwtSecret.toByteArray(), "HmacSHA256")

    private fun sessionAuthenticationConverter() =
        JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(
                JwtGrantedAuthoritiesConverter().apply {
                    setAuthoritiesClaimName(SessionTokens.ROLE_CLAIM)
                    setAuthorityPrefix("ROLE_")
                },
            )
        }

    /** Reads the token from the session cookie instead of the `Authorization` header. */
    private object SessionCookieTokenResolver : BearerTokenResolver {
        override fun resolve(request: HttpServletRequest): String? =
            request.cookies
                ?.firstOrNull { it.name == SessionTokens.COOKIE_NAME }
                ?.value
                ?.takeIf { it.isNotEmpty() }
    }

    private object DiscardingAuthorizedClientRepository : OAuth2AuthorizedClientRepository {
        override fun <T : OAuth2AuthorizedClient> loadAuthorizedClient(
            clientRegistrationId: String,
            principal: Authentication,
            request: HttpServletRequest,
        ): T? = null

        override fun saveAuthorizedClient(
            authorizedClient: OAuth2AuthorizedClient,
            principal: Authentication,
            request: HttpServletRequest,
            response: HttpServletResponse,
        ) = Unit

        override fun removeAuthorizedClient(
            clientRegistrationId: String,
            principal: Authentication,
            request: HttpServletRequest,
            response: HttpServletResponse,
        ) = Unit
    }

    private companion object {
        const val DISCORD_API_URL = "https://discord.com/api/v10"
    }
}
