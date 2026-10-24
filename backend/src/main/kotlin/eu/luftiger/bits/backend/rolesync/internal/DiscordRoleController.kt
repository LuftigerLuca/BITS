package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.shared.DiscordRolesReport
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The bot reports the roles of the guild on start and whenever one is created, changed or deleted. */
@RestController
@RequestMapping("/api/discord-roles")
class DiscordRoleController(
    private val roleService: DiscordRoleService,
) {
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun replace(
        @RequestBody report: DiscordRolesReport,
    ) = roleService.replaceAll(report.roles)
}
