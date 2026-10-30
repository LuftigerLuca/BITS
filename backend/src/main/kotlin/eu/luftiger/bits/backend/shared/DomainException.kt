package eu.luftiger.bits.backend.shared

/**
 * Why a service cannot carry out a request. Services throw these instead of HTTP exceptions; [ApiExceptionHandler]
 * turns them into a status and a problem detail. The message ends up in the answer, so it names no secrets.
 */
sealed class DomainException(
    message: String?,
) : RuntimeException(message)

/** 400: the input is malformed or breaks a rule of the feature. */
class InvalidInputException(
    reason: String,
) : DomainException(reason)

/** 404: what the request refers to does not exist (any more). */
class NotFoundException(
    reason: String? = null,
) : DomainException(reason)

/** 409: the input is fine, but clashes with what is saved, e.g. a role that has a rule already. */
class ConflictException(
    reason: String? = null,
) : DomainException(reason)

/** 410: the request refers to something that is over, e.g. a thread creation that failed in the meantime. */
class GoneException(
    reason: String,
) : DomainException(reason)

/** 422: the input is valid, but Discord does not allow it, e.g. a member who may not see the channel of a thread. */
class NotPossibleInDiscordException(
    reason: String,
) : DomainException(reason)

/** 429: the member asked too often, see [MemberRateLimit]. [retryAfterSeconds] tells the bot how long to wait. */
class TooManyRequestsException(
    val retryAfterSeconds: Long,
) : DomainException("Too many requests, retry in $retryAfterSeconds s")
