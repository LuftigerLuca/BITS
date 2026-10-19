package eu.luftiger.bits.backend.shared

import eu.luftiger.bits.warera.RestWareraClient
import eu.luftiger.bits.warera.WareraClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
class WareraConfig {
    @Bean
    fun wareraClient(
        builder: RestClient.Builder,
        properties: BitsProperties,
    ): WareraClient = RestWareraClient(builder.baseUrl(properties.warera.baseUrl).build())
}
