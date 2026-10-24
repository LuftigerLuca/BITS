package eu.luftiger.bits.backend

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Postgres and Redis for the integration tests. Every test class with its own configuration gets its own Spring
 * context, so the containers live in the companion object and are shared by all contexts of the JVM. Spring must not
 * stop them when a context closes; Testcontainers removes them when the JVM exits.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {
    @Bean(destroyMethod = "")
    @ServiceConnection
    fun postgres() = POSTGRES

    @Bean(destroyMethod = "")
    @ServiceConnection(name = "redis")
    fun redis() = REDIS

    companion object {
        private val POSTGRES = PostgreSQLContainer("postgres:17-alpine")
        private val REDIS = GenericContainer("redis:8-alpine").withExposedPorts(6379)
    }
}
