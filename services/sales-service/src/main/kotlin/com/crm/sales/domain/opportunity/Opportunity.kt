package com.crm.sales.domain.opportunity

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Opportunity Aggregate Root.
 * References customers by ID only — never owns customer data.
 */
data class Opportunity(
    val opportunityId: UUID = UUID.randomUUID(),
    val customerId: String,          // Reference to CIAM — not a foreign key
    val accountId: String? = null,   // Reference to CIAM Account
    val name: String,
    val stage: SalesStage = SalesStage.PROSPECTING,
    val amount: Money,
    val probability: Int = 0,        // 0-100
    val expectedCloseDate: LocalDate? = null,
    val ownerId: String? = null,     // Sales Rep ID
    val winLossReason: WinLossReason? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val quotes: MutableList<Quote> = mutableListOf(),
) {
    init { require(probability in 0..100) { "Probability must be 0-100" } }

    fun advanceStage(): Opportunity {
        val next = stage.next() ?: throw IllegalStateException("Cannot advance from $stage")
        return copy(stage = next, probability = next.defaultProbability, updatedAt = Instant.now())
    }

    fun close(isWon: Boolean, reason: WinLossReason? = null): Opportunity {
        val closedStage = if (isWon) SalesStage.CLOSED_WON else SalesStage.CLOSED_LOST
        return copy(stage = closedStage, winLossReason = reason, updatedAt = Instant.now())
    }

    fun reassignOwner(newOwnerId: String): Opportunity =
        copy(ownerId = newOwnerId, updatedAt = Instant.now())

    fun addQuote(quote: Quote): Opportunity {
        quotes.add(quote)
        return copy(updatedAt = Instant.now())
    }
}

data class Quote(
    val quoteId: UUID = UUID.randomUUID(),
    val opportunityId: UUID,
    val status: QuoteStatus = QuoteStatus.DRAFT,
    val lineItems: MutableList<LineItem> = mutableListOf(),
    val validUntil: LocalDate? = null,
    val createdAt: Instant = Instant.now(),
) {
    val totalAmount: Money
        get() = lineItems.fold(Money(BigDecimal.ZERO, "USD")) { acc, item ->
            Money(acc.value.add(item.totalPrice.value), acc.currency)
        }
}

data class LineItem(
    val productId: String,       // Reference to Product Catalog
    val productName: String,
    val quantity: Int,
    val unitPrice: Money,
    val discountPercent: BigDecimal = BigDecimal.ZERO,
) {
    val totalPrice: Money
        get() {
            val discount = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal(100)))
            return Money(unitPrice.value.multiply(BigDecimal(quantity)).multiply(discount), unitPrice.currency)
        }
}

data class Money(
    val value: BigDecimal,
    val currency: String = "USD",
) {
    init {
        require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must be ISO 4217" }
    }
}

enum class SalesStage(val defaultProbability: Int, val isClosed: Boolean = false, val isWon: Boolean = false) {
    PROSPECTING(10),
    DISCOVERY(25),
    PROPOSAL(50),
    NEGOTIATION(75),
    CLOSED_WON(100, isClosed = true, isWon = true),
    CLOSED_LOST(0, isClosed = true);

    fun next(): SalesStage? = when (this) {
        PROSPECTING -> DISCOVERY
        DISCOVERY -> PROPOSAL
        PROPOSAL -> NEGOTIATION
        NEGOTIATION -> CLOSED_WON
        else -> null
    }
}

enum class QuoteStatus { DRAFT, SENT, ACCEPTED, REJECTED }

data class WinLossReason(
    val category: String,
    val detail: String? = null,
)
