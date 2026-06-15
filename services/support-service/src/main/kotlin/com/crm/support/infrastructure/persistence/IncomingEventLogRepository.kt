package com.crm.support.infrastructure.persistence

import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class IncomingEventLogRepository {

    fun isProcessed(eventId: UUID): Boolean =
        IncomingEventLogEntity.find("eventId", eventId).firstResult() != null

    fun markProcessed(eventId: UUID, eventType: String, source: String, entityId: String) {
        IncomingEventLogEntity().apply {
            this.eventId = eventId
            this.eventType = eventType
            this.source = source
            this.entityId = entityId
            this.processedAt = Instant.now()
        }.persist()
    }
}
