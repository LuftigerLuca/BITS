package eu.luftiger.bits.backend.shared

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/**
 * `@ApplicationModuleListener`s run after the commit on their own thread. Without this they would run on the
 * committing thread, and a failing listener would surface as an error of the request that published the event.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig
