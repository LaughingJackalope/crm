package com.crm.communication.infrastructure.messaging

import com.crm.communication.application.NotificationOrchestrationService
import com.crm.communication.infrastructure.persistence.IncomingEventLogRepository
import io.smallrye.common.annotation.Blocking
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import java.util.UUID

@ApplicationScoped
class UnifiedEventConsumer @Inject constructor(
    private val notificationOrchestrationService: NotificationOrchestrationService,
    private val eventLogRepository: IncomingEventLogRepository,
) {
    private val log = Logger.getLogger(UnifiedEventConsumer::class.java)

    @Incoming("ciam-lifecycle-events")
    @Blocking
    @Transactional
    fun consumeCiamEvents(envelope: DomainEventEnvelope) {
        val entityId = envelope.payload.contactId ?: envelope.payload.entityId ?: "unknown"
        val idempotencyKey = buildIdempotencyKey("ciam", envelope.eventType, entityId)
        if (eventLogRepository.isProcessed(idempotencyKey)) { return }

        when (envelope.eventType) {
            "CustomerRegistered" -> notificationOrchestrationService.handleCustomerRegistered(
                recipientId = envelope.payload.contactId ?: "",
                email = envelope.payload.email, displayName = envelope.payload.displayName,
                source = envelope.payload.source,
            )
            "CustomerDeactivated" -> notificationOrchestrationService.handleCustomerDeactivated(
                recipientId = envelope.payload.contactId ?: "", reason = envelope.payload.reason,
            )
            "LifecycleStageChanged" -> notificationOrchestrationService.handleLifecycleStageChanged(
                recipientId = envelope.payload.contactId ?: "",
                fromStage = envelope.payload.fromStage, toStage = envelope.payload.toStage,
            )
            "EmailVerified" -> notificationOrchestrationService.handleEmailVerified(
                recipientId = envelope.payload.contactId ?: "", email = envelope.payload.email,
            )
            "CustomersMerged" -> notificationOrchestrationService.handleCustomersMerged(
                survivingContactId = envelope.payload.contactId ?: "",
                mergedContactId = envelope.payload.entityId,
            )
            "ConsentChanged" -> notificationOrchestrationService.handleConsentChanged(
                recipientId = envelope.payload.contactId ?: "",
                purpose = envelope.payload.purpose, granted = envelope.payload.granted,
            )
            else -> log.debugf("Ignoring unhandled CIAM event type: %s", envelope.eventType)
        }
        eventLogRepository.markProcessed(idempotencyKey, envelope.eventType, "ciam", entityId)
    }

    @Incoming("billing-events")
    @Blocking
    @Transactional
    fun consumeBillingEvents(envelope: DomainEventEnvelope) {
        val entityId = envelope.payload.invoiceId ?: envelope.payload.entityId ?: "unknown"
        val idempotencyKey = buildIdempotencyKey("billing", envelope.eventType, entityId)
        if (eventLogRepository.isProcessed(idempotencyKey)) { return }

        when (envelope.eventType) {
            "InvoiceGenerated" -> notificationOrchestrationService.handleInvoiceGenerated(
                recipientId = envelope.payload.entityId ?: "",
                invoiceId = envelope.payload.invoiceId, totalAmount = envelope.payload.totalAmount,
                currency = envelope.payload.currency, dueDate = envelope.payload.dueDate,
            )
            "PaymentSucceeded" -> notificationOrchestrationService.handlePaymentSucceeded(
                recipientId = envelope.payload.entityId ?: "",
                invoiceId = envelope.payload.invoiceId, amount = envelope.payload.amount,
            )
            "PaymentFailed" -> notificationOrchestrationService.handlePaymentFailed(
                recipientId = envelope.payload.entityId ?: "",
                invoiceId = envelope.payload.invoiceId, reason = envelope.payload.reason,
            )
            "SubscriptionCreated" -> notificationOrchestrationService.handleSubscriptionCreated(
                recipientId = envelope.payload.entityId ?: "",
                subscriptionId = envelope.payload.subscriptionId, planId = envelope.payload.planId,
            )
            "SubscriptionCancelled" -> notificationOrchestrationService.handleSubscriptionCancelled(
                recipientId = envelope.payload.entityId ?: "",
                subscriptionId = envelope.payload.subscriptionId, reason = envelope.payload.reason,
            )
            "DunningEscalated" -> notificationOrchestrationService.handleDunningEscalated(
                recipientId = envelope.payload.entityId ?: "", invoiceId = envelope.payload.invoiceId,
            )
            else -> log.debugf("Ignoring unhandled Billing event type: %s", envelope.eventType)
        }
        eventLogRepository.markProcessed(idempotencyKey, envelope.eventType, "billing", entityId)
    }

    @Incoming("support-events")
    @Blocking
    @Transactional
    fun consumeSupportEvents(envelope: DomainEventEnvelope) {
        val entityId = envelope.payload.ticketId ?: envelope.payload.entityId ?: "unknown"
        val idempotencyKey = buildIdempotencyKey("support", envelope.eventType, entityId)
        if (eventLogRepository.isProcessed(idempotencyKey)) { return }

        when (envelope.eventType) {
            "TicketCreated" -> notificationOrchestrationService.handleTicketCreated(
                recipientId = envelope.payload.requesterId ?: "",
                ticketId = envelope.payload.ticketId, subject = envelope.payload.ticketSubject,
                priority = envelope.payload.priority,
            )
            "TicketAssigned" -> notificationOrchestrationService.handleTicketAssigned(
                assigneeId = envelope.payload.assigneeId ?: "",
                ticketId = envelope.payload.ticketId, queueId = envelope.payload.queueId,
            )
            "TicketStatusChanged" -> notificationOrchestrationService.handleTicketStatusChanged(
                recipientId = envelope.payload.requesterId ?: "",
                ticketId = envelope.payload.ticketId, fromStatus = envelope.payload.fromStatus,
                toStatus = envelope.payload.toStatus,
            )
            "TicketEscalated" -> notificationOrchestrationService.handleTicketEscalated(
                recipientId = envelope.payload.requesterId ?: "",
                ticketId = envelope.payload.ticketId, escalatedTo = envelope.payload.escalatedTo,
                newPriority = envelope.payload.newPriority,
            )
            "TicketResolved" -> notificationOrchestrationService.handleTicketResolved(
                recipientId = envelope.payload.requesterId ?: "",
                ticketId = envelope.payload.ticketId, resolution = envelope.payload.resolution,
            )
            "TicketClosed" -> notificationOrchestrationService.handleTicketClosed(
                recipientId = envelope.payload.requesterId ?: "", ticketId = envelope.payload.ticketId,
            )
            "TicketReopened" -> notificationOrchestrationService.handleTicketReopened(
                recipientId = envelope.payload.requesterId ?: "", ticketId = envelope.payload.ticketId,
            )
            "SlaBreached" -> notificationOrchestrationService.handleSlaBreached(
                ticketId = envelope.payload.ticketId ?: "",
                slaDeadline = envelope.payload.slaDeadline?.toInstant().toString(),
                breachDurationSeconds = envelope.payload.breachDurationSeconds,
            )
            "CsatSubmitted" -> notificationOrchestrationService.handleCsatSubmitted(
                recipientId = envelope.payload.customerId ?: "",
                ticketId = envelope.payload.ticketId, score = envelope.payload.score,
            )
            else -> log.debugf("Ignoring unhandled Support event type: %s", envelope.eventType)
        }
        eventLogRepository.markProcessed(idempotencyKey, envelope.eventType, "support", entityId)
    }

    @Incoming("marketing-events")
    @Blocking
    @Transactional
    fun consumeMarketingEvents(envelope: DomainEventEnvelope) {
        val entityId = envelope.payload.campaignId ?: envelope.payload.entityId ?: "unknown"
        val idempotencyKey = buildIdempotencyKey("marketing", envelope.eventType, entityId)
        if (eventLogRepository.isProcessed(idempotencyKey)) { return }

        when (envelope.eventType) {
            "CampaignLaunched" -> notificationOrchestrationService.handleCampaignLaunched(
                campaignId = envelope.payload.campaignId, name = envelope.payload.campaignName,
            )
            "CampaignPaused" -> notificationOrchestrationService.handleCampaignPaused(
                campaignId = envelope.payload.campaignId,
            )
            "CampaignCompleted" -> notificationOrchestrationService.handleCampaignCompleted(
                campaignId = envelope.payload.campaignId,
            )
            "RevenueAttributedToCampaign" -> notificationOrchestrationService.handleRevenueAttributed(
                campaignId = envelope.payload.campaignId,
                opportunityId = envelope.payload.opportunityId,
                amount = envelope.payload.attributedRevenue,
            )
            "ConversionDeclared" -> notificationOrchestrationService.handleConversionDeclared(
                campaignId = envelope.payload.campaignId, customerId = envelope.payload.customerId,
                revenue = envelope.payload.conversionRevenue,
            )
            else -> log.debugf("Ignoring unhandled Marketing event type: %s", envelope.eventType)
        }
        eventLogRepository.markProcessed(idempotencyKey, envelope.eventType, "marketing", entityId)
    }

    @Incoming("sales-pipeline-events")
    @Blocking
    @Transactional
    fun consumeSalesEvents(envelope: DomainEventEnvelope) {
        val entityId = envelope.payload.opportunityId ?: envelope.payload.entityId ?: "unknown"
        val idempotencyKey = buildIdempotencyKey("sales", envelope.eventType, entityId)
        if (eventLogRepository.isProcessed(idempotencyKey)) { return }

        when (envelope.eventType) {
            "OpportunityCreated" -> notificationOrchestrationService.handleOpportunityCreated(
                recipientId = envelope.payload.customerId ?: "",
                opportunityId = envelope.payload.opportunityId, name = envelope.payload.campaignName,
            )
            "OpportunityClosed" -> {
                val isWon = envelope.payload.isWon ?: (envelope.payload.outcome == "won")
                notificationOrchestrationService.handleOpportunityClosed(
                    recipientId = envelope.payload.customerId ?: "",
                    opportunityId = envelope.payload.opportunityId, isWon = isWon,
                    totalAmount = envelope.payload.totalAmount,
                )
            }
            "QuoteGenerated" -> notificationOrchestrationService.handleQuoteGenerated(
                recipientId = envelope.payload.customerId ?: "",
                opportunityId = envelope.payload.opportunityId, totalAmount = envelope.payload.totalAmount,
            )
            "QuoteSent" -> notificationOrchestrationService.handleQuoteSent(
                recipientId = envelope.payload.customerId ?: "",
                opportunityId = envelope.payload.opportunityId,
            )
            "QuoteAccepted" -> notificationOrchestrationService.handleQuoteAccepted(
                recipientId = envelope.payload.customerId ?: "",
                opportunityId = envelope.payload.opportunityId,
            )
            "ForecastUpdated" -> notificationOrchestrationService.handleForecastUpdated(
                period = envelope.payload.period, projectedRevenue = envelope.payload.projectedRevenue,
            )
            "OwnerReassigned" -> notificationOrchestrationService.handleOwnerReassigned(
                recipientId = envelope.payload.newOwnerId,
                opportunityId = envelope.payload.opportunityId,
                previousOwnerId = envelope.payload.previousOwnerId,
            )
            else -> log.debugf("Ignoring unhandled Sales event type: %s", envelope.eventType)
        }
        eventLogRepository.markProcessed(idempotencyKey, envelope.eventType, "sales", entityId)
    }

    private fun buildIdempotencyKey(source: String, eventType: String, entityId: String): UUID =
        UUID.nameUUIDFromBytes("$source:$eventType:$entityId".toByteArray())
}
