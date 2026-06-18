package com.crm.marketing.infrastructure.rest

import com.crm.marketing.application.CampaignOrchestrationService
import com.crm.marketing.domain.campaign.AdNetworkSource
import com.crm.marketing.domain.campaign.Campaign
import com.crm.marketing.domain.campaign.CampaignStatus
import com.crm.marketing.infrastructure.persistence.CampaignRepository
import com.crm.openapi.marketing.model.*
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * RESTEasy Reactive endpoint for Campaign lifecycle operations.
 *
 * Serves JSON for programmatic consumers and htmx fragments.
 * Delegates all business logic to [CampaignOrchestrationService].
 */
@Path("/api/v1/campaigns")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class CampaignResource @Inject constructor(
    private val orchestrationService: CampaignOrchestrationService,
    private val campaignRepository: CampaignRepository,
) {

    // ── List campaigns ───────────────────────────────────────────────────────

    @GET
    fun listCampaigns(
        @QueryParam("status") statusFilter: String? = null,
        @QueryParam("source") sourceFilter: String? = null,
        @QueryParam("sort") @DefaultValue("-createdAt") sort: String,
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("size") @DefaultValue("25") size: Int,
    ): Response {
        // Fetch filtered or all campaigns
        val allCampaigns: List<Campaign> = when {
            statusFilter != null -> {
                val status = runCatching { CampaignStatus.valueOf(statusFilter) }.getOrNull()
                if (status != null) campaignRepository.findByStatus(status) else emptyList()
            }
            sourceFilter != null -> {
                val source = runCatching { AdNetworkSource.valueOf(sourceFilter) }.getOrNull()
                if (source != null) campaignRepository.findBySource(source) else emptyList()
            }
            else -> campaignRepository.findAll()
        }

        // Sort
        val sorted = when (sort) {
            "name" -> allCampaigns.sortedBy { it.name }
            "-name" -> allCampaigns.sortedByDescending { it.name }
            "startDate" -> allCampaigns.sortedBy { it.startDate }
            "-startDate" -> allCampaigns.sortedByDescending { it.startDate }
            "createdAt" -> allCampaigns.sortedBy { it.createdAt }
            "-createdAt" -> allCampaigns.sortedByDescending { it.createdAt }
            else -> allCampaigns.sortedByDescending { it.createdAt }
        }

        // In-memory pagination
        val total = sorted.size
        val totalPages = if (total == 0) 1 else kotlin.math.ceil(total.toDouble() / size).toInt()
        val safePage = page.coerceIn(1, totalPages)
        val offset = (safePage - 1) * size
        val items = sorted.drop(offset).take(size)

        val response = PaginatedCampaigns(
            items = items.map { it.toResponse() },
            page = safePage,
            pageSize = size,
            totalItems = total,
            totalPages = totalPages,
        )
        return Response.ok(response).build()
    }

    // ── Create campaign ──────────────────────────────────────────────────────

    @POST
    fun createCampaign(request: CreateCampaignRequest): Response {
        val source = try {
            mapChannelToSource(request.channel)
        } catch (e: IllegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse(error = ErrorDetail(
                    code = "invalid-channel",
                    message = "Unsupported channel: ${request.channel}",
                    target = "channel",
                )))
                .build()
        }

        val saved = orchestrationService.createCampaign(
            name = request.name,
            source = source,
            targetSegment = request.audienceId.toString(),
            budget = if (request.budget != null) java.math.BigDecimal.valueOf(request.budget!!.amount) else java.math.BigDecimal.ZERO,
        )
        return Response.status(Response.Status.CREATED)
            .entity(saved.toResponse())
            .build()
    }

    // ── Get campaign ─────────────────────────────────────────────────────────

    @GET
    @Path("/{campaignId}")
    fun getCampaign(@PathParam("campaignId") campaignId: UUID): Response {
        val campaign = orchestrationService.getCampaign(campaignId)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(error = ErrorDetail(
                    code = "not-found",
                    message = "Campaign $campaignId not found",
                    target = "campaignId",
                )))
                .build()
        return Response.ok(campaign.toResponse()).build()
    }

    // ── Launch campaign ──────────────────────────────────────────────────────

    @POST
    @Path("/{campaignId}/launch")
    fun launchCampaign(@PathParam("campaignId") campaignId: UUID): Response {
        val campaign = orchestrationService.launchCampaign(campaignId)
        return Response.ok(campaign.toResponse()).build()
    }

    // ── Pause campaign ───────────────────────────────────────────────────────

    @POST
    @Path("/{campaignId}/pause")
    fun pauseCampaign(@PathParam("campaignId") campaignId: UUID): Response {
        val campaign = orchestrationService.pauseCampaign(campaignId)
        return Response.ok(campaign.toResponse()).build()
    }

    // ── Complete campaign ────────────────────────────────────────────────────

    @POST
    @Path("/{campaignId}/complete")
    fun completeCampaign(@PathParam("campaignId") campaignId: UUID): Response {
        val campaign = orchestrationService.completeCampaign(campaignId)
        return Response.ok(campaign.toResponse()).build()
    }
}

