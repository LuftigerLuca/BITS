package eu.luftiger.bits.backend.reprimand.api.dto

import org.springframework.modulith.NamedInterface
import java.time.Instant

/** The reprimands a member got and those they gave, withdrawn ones too. */
@NamedInterface("api")
data class MemberReprimands(
    val received: List<ReprimandRecord>,
    val given: List<ReprimandRecord>,
)

@NamedInterface("api")
data class ReprimandRecord(
    val memberName: String,
    val actorName: String,
    val reason: String,
    val createdAt: Instant,
    val timeoutUntil: Instant?,
    val withdrawnAt: Instant?,
)
