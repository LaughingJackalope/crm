package com.crm.sales.infrastructure.persistence

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Idempotency log for incoming events from upstream Bounded Contexts.
 *
 * Before processing any event from Kafka, the consumer checks this table
 * for the event's unique ID. If found, the event is a duplicate and is
 * skipped (acknowledged without processing).
 *
 * After successful processing, the event ID is recorded here within the
 * same transaction as the domain changes. This guarantees exactly-once
 * processing semantics even with at-least-once Kafka delivery.
 *
 * Schema: `crm_sales.incoming_event_log`
 */
@Entity
@Table(name = "incoming_event_log", schema = "sales")
class IncomingEventLogEntity : PanacheEntityBase {

    @Id
    @Column(name = "event_id", nullable = false)
    lateinit var eventId: UUID

    /**
     * The event type discriminator (e.g., "LeadQualified", "CustomerRegistered").
     */
    @Column(name = "event_type", nullable = false, length = 128)
    lateinit var eventType: String

    /**
     * The source bounded context (e.g., "ciam").
     */
    @Column(name = "source", nullable = false, length = 32)
    lateinit var source: String

    /**
     * The aggregate root ID from the upstream context.
     */
    @Column(name = "entity_id", nullable = false, length = 36)
    lateinit var entityId: String

    /**
     * When this event was first processed.
     */
    @Column(name = "processed_at", nullable = false)
    lateinit var processedAt: Instant

    companion object : PanacheCompanion<IncomingEventLogEntity>
}
