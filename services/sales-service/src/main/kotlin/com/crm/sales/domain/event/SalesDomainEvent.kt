package com.crm.sales.domain.event

import com.crm.common.messaging.Identifiable
import java.time.Instant

/**
 * Sealed hierarchy of all domain events emitted by the Sales bounded context.
 */
sealed interface SalesDomainEvent : Identifiable {

    /**
     * Emitted when a new opportunity is opened.
     */
    data class OpportunityCreated(
        override val entityId: String,
        val customerId: String,
        val name: String,
        val amount: String,
        val createdAt: Instant,
    ) : SalesDomainEvent

    /**
     * Emitted when an opportunity advances to the next pipeline stage.
     */
    data class OpportunityStageAdvanced(
        override val entityId: String,
        val fromStage: String,
        val toStage: String,
        val advancedAt: Instant,
    ) : SalesDomainEvent

    /**
     * Emitted when an opportunity is closed (won or lost).
     */
    data class OpportunityClosed(
        override val entityId: String,
        val isWon: Boolean,
        val reason: String?,
        val closedAt: Instant,
    ) : SalesDomainEvent

    /**
     * Emitted when a quote is generated for an opportunity.
     */
    data class QuoteGenerated(
        override val entityId: String,
        val opportunityId: String,
        val totalAmount: String,
        val generatedAt: Instant,
    ) : SalesDomainEvent

    /**
     * Emitted when a quote is sent to the customer.
     */
    data class QuoteSent(
        override val entityId: String,
        val opportunityId: String,
        val sentAt: Instant,
    ) : SalesDomainEvent

    /**
     * Emitted when a customer accepts a quote.
     */
    data class QuoteAccepted(
        override val entityId: String,
        val opportunityId: String,
        val acceptedAt: Instant,
    ) : SalesDomainEvent

    /**
     * Emitted when the pipeline forecast is recalculated.
     */
    data class ForecastUpdated(
        override val entityId: String,
        val period: String,
        val projectedRevenue: String,
        val updatedAt: Instant,
    ) : SalesDomainEvent

    /**
     * Emitted when an opportunity's sales rep changes.
     */
    data class OwnerReassigned(
        override val entityId: String,
        val previousOwnerId: String?,
        val newOwnerId: String,
        val reassignedAt: Instant,
    ) : SalesDomainEvent
}
