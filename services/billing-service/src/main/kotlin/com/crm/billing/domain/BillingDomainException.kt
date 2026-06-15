package com.crm.billing.domain

/**
 * Sealed exception hierarchy for Billing domain errors.
 */
sealed class BillingDomainException(
    override val message: String,
) : RuntimeException(message) {

    class InvalidInvoiceState(
        val invoiceId: String,
        val currentStatus: String,
        val requiredStatus: String,
    ) : BillingDomainException(
        "Invoice $invoiceId is in status $currentStatus, required: $requiredStatus"
    )

    class DuplicateInvoice(
        val opportunityId: String,
    ) : BillingDomainException(
        "An invoice already exists for opportunity $opportunityId"
    )

    class InvalidLineItem(
        val reason: String,
    ) : BillingDomainException("Invalid invoice line item: $reason")

    class TaxCalculationError(
        val detail: String,
    ) : BillingDomainException("Tax calculation failed: $detail")

    /**
     * Thrown by the consumer when event processing fails fatally.
     * The message is captured by SmallRye's dead-letter mechanism
     * in the dead-letter-reason header on the DLQ message.
     */
    class ConsumerProcessingException(
        override val message: String,
        override val cause: Throwable? = null,
    ) : BillingDomainException(message)
}
