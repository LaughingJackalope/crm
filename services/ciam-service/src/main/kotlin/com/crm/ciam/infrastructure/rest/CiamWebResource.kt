package com.crm.ciam.infrastructure.rest

import com.crm.ciam.domain.customer.CustomerRepository
import com.crm.ciam.domain.customer.LifecycleStage
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateExtension
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.format.DateTimeFormatter

/**
 * Serves Qute-rendered HTML pages for the CIAM web UI.
 *
 * These endpoints return full HTML pages for browser navigation, while the
 * underlying REST API endpoints (in [CustomerResource]) serve JSON for htmx
 * fragments and programmatic consumers (TUI, macOS app).
 *
 * ## Routing convention
 * - `GET /ciam/contacts` → full page with contact list
 * - `GET /ciam/contacts?page=2&size=25&q=alice&lifecycleStage=LEAD` → same page, filtered
 * - `GET /ciam/contacts/{id}` → contact detail page
 * - `GET /ciam/contacts/new` → new contact form
 */
@Path("/ciam")
class CiamWebResource @Inject constructor(
    private val customerRepository: CustomerRepository,
) {

    @Inject
    @io.quarkus.qute.Location("contacts/list.html")
    lateinit var listTemplate: Template

    @Inject
    @io.quarkus.qute.Location("contacts/detail.html")
    lateinit var detailTemplate: Template

    // ── Contact list ─────────────────────────────────────────────────────────

    @GET
    @Path("/contacts")
    @Produces(MediaType.TEXT_HTML)
    fun listContacts(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("size") @DefaultValue("25") size: Int,
        @QueryParam("q") q: String? = null,
        @QueryParam("lifecycleStage") stageFilter: String? = null,
    ): Response {
        val lifecycleStage = stageFilter?.let { runCatching { LifecycleStage.valueOf(it) }.getOrNull() }

        // Fetch contacts with pagination and optional filters
        val allCustomers = customerRepository.findAllActive()
        val filtered = allCustomers.filter { customer ->
            val matchesQuery = q.isNullOrBlank() ||
                customer.displayName.contains(q, ignoreCase = true) ||
                customer.contacts.any { c ->
                    c.email.value.contains(q, ignoreCase = true) ||
                    c.firstName.contains(q, ignoreCase = true) ||
                    c.lastName.contains(q, ignoreCase = true)
                }
            val matchesStage = lifecycleStage == null || customer.lifecycleStage == lifecycleStage
            matchesQuery && matchesStage
        }

        val total = filtered.size
        val totalPages = if (total == 0) 1 else kotlin.math.ceil(total.toDouble() / size).toInt()
        val safePage = page.coerceIn(1, totalPages)
        val offset = (safePage - 1) * size
        val pageItems = filtered.drop(offset).take(size)

        // Build page numbers for pagination (show up to 5 pages around current)
        val pages = buildPageRange(safePage, totalPages)

        val html = listTemplate
            .data("contacts", pageItems.map { it.toWebContact() })
            .data("page", safePage)
            .data("size", size)
            .data("totalPages", totalPages)
            .data("total", total)
            .data("pages", pages)
            .data("q", q)
            .data("selectedStage", stageFilter)
            .data("stages", LifecycleStage.entries.map { StageInfo(it.name, lifecycleStageLabel(it)) })
            .data("activePage", "contacts")
            .render()

        return Response.ok(html).build()
    }

    // ── Contact detail ───────────────────────────────────────────────────────

    @GET
    @Path("/contacts/{contactId}")
    @Produces(MediaType.TEXT_HTML)
    fun getContact(@PathParam("contactId") contactId: java.util.UUID): Response {
        val customer = customerRepository.findById(contactId)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity("<html><body><h1>Contact not found</h1><a href='/ciam/contacts'>← Back</a></body></html>")
                .build()

        val html = detailTemplate
            .data("contact", customer.toWebContact())
            .data("stages", LifecycleStage.entries.map { StageInfo(it.name, lifecycleStageLabel(it)) })
            .data("activePage", "contacts")
            .render()

        return Response.ok(html).build()
    }

    // ── New contact form ─────────────────────────────────────────────────────

    @GET
    @Path("/contacts/new")
    @Produces(MediaType.TEXT_HTML)
    fun newContactForm(): Response {
        return Response.ok(
            """
            <!DOCTYPE html>
            <html><body style="padding:40px; font-family: sans-serif;">
            <h1>New Contact</h1>
            <p style="color: #666;">Form coming soon. Use the API for now:</p>
            <pre style="background: #f5f5f5; padding: 16px; border-radius: 8px;">
curl -X POST /api/v1/contacts \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Jane","lastName":"Doe","email":"jane@example.com"}'
            </pre>
            <a href="/ciam/contacts">← Back to Contacts</a>
            </body></html>
            """.trimIndent()
        ).build()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPageRange(current: Int, total: Int): List<Int> {
        if (total <= 7) return (1..total).toList()
        val start = maxOf(1, current - 2)
        val end = minOf(total, current + 2)
        return (start..end).toList()
    }

    private fun com.crm.ciam.domain.customer.Customer.toWebContact() = WebContact(
        contactId = this.customerId.toString(),
        displayName = this.displayName,
        firstName = this.contacts.firstOrNull()?.firstName ?: "",
        lastName = this.contacts.firstOrNull()?.lastName ?: "",
        title = this.contacts.firstOrNull()?.title,
        email = this.contacts.firstOrNull()?.email?.let { WebEmail(it.value) },
        phone = this.contacts.firstOrNull()?.phone?.let { "${it.countryCode} ${it.value}" },
        lifecycleStage = this.lifecycleStage,
        source = this.source,
        isActive = this.isActive,
        createdAt = DateTimeFormatter.ISO_INSTANT.format(this.createdAt),
        updatedAt = DateTimeFormatter.ISO_INSTANT.format(this.updatedAt),
    )
}

// ── Web-facing DTOs (flattened for template consumption) ────────────────────

data class WebContact(
    val contactId: String,
    val displayName: String,
    val firstName: String,
    val lastName: String,
    val title: String? = null,
    val email: WebEmail? = null,
    val phone: String? = null,
    val lifecycleStage: LifecycleStage,
    val source: String? = null,
    val isActive: Boolean = true,
    val createdAt: String,
    val updatedAt: String,
)

data class WebEmail(val value: String)

data class StageInfo(val name: String, val label: String)

// ── Shared label function (used by both Kotlin code and Qute templates) ──────

fun lifecycleStageLabel(stage: LifecycleStage): String = when (stage) {
    LifecycleStage.LEAD -> "Lead"
    LifecycleStage.QUALIFIED -> "Qualified"
    LifecycleStage.OPPORTUNITY -> "Opportunity"
    LifecycleStage.CUSTOMER -> "Customer"
    LifecycleStage.ADVOCATE -> "Advocate"
    LifecycleStage.CHURNED -> "Churned"
}

// ── Qute template extensions ─────────────────────────────────────────────────

@TemplateExtension
object TemplateExtensions {

    /**
     * Human-readable label for a [LifecycleStage].
     * Delegates to [lifecycleStageLabel] so the same logic is used in
     * Kotlin code and Qute templates.
     */
    @JvmStatic
    fun label(stage: LifecycleStage): String = lifecycleStageLabel(stage)
}
