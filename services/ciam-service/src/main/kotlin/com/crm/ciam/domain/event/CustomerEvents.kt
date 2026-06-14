package com.crm.ciam.domain.event

import com.crm.common.messaging.EventEnvelope
import com.crm.common.messaging.Identifiable
import java.time.Instant
import java.util.UUID

/**
 * Domain events emitted by the CIAM Bounded Context.
 * These are published to Kafka via the infrastructure messaging layer.
 */

data class CustomerRegistered(
    override val entityId: String,
    val displayName: String,
    val source: String?,
    val registeredAt: Instant,
) : Identifiable

data class LeadQualified(
    override val entityId: String,
    val previousStage: String,
    val qualifiedAt: Instant,
) : Identifiable

data class CustomersMerged(
    val survivingCustomerId: String,
    val mergedCustomerId: String,
    val mergedAt: Instant,
) : Identifiable {
    override val entityId: String get() = survivingCustomerId
}

data class ConsentChanged(
    override val entityId: String,
    val purpose: String,
    val granted: Boolean,
    val changedAt: Instant,
) : Identifiable

data class EmailVerified(
    override val entityId: String,
    val email: String,
    val verifiedAt: Instant,
) : Identifiable

data class CustomerDeactivated(
    override val entityId: String,
    val reason: String?,
    val deactivatedAt: Instant,
) : Identifiable

data class LifecycleStageChanged(
    override val entityId: String,
    val fromStage: String,
    val toStage: String,
    val changedAt: Instant,
) : Identifiable

/** Wraps a CIAM domain event in the standard envelope. */
fun <T : Identifiable> envelopeOf(
    event: T,
    source: String = "ciam",
    correlationId: UUID? = null,
    actorId: String? = null,
): EventEnvelope<T> = EventEnvelope(
    eventType = event::class.simpleName!!,
    source = source,
    correlationId = correlationId,
    actorId = actorId,
    payload = event,
)
