package com.crm.billing.infrastructure.persistence

import com.crm.billing.domain.invoice.Invoice
import com.crm.billing.domain.invoice.InvoiceLineItem
import com.crm.billing.domain.invoice.InvoiceStatus
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA entity for Invoice — maps to the `crm_billing.invoice` table.
 */
@Entity
@Table(name = "invoice", schema = "billing")
class InvoiceEntity : PanacheEntityBase {

    @Id
    @Column(name = "invoice_id", nullable = false)
    lateinit var invoiceId: UUID

    @Column(name = "opportunity_id", nullable = false, length = 36)
    lateinit var opportunityId: String

    @Column(name = "customer_id", nullable = false, length = 36)
    lateinit var customerId: String

    @Column(name = "account_id", length = 36)
    var accountId: String? = null

    @Column(name = "invoice_number", nullable = false, length = 64)
    lateinit var invoiceNumber: String

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    lateinit var status: InvoiceStatus

    @Column(name = "issue_date", nullable = false)
    lateinit var issueDate: LocalDate

    @Column(name = "due_date", nullable = false)
    lateinit var dueDate: LocalDate

    @Column(name = "currency", nullable = false, length = 3)
    lateinit var currency: String

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    lateinit var subtotal: BigDecimal

    @Column(name = "total_tax", nullable = false, precision = 19, scale = 2)
    lateinit var totalTax: BigDecimal

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    lateinit var total: BigDecimal

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant

    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var lineItems: MutableList<InvoiceLineItemEntity> = mutableListOf()

    companion object : PanacheCompanion<InvoiceEntity> {
        fun findByOpportunityId(opportunityId: String): InvoiceEntity? =
            find("opportunityId", opportunityId).firstResult()

        fun findByCustomerId(customerId: String): List<InvoiceEntity> =
            list("customerId", customerId)

        fun findByStatus(status: InvoiceStatus): List<InvoiceEntity> =
            list("status", status)
    }
}

/**
 * JPA entity for InvoiceLineItem — maps to `crm_billing.invoice_line_item`.
 */
@Entity
@Table(name = "invoice_line_item", schema = "billing")
class InvoiceLineItemEntity : PanacheEntityBase {

    @Id
    @Column(name = "line_item_id", nullable = false)
    lateinit var lineItemId: UUID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    lateinit var invoice: InvoiceEntity

    @Column(name = "description", nullable = false, length = 500)
    lateinit var description: String

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    lateinit var quantity: BigDecimal

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    lateinit var unitPrice: BigDecimal

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    lateinit var taxRate: BigDecimal

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    lateinit var lineTotal: BigDecimal

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    lateinit var taxAmount: BigDecimal

    companion object : PanacheCompanion<InvoiceLineItemEntity>
}

// ── Mapping: Entity → Domain ──────────────────────────────────────────────────

fun InvoiceEntity.toDomain(): Invoice = Invoice(
    invoiceId = invoiceId,
    opportunityId = opportunityId,
    customerId = customerId,
    accountId = accountId,
    invoiceNumber = invoiceNumber,
    status = status,
    issueDate = issueDate,
    dueDate = dueDate,
    currency = currency,
    lineItems = lineItems.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun InvoiceLineItemEntity.toDomain(): InvoiceLineItem = InvoiceLineItem(
    description = description,
    quantity = quantity,
    unitPrice = unitPrice,
    taxRate = taxRate,
)

// ── Mapping: Domain → Entity ──────────────────────────────────────────────────

fun Invoice.toEntity(): InvoiceEntity = InvoiceEntity().apply {
    invoiceId = this@toEntity.invoiceId
    opportunityId = this@toEntity.opportunityId
    customerId = this@toEntity.customerId
    accountId = this@toEntity.accountId
    invoiceNumber = this@toEntity.invoiceNumber
    status = this@toEntity.status
    issueDate = this@toEntity.issueDate
    dueDate = this@toEntity.dueDate
    currency = this@toEntity.currency
    subtotal = this@toEntity.subtotal
    totalTax = this@toEntity.totalTax
    total = this@toEntity.total
    createdAt = this@toEntity.createdAt
    updatedAt = this@toEntity.updatedAt
    lineItems = this@toEntity.lineItems.map { it.toEntity(this) }.toMutableList()
}

fun InvoiceLineItem.toEntity(invoice: InvoiceEntity): InvoiceLineItemEntity =
    InvoiceLineItemEntity().apply {
        lineItemId = UUID.randomUUID()
        this.invoice = invoice
        description = this@toEntity.description
        quantity = this@toEntity.quantity
        unitPrice = this@toEntity.unitPrice
        taxRate = this@toEntity.taxRate
        lineTotal = this@toEntity.total
        taxAmount = this@toEntity.taxRounded
    }
