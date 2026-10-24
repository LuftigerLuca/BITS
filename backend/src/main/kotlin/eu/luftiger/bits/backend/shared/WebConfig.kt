package eu.luftiger.bits.backend.shared

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.SecurityContextHolderFilter

/**
 * The bot's API is protected by the service token alone; the dashboard has its own filter chains. Whatever none of
 * them covers is denied by [fallbackSecurityFilterChain].
 */
@Configuration(proxyBeanMethods = false)
class WebConfig {
    @Bean
    @Order(1)
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        properties: BitsProperties,
    ): SecurityFilterChain {
        http {
            securityMatcher("/api/**")
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            addFilterAfter<SecurityContextHolderFilter>(ServiceTokenFilter(properties.serviceToken))
            authorizeHttpRequests { authorize(anyRequest, permitAll) }
        }
        return http.build()
    }

    /**
     * Last in line, for paths outside `/api/`, `/auth/` and `/web/`: a controller with a mistyped prefix, or an
     * actuator endpoint, would otherwise answer without any check. Only the health check of Docker and the error page
     * stay open.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun fallbackSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            exceptionHandling { authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.FORBIDDEN) }
            authorizeHttpRequests {
                authorize("/actuator/health/**", permitAll)
                authorize("/error", permitAll)
                authorize(anyRequest, denyAll)
            }
        }
        return http.build()
    }
}
