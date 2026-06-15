package com.crm.sales.domain

/**
 * Sealed exception hierarchy for Sales domain errors.
 */
sealed class SalesDomainException(
    override val message: String,
) : RuntimeException(message) {

    /**
     * Thrown when attempting to advance a closed opportunity.
     */
    class OpportunityClosed(
        val opportunityId: String,
        val stage: String,
    ) : SalesDomainException(
        "Opportunity $opportunityId is in closed stage $stage and cannot be advanced."
    )

    /**
     * Thrown when attempting to close an already-closed opportunity.
     */
    class OpportunityAlreadyClosed(
        val opportunityId: String,
    ) : SalesDomainException(
        "Opportunity $opportunityId is already closed."
    )

    /**
     * Thrown when a quote operation references a non-DRAFT quote.
     */
    class QuoteNotDraft(
        val quoteId: String,
        val status: String,
    ) : SalesDomainException(
        "Quote $quoteId is in status $status, expected DRAFT."
    )

    /**
     * Thrown when attempting to accept a quote that is not in SENT status.
     */
    class QuoteNotSent(
        val quoteId: String,
        val status: String,
    ) : SalesDomainException(
        "Quote $quoteId is in status $status, expected SENT for acceptance."
    )
}
