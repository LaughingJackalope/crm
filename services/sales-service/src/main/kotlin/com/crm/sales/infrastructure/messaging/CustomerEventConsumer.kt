package com.crm.sales.infrastructure.messaging

import com.crm.sales.application.OpportunityCommandService
import com.crm.sales.domain.opportunity.Money
import com.crm.sales.infrastructure.persistence.IncomingEventLogRepository
import io.smallrye.common.annotation.Blocking
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.util.UUID

/**
 * Consumes customer lifecycle events from the CIAM bounded context and
 * orchestrates local Sales domain commands.
 *
 * ## Idempotent Consumer Pattern
 *
 * Before processing any event, we check the [IncomingEventLogRepository]
 * for the event's unique ID. If already processed, the event is acknowledged
 * without re-processing. After successful processing, the event ID is
 * recorded in the same transaction as the domain changes.
 *
 * ## Reactive Transaction
 *
 * The consumer method is `@Blocking` (runs on a worker thread) and
 * `@Transactional`. The entire operation — idempotency check, opportunity
 * creation, and idempotency record — succeeds or fails together. If the
 * transaction rolls back, the Kafka offset is not committed, and the
 * event will be redelivered.
 *
 * ## Event routing
 *
 * | Event Type | Action |
 * |---|---|
 * | CustomerRegistered | Create a new Opportunity for the customer |
 * | LeadQualified | Create a new Opportunity (if not already created) |
 * | LifecycleStageChanged | Log for analytics (no-op for now) |
 * | Other | Log and acknowledge |
 */
@ApplicationScoped
class CustomerEventConsumer @Inject constructor(
    private val opportunityCommandService: OpportunityCommandService,
    private val eventLogRepository: IncomingEventLogRepository,
) {

    private val log = Logger.getLogger(CustomerEventConsumer::class.java)

    /**
     * Consume events from the `customer-events` Kafka channel.
     *
     * The `@Blocking` annotation ensures this runs on a worker thread pool
     * (not the event loop), which is required for JPA/Panache operations.
     *
     * The `@Transactional` annotation wraps the entire operation in a
     * database transaction. If any step fails, the transaction rolls back
     * and the Kafka offset is not committed — the event will be retried.
     */
    @Incoming("customer-events")
    @Blocking
    @Transactional
    fun consume(envelope: CustomerEventEnvelope) {
        val eventId = envelope.eventType + "-" + envelope.payload.entityId

        // Use entityId + eventType as idempotency key since the outbox
        // eventId is not in the envelope payload
        val idempotencyKey = buildIdempotencyKey(envelope)

        // Idempotency check: skip already-processed events
        if (eventLogRepository.isProcessed(idempotencyKey)) {
            log.infof("Skipping duplicate event: type=%s, entity=%s", envelope.eventType, envelope.payload.entityId)
            return
        }

        log.infof("Processing event: type=%s, entity=%s, source=%s",
            envelope.eventType, envelope.payload.entityId, envelope.source)

        when (envelope.eventType) {
            "CustomerRegistered" -> handleCustomerRegistered(envelope)
            "LeadQualified" -> handleLeadQualified(envelope)
            "LifecycleStageChanged" -> handleLifecycleStageChanged(envelope)
            else -> log.debugf("Ignoring unhandled event type: %s", envelope.eventType)
        }

        // Record idempotency AFTER successful processing (same TX)
        eventLogRepository.markProcessed(
            eventId = idempotencyKey,
            eventType = envelope.eventType,
            source = envelope.source,
            entityId = envelope.payload.entityId ?: "unknown",
        )
    }

    private fun handleCustomerRegistered(envelope: CustomerEventEnvelope) {
        val customerId = envelope.payload.entityId ?: run {
            log.warn("CustomerRegistered event missing entityId, skipping")
            return
        }
        val displayName = envelope.payload.displayName ?: "Unknown"
        val source = envelope.payload.source

        log.infof("Creating opportunity for new customer: %s (%s)", customerId, displayName)

        opportunityCommandService.createOpportunity(
            customerId = customerId,
            name = "New Lead: $displayName",
            amount = Money(BigDecimal.ZERO),
            source = source,
        )
    }

    private fun handleLeadQualified(envelope: CustomerEventEnvelope) {
        val customerId = envelope.payload.entityId ?: run {
            log.warn("LeadQualified event missing entityId, skipping")
            return
        }
        val displayName = envelope.payload.displayName ?: "Qualified Lead"

        log.infof("Creating opportunity for qualified lead: %s", customerId)

        opportunityCommandService.createOpportunity(
            customerId = customerId,
            name = "Qualified: $displayName",
            amount = Money(BigDecimal.ZERO),
            source = "lead-qualified",
        )
    }

    private fun handleLifecycleStageChanged(envelope: CustomerEventEnvelope) {
        val customerId = envelope.payload.entityId ?: return
        val fromStage = envelope.payload.fromStage
        val toStage = envelope.payload.toStage

        log.infof("Customer %s lifecycle changed: %s → %s", customerId, fromStage, toStage)

        // For now, just log. In a full implementation, this could:
        // - Update opportunity stage to match customer lifecycle
        // - Close opportunities when customer churns
        // - Escalate when customer reaches ADVOCATE stage
    }

    private fun buildIdempotencyKey(envelope: CustomerEventEnvelope): UUID {
        // Build a deterministic UUID from eventType + entityId for idempotency.
        // This ensures the same event is never processed twice, even if
        // redelivered by Kafka.
        val raw = "${envelope.eventType}:${envelope.payload.entityId}"
        return UUID.nameUUIDFromBytes(raw.toByteArray())
    }
}
