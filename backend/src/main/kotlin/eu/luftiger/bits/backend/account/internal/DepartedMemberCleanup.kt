package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.api.AccountRemoval
import eu.luftiger.bits.backend.account.api.dto.DepartedMember
import eu.luftiger.bits.backend.systemlog.api.AccountRemovalRecord
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DepartedMemberCleanup(
    private val linkRepository: AccountLinkRepository,
    private val verificationRepository: AccountVerificationRepository,
    private val nicknameOptInRepository: NicknameOptInRepository,
    private val systemLog: SystemLog,
) : AccountRemoval {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun removeDeparted(members: Collection<DepartedMember>): Int {
        val byId = members.associateBy { it.discordUserId }
        val links = linkRepository.findAllById(byId.keys)
        linkRepository.deleteAll(links)
        verificationRepository.deleteAllById(byId.keys)
        nicknameOptInRepository.deleteAllById(byId.keys)
        links.forEach { link ->
            systemLog.recordAccountRemoved(
                AccountRemovalRecord(link.discordUserId, byId[link.discordUserId]?.discordName, link.wareraUserId, link.wareraUsername),
            )
            log.info("Link of user {} deleted, they left the guild", link.discordUserId)
        }
        return links.size
    }
}
