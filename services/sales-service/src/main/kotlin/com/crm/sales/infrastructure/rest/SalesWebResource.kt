package com.crm.sales.infrastructure.rest

import com.crm.sales.application.OpportunityCommandService
import com.crm.sales.application.NotFoundException
import com.crm.sales.application.OpportunityRepository
import com.crm.sales.domain.opportunity.SalesStage
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateExtension
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.format.DateTimeFormatter

/**
 * Serves Qute-rendered HTML pages for the Sales / Opportunity Pipeline web UI.
 *
 * These endpoints return full HTML pages for browser navigation, while the
 * underlying REST API endpoints (in [OpportunityResource]) serve JSON for programmatic consumers.
 *
 * ## Routing convention
 * - `GET /sales/opportunities` → full page with opportunity list
 * - `GET /sales/opportunities?page=2&size=25&q=acme&stage=PROSPECTING` → same page, filtered
 * - `GET /sales/opportunities/{id}` → opportunity detail page
 */
@Path("/sales")
class SalesWebResource @Inject constructor(
    private val opportunityRepository: OpportunityRepository,
    private val commandService: OpportunityCommandService,
) {

    @Inject
    @io.quarkus.qute.Location("opportunities/list.html")
    lateinit var listTemplate: Template

    @Inject
    @io.quarkus.qute.Location("opportunities/detail.html")
    lateinit var detailTemplate: Template

    // ── Opportunity list ────────────────────────────────────────────────────

    @GET
    @Path("/opportunities")
    @Produces(MediaType.TEXT_HTML)
    fun listOpportunities(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("size") @DefaultValue("25") size: Int,
        @QueryParam("q") q: String? = null,
        @QueryParam("stage") stageFilter: String? = null,
    ): Response {
        val stage = stageFilter?.let { runCatching { SalesStage.valueOf(it) }.getOrNull() }

        // Fetch from repository — all if no stage filter, otherwise by stage
        val all = if (stage != null) {
            opportunityRepository.findByStage(stage)
        } else {
            SalesStage.entries.flatMap { opportunityRepository.findByStage(it) }
        }

        // Apply search filter
        val filtered = all.filter { opp ->
            if (q.isNullOrBlank()) true
            else opp.name.contains(q, ignoreCase = true) ||
                opp.customerId.contains(q, ignoreCase = true) ||
                opp.ownerId?.contains(q, ignoreCase = true) == true
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

        val html = listTemplate
            .data("opportunities", pageItems.map { it.toWebOpportunity() })
            .data("page", safePage)
            .data("size", size)
            .data("totalPages", totalPages)
            .data("total", total)
            .data("pages", pages)
            .data("q", q)
            .data("selectedStage", stageFilter)
            .data("stages", SalesStage.entries.map { StageInfo(it.name, salesStageLabel(it)) })
            .data("activePage", "opportunities")
            .render()

        return Response.ok(html).build()
    }

    // ── Opportunity detail ──────────────────────────────────────────────────

    @GET
    @Path("/opportunities/{id}")
    @Produces(MediaType.TEXT_HTML)
    fun getOpportunity(@PathParam("id") id: java.util.UUID): Response {
        val opportunity = opportunityRepository.findById(id)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity("<html><body><h1>Opportunity not found</h1><a href='/sales/opportunities'>← Back</a></body></html>")
                .build()

        val html = detailTemplate
            .data("opportunity", opportunity.toWebOpportunity())
            .data("stages", SalesStage.entries.map { StageInfo(it.name, salesStageLabel(it)) })
            .data("activePage", "opportunities")
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

data class WebOpportunity(
    val id: String,
    val name: String,
    val stage: SalesStage,
    val stageLabel: String,
    val amount: String,
    val probability: Int,
    val customerId: String,
    val ownerId: String?,
    val expectedCloseDate: String?,
    val createdAt: String,
    val isClosed: Boolean,
)

data class StageInfo(val name: String, val label: String)

// ── Domain → Web DTO mapping ─────────────────────────────────────────────────

private fun com.crm.sales.domain.opportunity.Opportunity.toWebOpportunity() = WebOpportunity(
    id = opportunityId.toString(),
    name = name,
    stage = stage,
    stageLabel = salesStageLabel(stage),
    amount = "${amount.value} ${amount.currency}",
    probability = probability,
    customerId = customerId,
    ownerId = ownerId,
    expectedCloseDate = expectedCloseDate?.toString(),
    createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt),
    isClosed = stage.isClosed,
)

// ── Label helpers ─────────────────────────────────────────────────────────────

fun salesStageLabel(stage: SalesStage): String = when (stage) {
    SalesStage.PROSPECTING -> "Prospecting"
    SalesStage.DISCOVERY -> "Discovery"
    SalesStage.PROPOSAL -> "Proposal"
    SalesStage.NEGOTIATION -> "Negotiation"
    SalesStage.CLOSED_WON -> "Closed Won"
    SalesStage.CLOSED_LOST -> "Closed Lost"
}
