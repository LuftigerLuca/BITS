package eu.luftiger.bits.backend

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {
    @Bean
    @ServiceConnection
    fun postgres() = PostgreSQLContainer("postgres:17-alpine")

    @Bean
    @ServiceConnection(name = "redis")
    fun redis() = GenericContainer("redis:8-alpine").withExposedPorts(6379)
}
