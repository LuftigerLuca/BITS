package eu.luftiger.bits.backend.shared

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Turns on `@Scheduled` for all modules, so that no module depends on another one for its schedule. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig
