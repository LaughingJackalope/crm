package com.crm.billing.application

import com.crm.billing.domain.event.BillingDomainEvent
import com.crm.billing.domain.event.BillingDomainEvent.InvoiceGenerated
import com.crm.billing.domain.invoice.FinancialDefaults
import com.crm.billing.domain.invoice.Invoice
import com.crm.billing.domain.invoice.InvoiceLineItem
import com.crm.billing.domain.invoice.InvoiceStatus
import com.crm.billing.infrastructure.persistence.InvoiceRepository
import com.crm.billing.infrastructure.persistence.OutboxEventEntity
import com.crm.billing.infrastructure.persistence.OutboxEventRepository
import com.crm.common.messaging.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Application service — orchestrates invoice creation from opportunity events.
 *
 * ## Transaction boundary
 * The [createInvoice] method is @Transactional. Within a single transaction:
 * 1. Check for duplicate invoices (idempotency at the domain level).
 * 2. Create the Invoice aggregate with line items and tax calculations.
 * 3. Save the Invoice to PostgreSQL.
 * 4. Write an InvoiceGenerated event to the outbox_event table.
 *
 * The transaction commits atomically — either both the invoice and the outbox
 * event are persisted, or neither is. The OutboxRelay handles the eventual
 * Kafka publish.
 */
@ApplicationScoped
class InvoiceOrchestrationService @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val outboxRepository: OutboxEventRepository,
) {

    /**
     * Create an invoice for a won opportunity.
     *
     * @param opportunityId The won opportunity ID from the Sales context.
     * @param customerId The customer ID (resolved from the opportunity).
     * @param invoiceNumber Human-readable invoice number.
     * @param dueDate When payment is due.
     * @return The created Invoice aggregate.
     * @throws com.crm.billing.domain.BillingDomainException.DuplicateInvoice if
     *         an invoice already exists for this opportunity.
     */
    @Transactional
    fun createInvoice(
        opportunityId: String,
        customerId: String,
        invoiceNumber: String,
        dueDate: LocalDate,
    ): Invoice {
        // Domain-level idempotency: check for existing invoice
        if (invoiceRepository.existsByOpportunityId(opportunityId)) {
            throw com.crm.billing.domain.BillingDomainException.DuplicateInvoice(opportunityId)
        }

        // Build the invoice aggregate with a default line item representing
        // the won opportunity value. In production, this would pull the
        // opportunity amount and line items from the Sales read model.
        val invoice = Invoice(
            opportunityId = opportunityId,
            customerId = customerId,
            invoiceNumber = invoiceNumber,
            dueDate = dueDate,
            currency = "USD",
        ).addLineItem(
            InvoiceLineItem(
                description = "Services rendered — Opportunity $opportunityId",
                quantity = BigDecimal.ONE,
                unitPrice = BigDecimal("999.99"), // Would come from opportunity amount
                taxRate = BigDecimal("8.25"),      // Would come from tax service
            )
        ).finalize() // Transition from DRAFT → ISSUED

        // Save the aggregate
        val saved = invoiceRepository.save(invoice)

        // Write domain event to outbox (same TX as aggregate save)
        val event = InvoiceGenerated(
            entityId = saved.invoiceId.toString(),
            invoiceId = saved.invoiceId,
            opportunityId = opportunityId,
            customerId = customerId,
            subscriptionId = null,
            totalAmount = "${saved.total} ${saved.currency}",
            currency = saved.currency,
            dueDate = dueDate,
            generatedAt = Instant.now(),
        )

        outboxRepository.save(
            OutboxEventEntity().apply {
                eventId = UUID.randomUUID()
                entityId = saved.invoiceId.toString()
                entityType = "invoice"
                eventType = "InvoiceGenerated"
                source = "billing"
                this@apply.payload = EventEnvelope(
                    eventType = "InvoiceGenerated",
                    source = "billing",
                    correlationId = null,
                    actorId = null,
                    payload = event,
                ).toJson()
            }
        )

        return saved
    }
}

private fun EventEnvelope<*>.toJson(): String {
    val payload = this.payload
    val payloadJson = when (payload) {
        is BillingDomainEvent -> payload.toJson()
        else -> payload.toString()
    }
    return """{"eventType":"$eventType","source":"$source","payload":$payloadJson}"""
}

private fun BillingDomainEvent.toJson(): String = when (this) {
    is InvoiceGenerated ->
        """{"invoiceId":"$invoiceId","opportunityId":"$opportunityId","customerId":"$customerId","totalAmount":"$totalAmount","currency":"$currency","dueDate":"$dueDate","generatedAt":"$generatedAt"}"""
    is BillingDomainEvent.InvoiceIssued ->
        """{"invoiceId":"$invoiceId","customerId":"$customerId","issuedAt":"$issuedAt"}"""
    is BillingDomainEvent.PaymentSucceeded ->
        """{"paymentId":"$paymentId","invoiceId":"$invoiceId","amount":"$amount","processedAt":"$processedAt"}"""
    is BillingDomainEvent.PaymentFailed ->
        """{"paymentId":"$paymentId","invoiceId":"$invoiceId","reason":"$reason","failedAt":"$failedAt"}"""
    is BillingDomainEvent.SubscriptionCreated ->
        """{"subscriptionId":"$subscriptionId","customerId":"$customerId","planId":"$planId","createdAt":"$createdAt"}"""
    is BillingDomainEvent.SubscriptionCancelled ->
        """{"subscriptionId":"$subscriptionId","customerId":"$customerId","reason":"${reason ?: ""}","cancelledAt":"$cancelledAt"}"""
}
