package com.crm.billing.infrastructure.persistence

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Transactional Outbox — stores domain events durably in the same database
 * transaction as the aggregate they belong to.
 * Schema: `crm_billing.outbox_event`
 */
@Entity
@Table(name = "outbox_event", schema = "billing")
class OutboxEventEntity : PanacheEntityBase {

    @Id
    @Column(name = "event_id", nullable = false)
    lateinit var eventId: UUID

    @Column(name = "entity_id", nullable = false, length = 36)
    lateinit var entityId: String

    @Column(name = "entity_type", nullable = false, length = 64)
    lateinit var entityType: String

    @Column(name = "event_type", nullable = false, length = 128)
    lateinit var eventType: String

    @Column(name = "source", nullable = false, length = 32)
    lateinit var source: String

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    lateinit var payload: String

    @Column(name = "correlation_id")
    var correlationId: String? = null

    @Column(name = "actor_id")
    var actorId: String? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: OutboxStatus = OutboxStatus.PENDING

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0

    companion object : PanacheCompanion<OutboxEventEntity>

    @Column(name = "metadata", columnDefinition = "text")
    var metadata: String? = null
}

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
}
