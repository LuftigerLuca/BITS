package eu.luftiger.bits.backend.top.internal

import eu.luftiger.bits.backend.top.domain.DiscordForum
import eu.luftiger.bits.backend.top.domain.TopForum
import org.springframework.data.jpa.repository.JpaRepository

interface DiscordForumRepository : JpaRepository<DiscordForum, String>

interface TopForumRepository : JpaRepository<TopForum, String>
