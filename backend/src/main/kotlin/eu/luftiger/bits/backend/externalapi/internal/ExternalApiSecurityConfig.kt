package eu.luftiger.bits.backend.externalapi.internal

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.savedrequest.NullRequestCache

/**
 * The third-party API under `/external/v1/`, with API tokens only, and its documentation under `/external/docs` and
 * `/external/swagger-ui/`, open to everybody. Neither knows the session cookie of the dashboard or the service token of
 * the bot. Only GET is allowed: the API only reads.
 */
@Configuration(proxyBeanMethods = false)
class ExternalApiSecurityConfig {
    @Bean
    @Order(4)
    fun externalApiSecurityFilterChain(
        http: HttpSecurity,
        tokens: ApiTokenService,
    ): SecurityFilterChain {
        http {
            securityMatcher("/external/v1/**")
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            requestCache { requestCache = NullRequestCache() }
            addFilterAfter<SecurityContextHolderFilter>(ApiTokenFilter(tokens))
            authorizeHttpRequests {
                authorize(HttpMethod.GET, "/external/v1/**", authenticated)
                authorize(anyRequest, denyAll)
            }
        }
        return http.build()
    }

    @Bean
    @Order(5)
    fun externalDocsSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            securityMatcher("/external/docs", "/external/docs/**", "/external/swagger-ui/**")
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            requestCache { requestCache = NullRequestCache() }
            authorizeHttpRequests {
                authorize(HttpMethod.GET, "/**", permitAll)
                authorize(anyRequest, denyAll)
            }
        }
        return http.build()
    }
}
