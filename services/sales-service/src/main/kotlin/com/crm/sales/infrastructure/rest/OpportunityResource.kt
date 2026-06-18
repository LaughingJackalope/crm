package com.crm.sales.infrastructure.rest

import com.crm.sales.application.OpportunityCommandService
import com.crm.sales.application.NotFoundException
import com.crm.sales.application.OpportunityRepository
import com.crm.sales.domain.opportunity.Opportunity
import com.crm.sales.domain.opportunity.SalesStage
import com.crm.openapi.sales.model.*
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * REST JSON API for Opportunity pipeline operations.
 *
 * Provides the HTTP contract for the Sales bounded context. All endpoints
 * use OpenAPI-generated DTOs from [com.crm.openapi.sales.model] to keep
 * the HTTP contract in sync with the API specification.
 *
 * The HTML web UI is served separately by [SalesWebResource].
 */
@Path("/api/v1/opportunities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class OpportunityResource @Inject constructor(
    private val commandService: OpportunityCommandService,
    private val opportunityRepository: OpportunityRepository,
)
{
    // ── List opportunities ──────────────────────────────────────────────────

    @GET
    fun listOpportunities(
        @QueryParam("stage") stage: String? = null,
        @QueryParam("ownerId") ownerId: String? = null,
        @QueryParam("customerId") customerId: String? = null,
        @QueryParam("sort") @DefaultValue("-createdAt") sort: String,
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("size") @DefaultValue("25") size: Int,
    ): Response {
        // Collect all opportunities from the repository, applying filters
        val all = when {
            stage != null -> {
                val salesStage = runCatching { SalesStage.valueOf(stage) }.getOrNull()
                    ?: return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ErrorResponse(ErrorDetail(code = "invalid-stage", message = "Unknown stage: $stage", target = "stage")))
                        .build()
                opportunityRepository.findByStage(salesStage)
            }
            ownerId != null -> opportunityRepository.findByOwnerId(ownerId)
            customerId != null -> opportunityRepository.findByCustomerId(customerId)
            else -> opportunityRepository.findByStage(SalesStage.PROSPECTING) +
                opportunityRepository.findByStage(SalesStage.DISCOVERY) +
                opportunityRepository.findByStage(SalesStage.PROPOSAL) +
                opportunityRepository.findByStage(SalesStage.NEGOTIATION) +
                opportunityRepository.findByStage(SalesStage.CLOSED_WON) +
                opportunityRepository.findByStage(SalesStage.CLOSED_LOST)
        }

        // Sort
        val sorted = when (sort) {
            "createdAt" -> all.sortedBy { it.createdAt }
            "-createdAt" -> all.sortedByDescending { it.createdAt }
            "name" -> all.sortedBy { it.name }
            "-name" -> all.sortedByDescending { it.name }
            "amount" -> all.sortedBy { it.amount.value }
            "-amount" -> all.sortedByDescending { it.amount.value }
            "expectedCloseDate" -> all.sortedBy { it.expectedCloseDate }
            "-expectedCloseDate" -> all.sortedByDescending { it.expectedCloseDate }
            else -> all.sortedByDescending { it.createdAt }
        }

        // Paginate
        val total = sorted.size
        val totalPages = if (total == 0) 1 else kotlin.math.ceil(total.toDouble() / size).toInt()
        val safePage = page.coerceIn(1, totalPages)
        val offset = (safePage - 1) * size
        val pageItems = sorted.drop(offset).take(size)

        val paginated = PaginatedOpportunities(
            items = pageItems.map { it.toResponse() },
            page = safePage,
            pageSize = size,
            totalItems = total,
            totalPages = totalPages,
        )
        return Response.ok(paginated).build()
    }

    // ── Create opportunity ──────────────────────────────────────────────────

    @POST
    fun createOpportunity(request: CreateOpportunityRequest): Response {
        val customerId = request.customerId.toString()
        val accountId = request.accountId?.toString()
        val amount = com.crm.sales.domain.opportunity.Money(
            value = request.amount.amount.let { java.math.BigDecimal.valueOf(it) },
            currency = request.amount.currency,
        )
        val expectedCloseDate = request.expectedCloseDate?.let { java.time.LocalDate.parse(it.toString()) }
        val ownerId = request.ownerId?.toString()

        val opportunity = commandService.createOpportunity(
            customerId = customerId,
            name = request.name,
            amount = amount,
            accountId = accountId,
            expectedCloseDate = expectedCloseDate,
            ownerId = ownerId,
        )

        return Response.status(Response.Status.CREATED)
            .entity(opportunity.toResponse())
            .build()
    }

    // ── Get opportunity ─────────────────────────────────────────────────────

    @GET
    @Path("/{id}")
    fun getOpportunity(@PathParam("id") id: UUID): Response {
        val opportunity = opportunityRepository.findById(id)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(ErrorDetail(code = "not-found", message = "Opportunity $id not found", target = "id")))
                .build()
        return Response.ok(opportunity.toResponse()).build()
    }

    // ── Advance stage ───────────────────────────────────────────────────────

    @POST
    @Path("/{id}/advance-stage")
    fun advanceStage(@PathParam("id") id: UUID): Response {
        return try {
            val opportunity = commandService.advanceStage(id)
            Response.ok(opportunity.toResponse()).build()
        } catch (e: NotFoundException) {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(ErrorDetail(code = "not-found", message = e.message ?: "Not found", target = "id")))
                .build()
        } catch (e: Exception) {
            Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse(ErrorDetail(code = "conflict", message = e.message ?: "Cannot advance stage", target = "id")))
                .build()
        }
    }

    // ── Close opportunity ───────────────────────────────────────────────────

    @POST
    @Path("/{id}/close")
    fun closeOpportunity(
        @PathParam("id") id: UUID,
        request: CloseOpportunityRequest,
    ): Response {
        return try {
            val isWon = request.outcome == CloseOpportunityRequest.Outcome.WON
            val reason = request.winLossReason?.let {
                com.crm.sales.domain.opportunity.WinLossReason(
                    category = it.category.name,
                    detail = it.detail,
                )
            }
            val opportunity = commandService.closeOpportunity(id, isWon, reason)
            Response.ok(opportunity.toResponse()).build()
        } catch (e: NotFoundException) {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(ErrorDetail(code = "not-found", message = e.message ?: "Not found", target = "id")))
                .build()
        } catch (e: Exception) {
            Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse(ErrorDetail(code = "conflict", message = e.message ?: "Cannot close opportunity", target = "id")))
                .build()
        }
    }

    // ── Reassign owner ──────────────────────────────────────────────────────

    @POST
    @Path("/{id}/reassign")
    fun reassignOwner(
        @PathParam("id") id: UUID,
        request: ReassignOwnerRequest,
    ): Response {
        return try {
            val newOwnerId = request.newOwnerId.toString()
            val opportunity = commandService.reassignOwner(id, newOwnerId)
            Response.ok(opportunity.toResponse()).build()
        } catch (e: NotFoundException) {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(ErrorDetail(code = "not-found", message = e.message ?: "Not found", target = "id")))
                .build()
        }
    }
}

// ── Mapping Extensions: Domain ↔ OpenAPI DTO ────────────────────────────────

/**
 * Convert a domain [Opportunity] to the OpenAPI [OpportunityResponse] DTO.
 *
 * Domain types differ from the API model in several ways:
 * - Domain customerId is a raw String; the DTO uses UUID representation.
 * - Domain amount is BigDecimal; the DTO uses Double.
 * - Domain Instant maps to OffsetDateTime via UTC.
 */
fun Opportunity.toResponse(): OpportunityResponse =
    OpportunityResponse(
        opportunityId = opportunityId,
        customerId = UUID.fromString(customerId),
        accountId = accountId?.let { UUID.fromString(it) } ?: opportunityId,
        name = name,
        stage = stage.name,
        amount = Money(
            amount = this.amount.value.toDouble(),
            currency = this.amount.currency,
        ),
        probability = probability,
        expectedCloseDate = expectedCloseDate ?: java.time.LocalDate.of(2099, 12, 31),
        ownerId = ownerId?.let { UUID.fromString(it) } ?: opportunityId,
        createdAt = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
        updatedAt = OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC),
    )
