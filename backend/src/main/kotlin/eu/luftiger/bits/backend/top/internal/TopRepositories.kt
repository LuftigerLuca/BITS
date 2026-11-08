package eu.luftiger.bits.backend.top.internal

import eu.luftiger.bits.backend.top.domain.DiscordForum
import eu.luftiger.bits.backend.top.domain.TopForum
import eu.luftiger.bits.backend.top.domain.TopManagerRole
import org.springframework.data.jpa.repository.JpaRepository

interface DiscordForumRepository : JpaRepository<DiscordForum, String>

interface TopForumRepository : JpaRepository<TopForum, String>

interface TopManagerRoleRepository : JpaRepository<TopManagerRole, String>
