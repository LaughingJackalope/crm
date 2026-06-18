package com.crm.billing.infrastructure.persistence

import com.crm.billing.domain.invoice.Invoice
import com.crm.billing.domain.invoice.InvoiceStatus
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Panache-based repository for Invoice aggregates.
 */
@ApplicationScoped
class InvoiceRepository {

    fun findById(id: UUID): Invoice? =
        InvoiceEntity.find("invoiceId", id).firstResult()?.toDomain()

    fun findByOpportunityId(opportunityId: String): Invoice? =
        InvoiceEntity.findByOpportunityId(opportunityId)?.toDomain()

    fun findByCustomerId(customerId: String): List<Invoice> =
        InvoiceEntity.findByCustomerId(customerId).map { it.toDomain() }

    fun findByStatus(status: InvoiceStatus): List<Invoice> =
        InvoiceEntity.findByStatus(status).map { it.toDomain() }

    fun save(invoice: Invoice): Invoice {
        val existing = InvoiceEntity.find("invoiceId", invoice.invoiceId).firstResult()
        val entity = existing ?: InvoiceEntity()

        entity.apply {
            invoiceId = invoice.invoiceId
            opportunityId = invoice.opportunityId
            customerId = invoice.customerId
            accountId = invoice.accountId
            invoiceNumber = invoice.invoiceNumber
            status = invoice.status
            issueDate = invoice.issueDate
            dueDate = invoice.dueDate
            currency = invoice.currency
            subtotal = invoice.subtotal
            totalTax = invoice.totalTax
            total = invoice.total
            if (existing == null) createdAt = invoice.createdAt
            updatedAt = invoice.updatedAt
            // Sync line items
            if (existing != null) {
                // Remove line items no longer present
                val newIds = invoice.lineItems.mapNotNull { li ->
                    lineItems.find { it.description == li.description && it.unitPrice == li.unitPrice }?.lineItemId
                }.toSet()
                lineItems.removeAll { it.lineItemId !in newIds }
            }
            // This is simplified; a full implementation would diff line items
        }

        entity.persist()
        return entity.toDomain()
    }

    fun existsByOpportunityId(opportunityId: String): Boolean =
        InvoiceEntity.find("opportunityId", opportunityId).firstResult() != null

    fun findAll(): List<Invoice> =
        InvoiceEntity.listAll().map { it.toDomain() }

}
