package eu.luftiger.bits.backend.auth.internal

import jakarta.servlet.http.HttpServletRequest
import kotlinx.serialization.Serializable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Login and logout of the dashboard, reachable without a session. The Discord login itself is handled by Spring
 * Security under `/auth/oauth2/`. Error responses carry no body; the dashboard words them by status code.
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val adminLoginService: AdminLoginService,
    private val sessionTokens: SessionTokens,
    private val properties: AuthProperties,
) {
    @GetMapping("/options")
    fun options() = AuthOptionsResponse(adminLogin = properties.admin.enabled)

    @PostMapping("/login")
    fun login(
        @RequestBody body: AdminLoginRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Unit> =
        when (val result = adminLoginService.login(body.username, body.password, request.remoteAddr)) {
            is AdminLoginResult.Success ->
                ResponseEntity
                    .noContent()
                    .header(HttpHeaders.SET_COOKIE, sessionTokens.cookie(result.user).toString())
                    .build()
            AdminLoginResult.InvalidCredentials -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            AdminLoginResult.LockedOut -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
            AdminLoginResult.Disabled -> ResponseEntity.notFound().build()
        }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<Unit> =
        ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, sessionTokens.clearedCookie().toString())
            .build()
}

@Serializable
data class AuthOptionsResponse(
    /** Whether the admin account is configured; the dashboard hides its form otherwise. */
    val adminLogin: Boolean,
)

@Serializable
data class AdminLoginRequest(
    val username: String,
    val password: String,
)
