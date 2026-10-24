package eu.luftiger.bits.backend

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The path of an endpoint decides which filter chain protects it, see "Backend architecture" in CONTRIBUTING.md.
 * A mistyped prefix would otherwise go unnoticed.
 */
@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@Import(TestcontainersConfig::class)
class EndpointPathTest {
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    lateinit var handlerMapping: RequestMappingHandlerMapping

    private val paths by lazy {
        handlerMapping.handlerMethods.keys
            .flatMap { it.pathPatternsCondition?.patternValues.orEmpty() }
            .filterNot { it == "/error" }
    }

    @Test
    fun `every endpoint lies under api, web or auth`() {
        val outside = paths.filterNot { path -> PREFIXES.any { path.startsWith(it) } }
        assertTrue(outside.isEmpty(), "Endpoints outside of ${PREFIXES.joinToString()}: $outside")
    }

    @Test
    fun `open endpoints under auth are known`() {
        assertEquals(OPEN_PATHS, paths.filter { it.startsWith("/auth/") }.toSet())
    }

    private companion object {
        val PREFIXES = listOf("/api/", "/web/", "/auth/")

        /** Reachable without any session. Extend only for endpoints that must be, such as a login. */
        val OPEN_PATHS = setOf("/auth/options", "/auth/login", "/auth/logout")
    }
}
