package eu.luftiger.bits.backend.shared

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.SecurityContextHolderFilter

/** The bot's API is protected by the service token alone; the dashboard has its own filter chains. */
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
}
