package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import org.springframework.data.jpa.repository.JpaRepository

interface RoleRuleRepository : JpaRepository<RoleRule, Long>
