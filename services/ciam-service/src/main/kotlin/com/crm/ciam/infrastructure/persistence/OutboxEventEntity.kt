package com.crm.ciam.infrastructure.persistence

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Transactional Outbox — stores domain events durably in the same database
 * transaction as the aggregate they belong to.
 *
 * A background relay ([OutboxRelay]) polls this table and publishes events
 * to Kafka. Once published, rows are deleted.
 *
 * This guarantees that an event is never lost: either both the aggregate
 * update AND the outbox row commit together, or neither does. The relay
 * handles the eventual Kafka publish.
 *
 * Schema: `crm_ciam.outbox_event`
 */
@Entity
@Table(name = "outbox_event", schema = "ciam")
class OutboxEventEntity : PanacheEntityBase {

    @Id
    @Column(name = "event_id", nullable = false)
    lateinit var eventId: UUID

    /**
     * The aggregate root ID — used as Kafka partition key for ordering.
     */
    @Column(name = "entity_id", nullable = false)
    lateinit var entityId: String

    /**
     * The aggregate type (e.g., "customer", "contact"). Used for routing
     * and monitoring.
     */
    @Column(name = "entity_type", nullable = false, length = 64)
    lateinit var entityType: String

    /**
     * The event type discriminator (e.g., "LifecycleStageChanged").
     * Maps to the AsyncAPI `eventType` header.
     */
    @Column(name = "event_type", nullable = false, length = 128)
    lateinit var eventType: String

    /**
     * Bounded context source (e.g., "ciam"). Maps to the AsyncAPI source header.
     */
    @Column(name = "source", nullable = false, length = 32)
    lateinit var source: String

    /**
     * JSON-serialized event payload. Stored as text to avoid schema coupling.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    lateinit var payload: String

    /**
     * Correlation ID for distributed tracing / saga correlation.
     */
    @Column(name = "correlation_id")
    var correlationId: String? = null

    /**
     * Actor ID (who triggered the event). For audit trails.
     */
    @Column(name = "actor_id")
    var actorId: String? = null

    /**
     * W3C trace context headers (traceparent, tracestate) extracted at the time
     * the outbox event was created. Stored as a JSON-serialized map to bridge
     * the trace context across the transactional outbox boundary.
     *
     * Populated by application services via TraceContextCarrier.extractCurrentTraceHeaders().
     * Consumed by OutboxRelay to reinject the trace context before Kafka publish.
     */
    @Column(name = "metadata", columnDefinition = "text")
    var metadata: String? = null

    /**
     * Creation time. Used for ordering and monitoring lag.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    /**
     * Processing status. PENDING → relay picks up → PUBLISHED → deleted.
     * FAILED means the relay will retry with backoff.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: OutboxStatus = OutboxStatus.PENDING

    /**
     * Number of failed publish attempts. Used for backoff and dead-letter.
     */
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0

    companion object : PanacheCompanion<OutboxEventEntity>
}

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
}