// ── Mapping Extensions: Domain → OpenAPI DTO ─────────────────────────────────

/**
 * Map domain [AdNetworkSource] to OpenAPI [Channel].
 * Only EMAIL and SMS have direct equivalents; default to Email for unmapped.
 */
fun mapChannelToSource(channel: Channel): AdNetworkSource = when (channel) {
    Channel.EMAIL -> AdNetworkSource.EMAIL
    Channel.SMS -> AdNetworkSource.SMS
    Channel.PUSH -> AdNetworkSource.DISPLAY
    Channel.SOCIAL -> AdNetworkSource.META
    Channel.DIRECT_MAIL -> AdNetworkSource.DIRECT
}

/**
 * Map OpenAPI [Channel] to a display-friendly source name.
 */
fun AdNetworkSource.toChannelLabel(): String = when (this) {
    AdNetworkSource.GOOGLE -> "Google Ads"
    AdNetworkSource.META -> "Meta Ads"
    AdNetworkSource.DIRECT -> "Direct"
    AdNetworkSource.EMAIL -> "Email"
    AdNetworkSource.SMS -> "SMS"
    AdNetworkSource.DISPLAY -> "Display"
    AdNetworkSource.AFFILIATE -> "Affiliate"
}

/**
 * Map domain [CampaignStatus] to OpenAPI [CampaignStatus].
 * Domain has CANCELLED (no OpenAPI equivalent); those become null → we use Completed as fallback.
 */
fun com.crm.marketing.domain.campaign.CampaignStatus.toOpenApiStatus(): com.crm.openapi.marketing.model.CampaignStatus? = when (this) {
    com.crm.marketing.domain.campaign.CampaignStatus.DRAFT -> com.crm.openapi.marketing.model.CampaignStatus.DRAFT
    com.crm.marketing.domain.campaign.CampaignStatus.ACTIVE -> com.crm.openapi.marketing.model.CampaignStatus.ACTIVE
    com.crm.marketing.domain.campaign.CampaignStatus.PAUSED -> com.crm.openapi.marketing.model.CampaignStatus.PAUSED
    com.crm.marketing.domain.campaign.CampaignStatus.COMPLETED -> com.crm.openapi.marketing.model.CampaignStatus.COMPLETED
    com.crm.marketing.domain.campaign.CampaignStatus.CANCELLED -> null
}

/**
 * Convert a domain [Campaign] to the OpenAPI [CampaignResponse] DTO.
 */
fun Campaign.toResponse(): CampaignResponse {
    return CampaignResponse(
        campaignId = campaignId,
        name = name,
        objective = targetSegment,
        status = status.toOpenApiStatus() ?: com.crm.openapi.marketing.model.CampaignStatus.DRAFT,
        channel = when (source) {
            AdNetworkSource.EMAIL -> Channel.EMAIL
            AdNetworkSource.SMS -> Channel.SMS
            AdNetworkSource.DISPLAY -> Channel.PUSH
            AdNetworkSource.META -> Channel.SOCIAL
            AdNetworkSource.DIRECT -> Channel.DIRECT_MAIL
            AdNetworkSource.GOOGLE -> Channel.PUSH
            AdNetworkSource.AFFILIATE -> Channel.SOCIAL
        },
        audienceId = java.util.UUID.nameUUIDFromBytes(targetSegment.toByteArray()),
        startDate = startDate?.let {
            OffsetDateTime.of(it.atStartOfDay(), ZoneOffset.UTC)
        } ?: OffsetDateTime.now(ZoneOffset.UTC),
        endDate = endDate?.let {
            OffsetDateTime.of(it.atStartOfDay(), ZoneOffset.UTC)
        } ?: OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
        budget = Money(
            amount = budget.toDouble(),
            currency = "USD",
        ),
        abTestConfig = null,
        createdAt = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
        updatedAt = OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC),
    )
}
