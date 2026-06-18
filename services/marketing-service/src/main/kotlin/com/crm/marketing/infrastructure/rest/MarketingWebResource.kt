package com.crm.marketing.infrastructure.rest

import com.crm.marketing.application.CampaignOrchestrationService
import com.crm.marketing.domain.campaign.AdNetworkSource
import com.crm.marketing.domain.campaign.Campaign
import com.crm.marketing.domain.campaign.CampaignStatus
import com.crm.marketing.infrastructure.persistence.CampaignRepository
import io.quarkus.qute.Template
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Serves Qute-rendered HTML pages for the Marketing web UI.
 *
 * These endpoints return full HTML pages for browser navigation, while the
 * underlying REST API endpoints (in [CampaignResource]) serve JSON for htmx
 * fragments and programmatic consumers.
 *
 * ## Routing convention
 * - `GET /marketing/campaigns` → full page with campaign list
 * - `GET /marketing/campaigns?page=2&size=25&q=sale&status=ACTIVE` → same page, filtered
 * - `GET /marketing/campaigns/{id}` → campaign detail page
 */
@Path("/marketing")
class MarketingWebResource @Inject constructor(
    private val campaignRepository: CampaignRepository,
    private val orchestrationService: CampaignOrchestrationService,
) {

    @Inject
    @io.quarkus.qute.Location("campaigns/list.html")
    lateinit var listTemplate: Template

    @Inject
    @io.quarkus.qute.Location("campaigns/detail.html")
    lateinit var detailTemplate: Template

    // ── Campaign list ────────────────────────────────────────────────────────

    @GET
    @Path("/campaigns")
    @Produces(MediaType.TEXT_HTML)
    fun listCampaigns(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("size") @DefaultValue("25") size: Int,
        @QueryParam("q") q: String? = null,
        @QueryParam("status") statusFilter: String? = null,
    ): Response {
        val status = statusFilter?.let { runCatching { CampaignStatus.valueOf(it) }.getOrNull() }

        // Fetch campaigns with optional status filter
        val allCampaigns: List<Campaign> = if (status != null) {
            campaignRepository.findByStatus(status)
        } else {
            campaignRepository.findAll()
        }

        // Apply text search
        val filtered = allCampaigns.filter { campaign ->
            q.isNullOrBlank() ||
                campaign.name.contains(q, ignoreCase = true) ||
                campaign.source.name.contains(q, ignoreCase = true)
        }

        // Sort by createdAt descending (most recent first)
        val sorted = filtered.sortedByDescending { it.createdAt }

        // Pagination
        val total = sorted.size
        val totalPages = if (total == 0) 1 else kotlin.math.ceil(total.toDouble() / size).toInt()
        val safePage = page.coerceIn(1, totalPages)
        val offset = (safePage - 1) * size
        val pageItems = sorted.drop(offset).take(size)

        // Build page numbers for pagination
        val pages = buildPageRange(safePage, totalPages)

        val html = listTemplate
            .data("campaigns", pageItems.map { it.toWebCampaign(orchestrationService) })
            .data("page", safePage)
            .data("size", size)
            .data("totalPages", totalPages)
            .data("total", total)
            .data("pages", pages)
            .data("q", q)
            .data("selectedStatus", statusFilter)
            .data("statuses", CampaignStatus.entries.map { StatusInfo(it.name, campaignStatusLabel(it)) })
            .data("activePage", "campaigns")
            .render()

        return Response.ok(html).build()
    }

    // ── Campaign detail ──────────────────────────────────────────────────────

    @GET
    @Path("/campaigns/{campaignId}")
    @Produces(MediaType.TEXT_HTML)
    fun getCampaign(@PathParam("campaignId") campaignId: UUID): Response {
        val campaign = orchestrationService.getCampaign(campaignId)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity("<html><body><h1>Campaign not found</h1><a href='/marketing/campaigns'>← Back</a></body></html>")
                .build()

        val roas = orchestrationService.getCampaignRoas(campaignId)
        val cpa = orchestrationService.getCampaignCpa(campaignId)

        val html = detailTemplate
            .data("campaign", campaign.toWebCampaign(orchestrationService, roas, cpa))
            .data("statuses", CampaignStatus.entries.map { StatusInfo(it.name, campaignStatusLabel(it)) })
            .data("activePage", "campaigns")
            .render()

        return Response.ok(html).build()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPageRange(current: Int, total: Int): List<Int> {
        if (total <= 7) return (1..total).toList()
        val start = maxOf(1, current - 2)
        val end = minOf(total, current + 2)
        return (start..end).toList()
    }
}

// ── Web-facing DTOs (flattened for template consumption) ────────────────────

data class WebCampaign(
    val id: String,
    val name: String,
    val status: CampaignStatus,
    val statusLabel: String,
    val source: String,
    val budget: String,
    val sent: Int,
    val opened: Int,
    val clicked: Int,
    val conversions: Int,
    val roas: String,
    val cpa: String,
    val startDate: String?,
    val endDate: String?,
    val createdAt: String,
)

data class StatusInfo(val name: String, val label: String)

// ── Label helpers ───────────────────────────────────────────────────────────

fun campaignStatusLabel(status: CampaignStatus): String = when (status) {
    CampaignStatus.DRAFT -> "Draft"
    CampaignStatus.ACTIVE -> "Active"
    CampaignStatus.PAUSED -> "Paused"
    CampaignStatus.COMPLETED -> "Completed"
    CampaignStatus.CANCELLED -> "Cancelled"
}

fun adNetworkSourceLabel(source: AdNetworkSource): String = when (source) {
    AdNetworkSource.GOOGLE -> "Google Ads"
    AdNetworkSource.META -> "Meta Ads"
    AdNetworkSource.DIRECT -> "Direct"
    AdNetworkSource.EMAIL -> "Email"
    AdNetworkSource.SMS -> "SMS"
    AdNetworkSource.DISPLAY -> "Display"
    AdNetworkSource.AFFILIATE -> "Affiliate"
}

// ── Mapping: Domain → Web DTO ───────────────────────────────────────────────

fun Campaign.toWebCampaign(
    orchestrationService: CampaignOrchestrationService,
    roas: java.math.BigDecimal? = null,
    cpa: java.math.BigDecimal? = null,
): WebCampaign {
    val effectiveRoas = roas ?: orchestrationService.getCampaignRoas(campaignId)
    val effectiveCpa = cpa ?: orchestrationService.getCampaignCpa(campaignId)

    return WebCampaign(
        id = campaignId.toString(),
        name = name,
        status = status,
        statusLabel = campaignStatusLabel(status),
        source = adNetworkSourceLabel(source),
        budget = "$${budget.setScale(2, RoundingMode.HALF_UP)}",
        sent = metrics.impressions.toInt(),
        opened = 0,  // Not tracked in domain metrics
        clicked = metrics.clicks.toInt(),
        conversions = metrics.attributedConversions.toInt(),
        roas = "${effectiveRoas.setScale(2, RoundingMode.HALF_UP)}x",
        cpa = "$${effectiveCpa.setScale(2, RoundingMode.HALF_UP)}",
        startDate = startDate?.toString(),
        endDate = endDate?.toString(),
        createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt),
    )
}
