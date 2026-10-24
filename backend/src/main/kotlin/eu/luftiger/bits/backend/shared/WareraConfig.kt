package eu.luftiger.bits.backend.shared

import eu.luftiger.bits.warera.RestWareraClient
import eu.luftiger.bits.warera.WareraClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class WareraConfig {
    @Bean
    fun wareraClient(
        builder: RestClient.Builder,
        properties: BitsProperties,
        clock: Clock,
    ): WareraClient = CachingWareraClient(RestWareraClient(builder.baseUrl(properties.warera.baseUrl).build()), clock)
}
