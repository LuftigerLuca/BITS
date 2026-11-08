package eu.luftiger.bits.backend.reprimand.internal

import eu.luftiger.bits.backend.reprimand.api.ReprimandRecords
import eu.luftiger.bits.backend.reprimand.api.dto.MemberReprimands
import eu.luftiger.bits.backend.reprimand.api.dto.ReprimandRecord
import eu.luftiger.bits.backend.reprimand.domain.Reprimand
import org.springframework.stereotype.Component

@Component
class ReprimandRecordsLookup(
    private val repository: ReprimandRepository,
) : ReprimandRecords {
    override fun recordsAbout(discordUserId: String) =
        MemberReprimands(
            received = repository.findByDiscordUserIdOrderById(discordUserId).map(::record),
            given = repository.findByActorDiscordUserIdOrderById(discordUserId).map(::record),
        )

    private fun record(reprimand: Reprimand) =
        ReprimandRecord(
            reprimand.memberName,
            reprimand.actorName,
            reprimand.reason,
            reprimand.createdAt,
            reprimand.timeoutUntil,
            reprimand.withdrawnAt,
        )
}
