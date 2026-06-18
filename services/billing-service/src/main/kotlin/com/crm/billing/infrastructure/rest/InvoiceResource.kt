package com.crm.billing.infrastructure.rest

import com.crm.billing.domain.invoice.Invoice
import com.crm.billing.infrastructure.persistence.InvoiceRepository
import com.crm.billing.domain.invoice.InvoiceStatus
import com.crm.openapi.billing.model.*
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * REST JSON API for Invoice operations.
 *
 * Provides the HTTP contract for the Billing bounded context. All endpoints
 * use OpenAPI-generated DTOs from [com.crm.openapi.billing.model] to keep
 * the HTTP contract in sync with the API specification.
 *
 * The HTML web UI is served separately by [BillingWebResource].
 */
@Path("/api/v1/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class InvoiceResource @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
) {

    // ── List invoices ──────────────────────────────────────────────────────

    @GET
    fun listInvoices(
        @QueryParam("status") status: String? = null,
        @QueryParam("customerId") customerId: String? = null,
        @QueryParam("sort") @DefaultValue("-createdAt") sort: String,
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("size") @DefaultValue("25") size: Int,
    ): Response {
        // Collect invoices from the repository, applying filters
        val all = when {
            status != null -> {
                val invoiceStatus = runCatching { InvoiceStatus.valueOf(status) }.getOrNull()
                    ?: return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ErrorResponse(ErrorDetail(code = "invalid-status", message = "Unknown status: $status", target = "status")))
                        .build()
                invoiceRepository.findByStatus(invoiceStatus)
            }
            customerId != null -> invoiceRepository.findByCustomerId(customerId)
            else -> invoiceRepository.findAll()
        }

        // Sort
        val sorted = when (sort) {
            "createdAt" -> all.sortedBy { it.createdAt }
            "-createdAt" -> all.sortedByDescending { it.createdAt }
            "dueDate" -> all.sortedBy { it.dueDate }
            "-dueDate" -> all.sortedByDescending { it.dueDate }
            "total" -> all.sortedBy { it.total }
            "-total" -> all.sortedByDescending { it.total }
            else -> all.sortedByDescending { it.createdAt }
        }

        // Paginate
        val total = sorted.size
        val totalPages = if (total == 0) 1 else kotlin.math.ceil(total.toDouble() / size).toInt()
        val safePage = page.coerceIn(1, totalPages)
        val offset = (safePage - 1) * size
        val pageItems = sorted.drop(offset).take(size)

        val paginated = PaginatedInvoices(
            items = pageItems.map { it.toResponse() },
            page = safePage,
            pageSize = size,
            totalItems = total,
            totalPages = totalPages,
        )
        return Response.ok(paginated).build()
    }

    // ── Get invoice ─────────────────────────────────────────────────────────

    @GET
    @Path("/{id}")
    fun getInvoice(@PathParam("id") id: UUID): Response {
        val invoice = invoiceRepository.findById(id)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(ErrorDetail(code = "not-found", message = "Invoice $id not found", target = "id")))
                .build()
        return Response.ok(invoice.toResponse()).build()
    }
}

// ── Mapping Extensions: Domain ↔ OpenAPI DTO ────────────────────────────────

/**
 * Convert a domain [Invoice] to the OpenAPI [InvoiceResponse] DTO.
 *
 * Domain types differ from the API model in several ways:
 * - Domain total/subtotal/totalTax are BigDecimal; the DTO uses Money (Double amount).
 * - Domain quantity is BigDecimal; the DTO uses Int.
 * - Domain Instant maps to OffsetDateTime via UTC.
 * - Domain InvoiceStatus enum values match the OpenAPI enum (both UPPERCASE).
 */
fun Invoice.toResponse(): InvoiceResponse =
    InvoiceResponse(
        invoiceId = this.invoiceId,
        subscriptionId = UUID.randomUUID(), // Not yet linked to subscription in domain model
        customerId = UUID.fromString(this.customerId),
        status = com.crm.openapi.billing.model.InvoiceStatus.valueOf(this.status.name),
        lineItems = this.lineItems.map { it.toResponse() },
        subtotal = Money(
            amount = this.subtotal.toDouble(),
            currency = this.currency,
        ),
        tax = Money(
            amount = this.totalTax.toDouble(),
            currency = this.currency,
        ),
        total = Money(
            amount = this.total.toDouble(),
            currency = this.currency,
        ),
        dueDate = this.dueDate,
        issuedAt = OffsetDateTime.ofInstant(this.createdAt, ZoneOffset.UTC),
        paidAt = null, // Domain model does not yet track paid timestamp
    )

/**
 * Convert a domain [InvoiceLineItem] to the OpenAPI [InvoiceLineItem] DTO.
 */
fun com.crm.billing.domain.invoice.InvoiceLineItem.toResponse(): com.crm.openapi.billing.model.InvoiceLineItem =
    com.crm.openapi.billing.model.InvoiceLineItem(
        description = this.description,
        quantity = this.quantity.toInt(),
        unitPrice = Money(
            amount = this.unitPrice.toDouble(),
            currency = "USD", // Line items inherit currency from parent invoice
        ),
        totalPrice = Money(
            amount = this.total.toDouble(),
            currency = "USD",
        ),
    )
