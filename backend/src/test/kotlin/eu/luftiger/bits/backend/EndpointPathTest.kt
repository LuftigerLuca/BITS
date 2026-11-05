package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.externalapi.internal.ExternalEndpoint
import eu.luftiger.bits.backend.externalapi.internal.ExternalScope
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
    fun `every endpoint lies under api, web, auth or external`() {
        val outside = paths.filterNot { path -> PREFIXES.any { path.startsWith(it) } }
        assertTrue(outside.isEmpty(), "Endpoints outside of ${PREFIXES.joinToString()}: $outside")
    }

    @Test
    fun `open endpoints under auth are known`() {
        assertEquals(OPEN_PATHS, paths.filter { it.startsWith("/auth/") }.toSet())
    }

    @Test
    fun `open endpoints under external are the documentation only`() {
        val open = paths.filter { it.startsWith("/external/") && !it.startsWith("/external/v1/") }
        assertTrue(open.all { it.startsWith("/external/docs") }, "Open endpoints under /external/: $open")
    }

    @Test
    fun `every endpoint of the third-party API has the scope of its path`() {
        val endpoints =
            handlerMapping.handlerMethods
                .flatMap { (info, method) ->
                    info.pathPatternsCondition
                        ?.patternValues
                        .orEmpty()
                        .map { it to method }
                }.filter { (path, _) -> path.startsWith("/external/v1/") }
        assertEquals(ExternalScope.entries.size, endpoints.size)
        for ((path, method) in endpoints) {
            val scope = method.getMethodAnnotation(ExternalEndpoint::class.java)?.scope
            assertEquals(path, scope?.path, "Scope of $path")
        }
    }

    private companion object {
        val PREFIXES = listOf("/api/", "/web/", "/auth/", "/external/")

        /** Reachable without any session. Extend only for endpoints that must be, such as a login. */
        val OPEN_PATHS = setOf("/auth/options", "/auth/login", "/auth/logout")
    }
}
