package com.crm.common.messaging

import java.time.Instant
import java.util.UUID

/**
 * Wrapper for all domain events published to Kafka.
 * Ensures consistent routing key structure and metadata.
 */
data class EventEnvelope<T>(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val source: String,           // Bounded Context name, e.g. "ciam"
    val timestamp: Instant = Instant.now(),
    val correlationId: UUID?,     // Trace / saga correlation
    val actorId: String?,         // Who triggered the event
    val version: String = "1.0",
    val payload: T,
) {
    /** Kafka partition key — entity ID for ordering guarantees */
    val partitionKey: String
        get() = when (payload) {
            is Identifiable -> payload.entityId
            else -> eventId.toString()
        }
}

/** Marker interface for domain event payloads that own an entity ID. */
interface Identifiable {
    val entityId: String
}
