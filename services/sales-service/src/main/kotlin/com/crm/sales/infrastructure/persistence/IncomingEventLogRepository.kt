package com.crm.sales.infrastructure.persistence

import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID

/**
 * Repository for the idempotency log.
 *
 * Uses the Panache companion pattern (same as the existing repositories
 * in this project).
 */
@ApplicationScoped
class IncomingEventLogRepository {

    /**
     * Check if an event has already been processed.
     */
    fun isProcessed(eventId: UUID): Boolean =
        IncomingEventLogEntity.find("eventId", eventId).firstResult() != null
    /**
     * Record that an event has been successfully processed.
     * Must be called within the same transaction as the domain changes.
     */
    fun markProcessed(
        eventId: UUID,
        eventType: String,
        source: String,
        entityId: String,
    ) {
        IncomingEventLogEntity().apply {
            this.eventId = eventId
            this.eventType = eventType
            this.source = source
            this.entityId = entityId
            this.processedAt = Instant.now()
        }.persist()
    }
}
