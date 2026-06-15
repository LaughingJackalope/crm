package com.crm.billing.infrastructure.messaging

import com.crm.billing.application.InvoiceOrchestrationService
import com.crm.billing.domain.BillingDomainException
import com.crm.billing.infrastructure.persistence.IncomingEventLogRepository
import io.smallrye.common.annotation.Blocking
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import java.time.LocalDate
import java.util.UUID

/**
 * Consumes opportunity events from the Sales bounded context and
 * orchestrates invoice generation.
 *
 * ## Flow
 * 1. Idempotency check via [IncomingEventLogRepository].
 * 2. Route `OpportunityClosed` (won) → [InvoiceOrchestrationService.createInvoice].
 * 3. Record idempotency after successful processing (same TX).
 *
 * ## Error handling
 * - Retries (3, exponential backoff) for transient failures via channel config.
 * - Persistent failures route to DLQ after retries are exhausted.
 * - [BillingDomainException.ConsumerProcessingException] messages are captured
 *   in dead-letter headers.
 */
@ApplicationScoped
class OpportunityEventConsumer @Inject constructor(
    private val invoiceOrchestrationService: InvoiceOrchestrationService,
    private val eventLogRepository: IncomingEventLogRepository,
) {

    private val log = Logger.getLogger(OpportunityEventConsumer::class.java)

    @Incoming("sales-opportunity-events")
    @Blocking
    @Transactional
    fun consume(envelope: OpportunityEventEnvelope) {
        val opportunityId = envelope.payload.opportunityId
            ?: throw BillingDomainException.ConsumerProcessingException(
                "OpportunityClosed event missing opportunityId"
            )

        val idempotencyKey = buildIdempotencyKey(opportunityId)

        // Idempotency check
        if (eventLogRepository.isProcessed(idempotencyKey)) {
            log.infof("Skipping duplicate event: type=%s, opportunity=%s",
                envelope.eventType, opportunityId)
            return
        }

        log.infof("Processing event: type=%s, opportunity=%s, outcome=%s",
            envelope.eventType, opportunityId, envelope.payload.outcome)

        when (envelope.eventType) {
            "OpportunityClosed" -> {
                val isWon = envelope.payload.isWon
                    ?: (envelope.payload.outcome == "won")
                if (isWon) {
                    invoiceOrchestrationService.createInvoice(
                        opportunityId = opportunityId,
                        customerId = resolveCustomerId(opportunityId),
                        invoiceNumber = generateInvoiceNumber(opportunityId),
                        dueDate = LocalDate.now().plusDays(30),
                    )
                } else {
                    log.infof("Opportunity %s was lost — no invoice generated", opportunityId)
                }
            }
            else -> log.debugf("Ignoring unhandled event type: %s", envelope.eventType)
        }

        // Record idempotency AFTER successful processing (same TX)
        eventLogRepository.markProcessed(
            eventId = idempotencyKey,
            eventType = envelope.eventType,
            source = envelope.source,
            entityId = opportunityId,
        )
    }

    private fun buildIdempotencyKey(opportunityId: String): UUID {
        val raw = "invoice:$opportunityId"
        return UUID.nameUUIDFromBytes(raw.toByteArray())
    }

    private fun resolveCustomerId(opportunityId: String): String {
        // In production, query the local Opportunity read model table
        // populated by the OpportunityCreated event stream.
        return "pending:$opportunityId"
    }

    private fun generateInvoiceNumber(opportunityId: String): String {
        val shortId = opportunityId.takeLast(8).uppercase()
        val timestamp = System.currentTimeMillis()
        return "INV-$shortId-$timestamp"
    }
}
