package com.crm.sales.domain.opportunity

import com.crm.sales.domain.SalesDomainException
import com.crm.sales.domain.event.SalesDomainEvent
import com.crm.sales.domain.event.SalesDomainEvent.OpportunityStageAdvanced
import com.crm.sales.domain.event.SalesDomainEvent.QuoteGenerated
import com.crm.sales.domain.event.SalesDomainEvent.QuoteSent
import com.crm.sales.domain.event.SalesDomainEvent.QuoteAccepted
import com.crm.sales.domain.event.SalesDomainEvent.OwnerReassigned
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Domain result of a state-mutating operation on an [Opportunity] aggregate.
 *
 * Mirrors the pattern from the CIAM context: every command returns both the
 * new aggregate state and the domain event(s) produced.
 */
data class OpportunityResult(
    val aggregate: Opportunity,
    val events: List<SalesDomainEvent>,
)

fun Opportunity.withSalesEvent(event: SalesDomainEvent): OpportunityResult =
    OpportunityResult(this, listOf(event))

fun Opportunity.noSalesEvent(): OpportunityResult =
    OpportunityResult(this, emptyList())

/**
 * Opportunity Aggregate Root.
 *
 * Manages the sales pipeline from prospecting through close. References
 * customers by ID only — never owns customer data.
 *
 * ## Stage Progression
 *
 * Stages advance linearly: PROSPECTING → DISCOVERY → PROPOSAL → NEGOTIATION → CLOSED_WON.
 * Closing as lost can happen from any non-closed stage via [close].
 *
 * ## Invariants
 *
 * - Probability is always in [0, 100].
 * - A closed opportunity cannot be advanced or re-closed.
 * - Quotes can only be added to non-closed opportunities.
 */
data class Opportunity(
    val opportunityId: UUID = UUID.randomUUID(),
    val customerId: String,
    val accountId: String? = null,
    val name: String,
    val stage: SalesStage = SalesStage.PROSPECTING,
    val amount: Money,
    val probability: Int = 0,
    val expectedCloseDate: LocalDate? = null,
    val ownerId: String? = null,
    val winLossReason: WinLossReason? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val quotes: List<Quote> = emptyList(),
) {
    init {
        require(probability in 0..100) {
            "Probability must be 0-100, got $probability"
        }
    }

    // ── Commands ─────────────────────────────────────────────────────────

    /**
     * Advance the opportunity to the next pipeline stage.
     *
     * The next stage and its default probability are determined by
     * [SalesStage.next]. CLOSED_LOST cannot be advanced (it's a terminal sink).
     *
     * @return [OpportunityResult] with updated opportunity and [OpportunityStageAdvanced] event.
     * @throws SalesDomainException.OpportunityClosed if the opportunity is already closed.
     */
    fun advanceStage(): OpportunityResult {
        if (stage.isClosed) {
            throw SalesDomainException.OpportunityClosed(
                opportunityId = opportunityId.toString(),
                stage = stage.name,
            )
        }
        val nextStage = stage.next()
            ?: throw SalesDomainException.OpportunityClosed(
                opportunityId = opportunityId.toString(),
                stage = stage.name,
            )
        val fromStage = stage
        val updated = copy(
            stage = nextStage,
            probability = nextStage.defaultProbability,
            updatedAt = Instant.now(),
        )
        val event = OpportunityStageAdvanced(
            entityId = opportunityId.toString(),
            fromStage = fromStage.name,
            toStage = nextStage.name,
            advancedAt = updated.updatedAt,
        )
        return updated.withSalesEvent(event)
    }

    /**
     * Close the opportunity as won or lost.
     *
     * @param isWon true → CLOSED_WON, false → CLOSED_LOST.
     * @param reason Optional win/loss reason for analytics.
     * @return [OpportunityResult] with closed opportunity and [OpportunityClosed] event.
     * @throws SalesDomainException.OpportunityAlreadyClosed if already closed.
     */
    fun close(isWon: Boolean, reason: WinLossReason? = null): OpportunityResult {
        if (stage.isClosed) {
            throw SalesDomainException.OpportunityAlreadyClosed(opportunityId.toString())
        }
        val closedStage = if (isWon) SalesStage.CLOSED_WON else SalesStage.CLOSED_LOST
        val updated = copy(
            stage = closedStage,
            winLossReason = reason,
            updatedAt = Instant.now(),
        )
        val event = SalesDomainEvent.OpportunityClosed(
            entityId = opportunityId.toString(),
            isWon = isWon,
            reason = reason?.category,
            closedAt = updated.updatedAt,
        )
        return updated.withSalesEvent(event)
    }

    /**
     * Reassign the opportunity to a new sales rep.
     *
     * @return [OpportunityResult] with updated opportunity and [OwnerReassigned] event.
     */
    fun reassignOwner(newOwnerId: String): OpportunityResult {
        val previousOwner = ownerId
        val updated = copy(
            ownerId = newOwnerId,
            updatedAt = Instant.now(),
        )
        val event = OwnerReassigned(
            entityId = opportunityId.toString(),
            previousOwnerId = previousOwner,
            newOwnerId = newOwnerId,
            reassignedAt = updated.updatedAt,
        )
        return updated.withSalesEvent(event)
    }

    /**
     * Add a quote to this opportunity.
     *
     * @return [OpportunityResult] with the quote added and [QuoteGenerated] event.
     * @throws SalesDomainException.OpportunityClosed if the opportunity is closed.
     */
    fun addQuote(quote: Quote): OpportunityResult {
        if (stage.isClosed) {
            throw SalesDomainException.OpportunityClosed(
                opportunityId = opportunityId.toString(),
                stage = stage.name,
            )
        }
        val updated = copy(
            quotes = quotes + quote,
            updatedAt = Instant.now(),
        )
        val event = QuoteGenerated(
            entityId = quote.quoteId.toString(),
            opportunityId = opportunityId.toString(),
            totalAmount = "${quote.totalAmount.value} ${quote.totalAmount.currency}",
            generatedAt = quote.createdAt,
        )
        return updated.withSalesEvent(event)
    }

    /**
     * Mark a quote as sent to the customer.
     *
     * @return [OpportunityResult] with updated quote status and [QuoteSent] event.
     * @throws SalesDomainException.QuoteNotDraft if the quote is not in DRAFT status.
     */
    fun sendQuote(quoteId: UUID): OpportunityResult {
        val quote = quotes.find { it.quoteId == quoteId }
            ?: throw IllegalArgumentException("Quote $quoteId not found in opportunity $opportunityId")
        if (quote.status != QuoteStatus.DRAFT) {
            throw SalesDomainException.QuoteNotDraft(
                quoteId = quoteId.toString(),
                status = quote.status.name,
            )
        }
        val updatedQuote = quote.copy(status = QuoteStatus.SENT)
        val updatedQuotes = quotes.map { if (it.quoteId == quoteId) updatedQuote else it }
        val updated = copy(quotes = updatedQuotes, updatedAt = Instant.now())
        val event = QuoteSent(
            entityId = quoteId.toString(),
            opportunityId = opportunityId.toString(),
            sentAt = updated.updatedAt,
        )
        return updated.withSalesEvent(event)
    }

    /**
     * Mark a quote as accepted by the customer.
     *
     * @return [OpportunityResult] with updated quote status and [QuoteAccepted] event.
     * @throws SalesDomainException.QuoteNotSent if the quote is not in SENT status.
     */
    fun acceptQuote(quoteId: UUID): OpportunityResult {
        val quote = quotes.find { it.quoteId == quoteId }
            ?: throw IllegalArgumentException("Quote $quoteId not found in opportunity $opportunityId")
        if (quote.status != QuoteStatus.SENT) {
            throw SalesDomainException.QuoteNotSent(
                quoteId = quoteId.toString(),
                status = quote.status.name,
            )
        }
        val updatedQuote = quote.copy(status = QuoteStatus.ACCEPTED)
        val updatedQuotes = quotes.map { if (it.quoteId == quoteId) updatedQuote else it }
        val updated = copy(quotes = updatedQuotes, updatedAt = Instant.now())
        val event = QuoteAccepted(
            entityId = quoteId.toString(),
            opportunityId = opportunityId.toString(),
            acceptedAt = updated.updatedAt,
        )
        return updated.withSalesEvent(event)
    }
}

