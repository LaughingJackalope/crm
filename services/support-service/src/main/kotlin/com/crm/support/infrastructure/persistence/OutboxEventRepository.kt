package com.crm.support.infrastructure.persistence

import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class OutboxEventRepository {

    fun save(event: OutboxEventEntity) { event.persist() }

    fun findPending(batchSize: Int = 100): List<OutboxEventEntity> =
        OutboxEventEntity.list("status = ?1 ORDER BY createdAt ASC", OutboxStatus.PENDING)
            .take(batchSize)

    fun findFailedForRetry(maxRetries: Int = 5, retryThreshold: Instant = Instant.now().minusSeconds(60)): List<OutboxEventEntity> =
        OutboxEventEntity.list(
            "status = ?1 AND retryCount < ?2 AND createdAt < ?3 ORDER BY createdAt ASC",
            OutboxStatus.FAILED, maxRetries, retryThreshold
        ).take(100)

    fun markFailed(eventId: UUID) {
        val entity = OutboxEventEntity.find("eventId", eventId).firstResult() ?: return
        entity.retryCount++
        entity.status = if (entity.retryCount >= 5) OutboxStatus.FAILED else OutboxStatus.PENDING
        entity.persist()
    }

    fun remove(eventId: UUID) { OutboxEventEntity.delete("eventId", eventId) }

    fun countPending(): Long = OutboxEventEntity.count("status", OutboxStatus.PENDING)

    fun countFailed(): Long = OutboxEventEntity.count("status", OutboxStatus.FAILED)
}
