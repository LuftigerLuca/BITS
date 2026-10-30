package eu.luftiger.bits.backend.shared

import eu.luftiger.bits.warera.WareraApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Answers every error under `/web` and `/api` with a problem detail (RFC 9457): the [DomainException]s of the
 * services, WarEra being unreachable, and through [ResponseEntityExceptionHandler] what Spring MVC rejects itself
 * (unreadable bodies, failed validation, a `ResponseStatusException` of a controller).
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(DomainException::class)
    fun domain(
        e: DomainException,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val status =
            when (e) {
                is InvalidInputException -> HttpStatus.BAD_REQUEST
                is NotFoundException -> HttpStatus.NOT_FOUND
                is ConflictException -> HttpStatus.CONFLICT
                is GoneException -> HttpStatus.GONE
                is NotPossibleInDiscordException -> HttpStatus.UNPROCESSABLE_CONTENT
                is TooManyRequestsException -> HttpStatus.TOO_MANY_REQUESTS
            }
        val headers = HttpHeaders()
        if (e is TooManyRequestsException) headers.set(HttpHeaders.RETRY_AFTER, e.retryAfterSeconds.toString())
        return handleExceptionInternal(e, ProblemDetail.forStatusAndDetail(status, e.message), headers, status, request)
    }

    /** The bot tells the member that WarEra cannot be reached, the dashboard says so as well. */
    @ExceptionHandler(WareraApiException::class)
    fun wareraUnavailable(
        e: WareraApiException,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        log.warn("WarEra API not reachable", e)
        val status = HttpStatus.SERVICE_UNAVAILABLE
        return handleExceptionInternal(e, ProblemDetail.forStatusAndDetail(status, "WarEra not reachable"), HttpHeaders(), status, request)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