// ── Value Objects ────────────────────────────────────────────────────────────

data class Quote(
    val quoteId: UUID = UUID.randomUUID(),
    val opportunityId: UUID,
    val status: QuoteStatus = QuoteStatus.DRAFT,
    val lineItems: List<LineItem> = emptyList(),
    val validUntil: LocalDate? = null,
    val createdAt: Instant = Instant.now(),
) {
    val totalAmount: Money
        get() = lineItems.fold(Money(BigDecimal.ZERO, "USD")) { acc, item ->
            Money(acc.value.add(item.totalPrice.value), acc.currency)
        }
}

data class LineItem(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Money,
    val discountPercent: BigDecimal = BigDecimal.ZERO,
) {
    val totalPrice: Money
        get() {
            val discount = BigDecimal.ONE.subtract(
                discountPercent.divide(BigDecimal(100))
            )
            return Money(
                unitPrice.value.multiply(BigDecimal(quantity)).multiply(discount),
                unitPrice.currency,
            )
        }
}

data class Money(
    val value: BigDecimal,
    val currency: String = "USD",
) {
    init {
        require(currency.matches(Regex("[A-Z]{3}"))) {
            "Currency must be ISO 4217, got: $currency"
        }
    }
}

data class WinLossReason(
    val category: String,
    val detail: String? = null,
)

// ── Enums ────────────────────────────────────────────────────────────────────

enum class SalesStage(
    val defaultProbability: Int,
    val isClosed: Boolean = false,
    val isWon: Boolean = false,
) {
    PROSPECTING(10),
    DISCOVERY(25),
    PROPOSAL(50),
    NEGOTIATION(75),
    CLOSED_WON(100, isClosed = true, isWon = true),
    CLOSED_LOST(0, isClosed = true);

    /**
     * Return the next stage in the pipeline, or null if this is terminal.
     */
    fun next(): SalesStage? = when (this) {
        PROSPECTING -> DISCOVERY
        DISCOVERY -> PROPOSAL
        PROPOSAL -> NEGOTIATION
        NEGOTIATION -> CLOSED_WON
        else -> null
    }
}

enum class QuoteStatus {
    DRAFT, SENT, ACCEPTED, REJECTED
}
