package eu.luftiger.bits.backend.externalapi.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant

/**
 * A bearer token of the third-party API. Name, scopes and expiry are fixed when it is created: for other rights, an
 * admin creates a new token and revokes this one. Only the hash of the token is kept.
 */
@Entity
@Table(name = "api_token")
class ApiToken(
    @Column(name = "name", nullable = false)
    val name: String,
    @Column(name = "token_hash", nullable = false)
    val tokenHash: String,
    @Column(name = "token_hint", nullable = false)
    val tokenHint: String,
    /** IDs of the scopes, see `ExternalScope`; IDs that are no longer known grant nothing. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_token_scope", joinColumns = [JoinColumn(name = "token_id")])
    @Column(name = "scope", nullable = false)
    val scopes: Set<String>,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "created_by_name", nullable = false)
    val createdByName: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** Updated at most once a minute, see `ApiTokenRepository.touch`. */
    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
}
