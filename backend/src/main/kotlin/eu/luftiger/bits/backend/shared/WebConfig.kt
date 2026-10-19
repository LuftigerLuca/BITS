package eu.luftiger.bits.backend.shared

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class WebConfig {
    @Bean
    fun serviceTokenFilter(properties: BitsProperties): FilterRegistrationBean<ServiceTokenFilter> =
        FilterRegistrationBean(ServiceTokenFilter(properties.serviceToken)).apply {
            addUrlPatterns("/api/*")
        }
}
