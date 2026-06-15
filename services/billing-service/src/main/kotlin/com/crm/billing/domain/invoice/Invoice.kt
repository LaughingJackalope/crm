package com.crm.billing.domain.invoice

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Standard financial rounding mode used across all monetary calculations.
 * HALF_UP is the most common commercial rounding: 0.5 rounds away from zero.
 * All BigDecimal operations in this aggregate use this mode with SCALE_2.
 */
object FinancialDefaults {
    val ROUNDING_MODE: RoundingMode = RoundingMode.HALF_UP
    val MONETARY_SCALE: Int = 2
    val TAX_SCALE: Int = 4
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(MONETARY_SCALE)
}

/**
 * Invoice Aggregate Root.
 *
 * Encapsulates all invoicing logic including tax calculation, line item
 * management, and state transitions. All monetary calculations use
 * [BigDecimal] with [FinancialDefaults.ROUNDING_MODE] to avoid
 * rounding discrepancies.
 *
 * ## Invariants
 * - Subtotal is the sum of all line item totals (quantity × unit price).
 * - Tax is computed per-line-item and summed, not on the subtotal.
 * - Total = subtotal + totalTax.
 * - Currency must be a valid ISO 4217 3-letter code.
 * - An issued or paid invoice cannot have line items added.
 */
data class Invoice(
    val invoiceId: UUID = UUID.randomUUID(),
    val opportunityId: String,
    val customerId: String,
    val accountId: String? = null,
    val invoiceNumber: String,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val issueDate: LocalDate = LocalDate.now(),
    val dueDate: LocalDate,
    val currency: String = "USD",
    val lineItems: List<InvoiceLineItem> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    init {
        require(currency.matches(Regex("^[A-Z]{3}$"))) {
            "Currency must be ISO 4217, got: $currency"
        }
        require(dueDate.isAfter(issueDate) || dueDate.isEqual(issueDate)) {
            "Due date must be on or after issue date"
        }
        require(invoiceNumber.isNotBlank()) {
            "Invoice number must not be blank"
        }
    }

    /**
     * Sum of all line item totals (quantity × unit price), rounded to 2 decimal places.
     */
    val subtotal: BigDecimal
        get() = lineItems
            .fold(FinancialDefaults.ZERO) { acc, item ->
                acc.add(item.total)
            }
            .setScale(FinancialDefaults.MONETARY_SCALE, FinancialDefaults.ROUNDING_MODE)

    /**
     * Sum of all per-line-item tax amounts, rounded to 2 decimal places.
     * Tax is computed per line item to avoid rounding at the subtotal level.
     */
    val totalTax: BigDecimal
        get() = lineItems
            .fold(FinancialDefaults.ZERO) { acc, item ->
                acc.add(item.taxRounded)
            }
            .setScale(FinancialDefaults.MONETARY_SCALE, FinancialDefaults.ROUNDING_MODE)

    /**
     * Total amount due: subtotal + totalTax.
     */
    val total: BigDecimal
        get() = subtotal.add(totalTax)

    fun addLineItem(item: InvoiceLineItem): Invoice {
        check(status == InvoiceStatus.DRAFT) {
            "Cannot add line items to an invoice in status $status"
        }
        return copy(
            lineItems = lineItems + item,
            updatedAt = Instant.now(),
        )
    }

    fun finalize(): Invoice {
        check(status == InvoiceStatus.DRAFT) {
            "Only DRAFT invoices can be finalized"
        }
        check(lineItems.isNotEmpty()) {
            "Cannot finalize an invoice with no line items"
        }
        return copy(status = InvoiceStatus.ISSUED, updatedAt = Instant.now())
    }

    fun markPaid(): Invoice {
        check(status == InvoiceStatus.ISSUED || status == InvoiceStatus.OVERDUE) {
            "Only ISSUED or OVERDUE invoices can be marked paid, got: $status"
        }
        return copy(status = InvoiceStatus.PAID, updatedAt = Instant.now())
    }

    fun markOverdue(): Invoice {
        check(status == InvoiceStatus.ISSUED) {
            "Only ISSUED invoices can be marked overdue, got: $status"
        }
        return copy(status = InvoiceStatus.OVERDUE, updatedAt = Instant.now())
    }

    fun void(reason: String? = null): Invoice {
        check(status != InvoiceStatus.PAID) {
            "Cannot void a paid invoice"
        }
        check(status != InvoiceStatus.VOID) {
            "Invoice is already void"
        }
        return copy(status = InvoiceStatus.VOID, updatedAt = Instant.now())
    }
}

/**
 * A single line item on an invoice.
 *
 * All monetary values use BigDecimal. [total] and [taxRounded] apply
 * [FinancialDefaults.HALF_UP] rounding at each step to avoid cumulative
 * rounding errors.
 */
data class InvoiceLineItem(
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal = FinancialDefaults.ZERO,
) {
    init {
        require(quantity > BigDecimal.ZERO) { "Quantity must be positive, got: $quantity" }
        require(unitPrice >= BigDecimal.ZERO) { "Unit price must be non-negative, got: $unitPrice" }
        require(taxRate >= BigDecimal.ZERO) { "Tax rate must be non-negative, got: $taxRate" }
    }

    /**
     * Line item total: quantity × unitPrice, rounded to 2 decimal places.
     */
    val total: BigDecimal
        get() = quantity
            .multiply(unitPrice)
            .setScale(FinancialDefaults.MONETARY_SCALE, FinancialDefaults.ROUNDING_MODE)

    /**
     * Tax amount for this line item: total × (taxRate / 100).
     * Calculated at TAX_SCALE precision, then rounded to MONETARY_SCALE for summation.
     */
    val taxRaw: BigDecimal
        get() = total
            .multiply(taxRate.divide(BigDecimal(100), FinancialDefaults.TAX_SCALE, FinancialDefaults.ROUNDING_MODE))
            .setScale(FinancialDefaults.TAX_SCALE, FinancialDefaults.ROUNDING_MODE)

    /**
     * Tax amount rounded to monetary scale for summation into totalTax.
     */
    val taxRounded: BigDecimal
        get() = taxRaw.setScale(FinancialDefaults.MONETARY_SCALE, FinancialDefaults.ROUNDING_MODE)
}

enum class InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    OVERDUE,
    VOID,
}
