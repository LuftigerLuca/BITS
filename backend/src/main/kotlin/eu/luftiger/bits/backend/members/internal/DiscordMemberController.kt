package eu.luftiger.bits.backend.members.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.shared.DiscordMemberChange
import eu.luftiger.bits.shared.DiscordMembersReport
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The bot reports the members of the guild on start and every hour, and each member who joins or leaves. */
@RestController
@RequestMapping("/api/discord-members")
class DiscordMemberController(
    private val memberService: GuildMemberService,
) {
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun replace(
        @RequestBody report: DiscordMembersReport,
    ) {
        BotInput.memberCount(report.discordUserIds.size)
        report.discordUserIds.forEach(BotInput::discordUserId)
        memberService.replaceAll(report.discordUserIds.toSet())
    }

    @PostMapping("/joined")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun joined(
        @RequestBody change: DiscordMemberChange,
    ) {
        check(change)
        memberService.joined(change.discordUserId)
    }

    @PostMapping("/left")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun left(
        @RequestBody change: DiscordMemberChange,
    ) {
        check(change)
        memberService.left(change.discordUserId, change.memberName)
    }

    private fun check(change: DiscordMemberChange) {
        BotInput.discordUserId(change.discordUserId)
        BotInput.discordName(change.memberName)
    }
}
