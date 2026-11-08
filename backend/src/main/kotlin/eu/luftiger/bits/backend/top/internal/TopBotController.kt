package eu.luftiger.bits.backend.top.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.backend.top.domain.DiscordForum
import eu.luftiger.bits.backend.top.domain.ForumTag
import eu.luftiger.bits.shared.DiscordForumsReport
import eu.luftiger.bits.shared.TopAction
import eu.luftiger.bits.shared.TopActionReport
import eu.luftiger.bits.shared.TopActor
import eu.luftiger.bits.shared.TopPermissionRequest
import eu.luftiger.bits.shared.TopPermissionResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The bot reports the forum channels of the guild, asks before `/topclose` and `/topopen` whether the member may, and
 * reports what it did afterwards.
 */
@RestController
class TopBotController(
    private val service: TopService,
) {
    @PutMapping("/api/discord-forums")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forums(
        @RequestBody report: DiscordForumsReport,
    ) {
        BotInput.channelCount(report.forums.size)
        report.forums.forEach { forum ->
            BotInput.channelId(forum.id)
            BotInput.channelName(forum.name)
            forum.category?.let(BotInput::channelName)
            BotInput.tagCount(forum.tags.size)
            forum.tags.forEach {
                BotInput.tagId(it.id)
                BotInput.channelName(it.name)
            }
        }
        service.replaceForums(
            report.forums.map { forum ->
                DiscordForum(
                    forum.id,
                    forum.name,
                    forum.category,
                    forum.position,
                    forum.threadsManageable,
                    forum.tags.map { ForumTag(it.id, it.name) },
                )
            },
        )
    }

    @PostMapping("/api/tops/permission")
    fun permission(
        @RequestBody request: TopPermissionRequest,
    ): TopPermissionResponse {
        BotInput.channelId(request.forumId)
        return when (val permission = service.permission(actor(request.actor), request.forumId)) {
            is TopPermission.Allowed -> TopPermissionResponse.Allowed(permission.doneTagId)
            TopPermission.Forbidden -> TopPermissionResponse.Forbidden
            TopPermission.NotTopForum -> TopPermissionResponse.NotTopForum
        }
    }

    @PostMapping("/api/tops/actions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun action(
        @RequestBody report: TopActionReport,
    ) {
        BotInput.channelId(report.forumId)
        BotInput.channelId(report.threadId)
        BotInput.channelName(report.threadName)
        report.reason?.let(BotInput::reason)
        val action =
            when (report.action) {
                TopAction.CLOSED -> TopRecord.Closed(report.reason?.trim()?.ifEmpty { null }, report.archived)
                TopAction.REOPENED -> TopRecord.Reopened
            }
        service.record(actor(report.actor), report.forumId, report.threadName, action)
    }

    private fun actor(actor: TopActor): TopMember {
        BotInput.discordUserId(actor.discordUserId)
        BotInput.memberName(actor.name)
        actor.avatarUrl?.let(BotInput::avatarUrl)
        BotInput.roleCount(actor.roleIds.size)
        actor.roleIds.forEach(BotInput::roleId)
        return TopMember(AuditActor("discord:${actor.discordUserId}", actor.name, actor.avatarUrl), actor.administrator, actor.roleIds)
    }
}
