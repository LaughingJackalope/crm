package com.crm.billing.domain.invoice

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Invoice(
    val invoiceId: UUID = UUID.randomUUID(),
    val customerId: String,          // Reference to CIAM
    val accountId: String? = null,
    val invoiceNumber: String,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val issueDate: LocalDate = LocalDate.now(),
    val dueDate: LocalDate,
    val lineItems: MutableList<InvoiceLineItem> = mutableListOf(),
    val currency: String = "USD",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    val subtotal: BigDecimal
        get() = lineItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.total) }

    val totalTax: BigDecimal
        get() = lineItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.taxAmount) }

    val total: BigDecimal get() = subtotal.add(totalTax)

    fun finalize(): Invoice = copy(status = InvoiceStatus.ISSUED, updatedAt = Instant.now())
    fun markPaid(): Invoice = copy(status = InvoiceStatus.PAID, updatedAt = Instant.now())
    fun void(reason: String): Invoice = copy(status = InvoiceStatus.VOID, updatedAt = Instant.now())
    fun addLineItem(item: InvoiceLineItem): Invoice {
        lineItems.add(item)
        return copy(updatedAt = Instant.now())
    }
}

data class InvoiceLineItem(
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal = BigDecimal.ZERO,
) {
    val total: BigDecimal get() = unitPrice.multiply(quantity)
    val taxAmount: BigDecimal get() = total.multiply(taxRate.divide(BigDecimal(100)))
}

enum class InvoiceStatus { DRAFT, ISSUED, PAID, OVERDUE, VOID }
