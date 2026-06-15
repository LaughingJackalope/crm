package com.crm.billing.domain.event

import com.crm.common.messaging.Identifiable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Sealed hierarchy of all domain events emitted by the Billing bounded context.
 */
sealed interface BillingDomainEvent : Identifiable {

    data class InvoiceGenerated(
        override val entityId: String,
        val invoiceId: UUID,
        val opportunityId: String,
        val customerId: String,
        val subscriptionId: String? = null,
        val totalAmount: String,
        val currency: String,
        val dueDate: LocalDate,
        val generatedAt: Instant,
    ) : BillingDomainEvent

    data class InvoiceIssued(
        override val entityId: String,
        val invoiceId: UUID,
        val customerId: String,
        val issuedAt: Instant,
    ) : BillingDomainEvent

    data class PaymentSucceeded(
        override val entityId: String,
        val paymentId: UUID,
        val invoiceId: UUID,
        val amount: String,
        val processedAt: Instant,
    ) : BillingDomainEvent

    data class PaymentFailed(
        override val entityId: String,
        val paymentId: UUID,
        val invoiceId: UUID,
        val reason: String,
        val failedAt: Instant,
    ) : BillingDomainEvent

    data class SubscriptionCreated(
        override val entityId: String,
        val subscriptionId: UUID,
        val customerId: String,
        val planId: String,
        val createdAt: Instant,
    ) : BillingDomainEvent

    data class SubscriptionCancelled(
        override val entityId: String,
        val subscriptionId: UUID,
        val customerId: String,
        val reason: String?,
        val cancelledAt: Instant,
    ) : BillingDomainEvent
}
