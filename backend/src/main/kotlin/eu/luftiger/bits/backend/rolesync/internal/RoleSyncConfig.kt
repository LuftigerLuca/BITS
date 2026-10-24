package eu.luftiger.bits.backend.rolesync.internal

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class RoleSyncConfig
