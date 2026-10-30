package eu.luftiger.bits.backend.shared

import eu.luftiger.bits.warera.RestWareraClient
import eu.luftiger.bits.warera.WareraCallListener
import eu.luftiger.bits.warera.WareraClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class WareraConfig {
    /** [listener] is the statistics of the page "System-Status", if a module provides one. */
    @Bean
    fun wareraClient(
        builder: RestClient.Builder,
        properties: BitsProperties,
        clock: Clock,
        listener: ObjectProvider<WareraCallListener>,
    ): WareraClient {
        val calls = listener.getIfAvailable { WareraCallListener.NONE }
        val rest = RestWareraClient(builder.baseUrl(properties.warera.baseUrl).build(), listener = calls)
        return CachingWareraClient(rest, clock, listener = calls)
    }
}
