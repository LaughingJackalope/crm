package com.crm.billing.infrastructure.rest

import com.crm.billing.domain.invoice.Invoice
import com.crm.billing.domain.invoice.InvoiceLineItem
import com.crm.billing.domain.invoice.InvoiceStatus
import com.crm.billing.infrastructure.persistence.InvoiceRepository
import io.quarkus.qute.Template
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Serves Qute-rendered HTML pages for the Billing / Invoices web UI.
 *
 * These endpoints return full HTML pages for browser navigation, while the
 * underlying REST API endpoints (in [InvoiceResource]) serve JSON for htmx
 * fragments and programmatic consumers.
 *
 * ## Routing convention
 * - `GET /billing/invoices` → full page with invoice list
 * - `GET /billing/invoices?page=2&size=25&q=acme&status=DRAFT` → same page, filtered
 * - `GET /billing/invoices/{id}` → invoice detail page
 */
@Path("/billing")
class BillingWebResource @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
) {

    @Inject
    @io.quarkus.qute.Location("invoices/list.html")
    lateinit var listTemplate: Template

    @Inject
    @io.quarkus.qute.Location("invoices/detail.html")
    lateinit var detailTemplate: Template

    // ── Invoice list ────────────────────────────────────────────────────────

    @GET
    @Path("/invoices")
    @Produces(MediaType.TEXT_HTML)
    fun listInvoices(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("size") @DefaultValue("25") size: Int,
        @QueryParam("q") q: String? = null,
        @QueryParam("status") statusFilter: String? = null,
    ): Response {
        val status = statusFilter?.let { runCatching { InvoiceStatus.valueOf(it) }.getOrNull() }

        // Fetch from repository — all if no status filter, otherwise by status
        val all = if (status != null) {
            invoiceRepository.findByStatus(status)
        } else {
            invoiceRepository.findAll()
        }

        // Apply search filter
        val filtered = all.filter { invoice ->
            if (q.isNullOrBlank()) true
            else invoice.invoiceNumber.contains(q, ignoreCase = true) ||
                invoice.customerId.contains(q, ignoreCase = true) ||
                invoice.invoiceId.toString().contains(q, ignoreCase = true)
        }

        // Sort by createdAt descending (most recent first)
        val sorted = filtered.sortedByDescending { it.createdAt }

        // Paginate in-memory
        val total = sorted.size
        val totalPages = if (total == 0) 1 else kotlin.math.ceil(total.toDouble() / size).toInt()
        val safePage = page.coerceIn(1, totalPages)
        val offset = (safePage - 1) * size
        val pageItems = sorted.drop(offset).take(size)

        // Build page numbers for pagination (show up to 5 pages around current)
        val pages = buildPageRange(safePage, totalPages)

        // Build status info list for the template
        val statusInfos = InvoiceStatus.entries.map { s ->
            mapOf("name" to s.name, "label" to invoiceStatusLabel(s))
        }

        val html = listTemplate
            .data("invoices", pageItems.map { it.toWebInvoice() })
            .data("page", safePage)
            .data("size", size)
            .data("totalPages", totalPages)
            .data("total", total)
            .data("pages", pages)
            .data("q", q)
            .data("selectedStatus", statusFilter)
            .data("statuses", statusInfos)
            .data("activePage", "invoices")
            .render()

        return Response.ok(html).build()
    }

    // ── Invoice detail ──────────────────────────────────────────────────────

    @GET
    @Path("/invoices/{id}")
    @Produces(MediaType.TEXT_HTML)
    fun getInvoice(@PathParam("id") id: java.util.UUID): Response {
        val invoice = invoiceRepository.findById(id)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity("<html><body><h1>Invoice not found</h1><a href='/billing/invoices'>← Back</a></body></html>")
                .build()

        val html = detailTemplate
            .data("invoice", invoice.toWebInvoiceDetail())
            .data("activePage", "invoices")
            .render()

        return Response.ok(html).build()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildPageRange(current: Int, total: Int): List<Int> {
        if (total <= 7) return (1..total).toList()
        val start = maxOf(1, current - 2)
        val end = minOf(total, current + 2)
        return (start..end).toList()
    }
}

// ── Web-facing DTOs (flattened for template consumption) ────────────────────

data class WebInvoice(
    val id: String,
    val invoiceNumber: String,
    val status: InvoiceStatus,
    val statusLabel: String,
    val customerId: String,
    val total: String,
    val currency: String,
    val issueDate: String,
    val dueDate: String,
    val isOverdue: Boolean,
    val createdAt: String,
)

/**
 * Extended web DTO for the detail page, including line items and financial summary.
 */
data class WebInvoiceDetail(
    val id: String,
    val invoiceNumber: String,
    val status: InvoiceStatus,
    val statusLabel: String,
    val customerId: String,
    val total: String,
    val subtotal: String,
    val totalTax: String,
    val currency: String,
    val issueDate: String,
    val dueDate: String,
    val isOverdue: Boolean,
    val createdAt: String,
    val lineItems: List<WebInvoiceLineItem>,
)

data class WebInvoiceLineItem(
    val description: String,
    val quantity: String,
    val unitPrice: String,
    val taxRate: String,
    val total: String,
)

// ── Domain → Web DTO mapping ─────────────────────────────────────────────────

private fun Invoice.toWebInvoice(): WebInvoice {
    val today = LocalDate.now()
    val isOverdue = status != InvoiceStatus.PAID &&
        status != InvoiceStatus.VOID &&
        dueDate.isBefore(today)
    return WebInvoice(
        id = invoiceId.toString(),
        invoiceNumber = invoiceNumber,
        status = status,
        statusLabel = invoiceStatusLabel(status),
        customerId = customerId,
        total = "$total $currency",
        currency = currency,
        issueDate = issueDate.toString(),
        dueDate = dueDate.toString(),
        isOverdue = isOverdue,
        createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt),
    )
}

private fun Invoice.toWebInvoiceDetail(): WebInvoiceDetail {
    val today = LocalDate.now()
    val isOverdue = status != InvoiceStatus.PAID &&
        status != InvoiceStatus.VOID &&
        dueDate.isBefore(today)
    return WebInvoiceDetail(
        id = invoiceId.toString(),
        invoiceNumber = invoiceNumber,
        status = status,
        statusLabel = invoiceStatusLabel(status),
        customerId = customerId,
        total = total.toPlainString(),
        subtotal = subtotal.toPlainString(),
        totalTax = totalTax.toPlainString(),
        currency = currency,
        issueDate = issueDate.toString(),
        dueDate = dueDate.toString(),
        isOverdue = isOverdue,
        createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt),
        lineItems = lineItems.map { it.toWebLineItem() },
    )
}

private fun InvoiceLineItem.toWebLineItem(): WebInvoiceLineItem =
    WebInvoiceLineItem(
        description = description,
        quantity = quantity.toPlainString(),
        unitPrice = unitPrice.toPlainString(),
        taxRate = taxRate.toPlainString(),
        total = total.toPlainString(),
    )

// ── Label helpers ─────────────────────────────────────────────────────────────

fun invoiceStatusLabel(status: InvoiceStatus): String = when (status) {
    InvoiceStatus.DRAFT -> "Draft"
    InvoiceStatus.ISSUED -> "Issued"
    InvoiceStatus.PAID -> "Paid"
    InvoiceStatus.VOID -> "Void"
    InvoiceStatus.OVERDUE -> "Overdue"
}
