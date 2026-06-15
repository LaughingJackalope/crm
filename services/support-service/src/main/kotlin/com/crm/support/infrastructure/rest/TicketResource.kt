package com.crm.support.infrastructure.rest

import com.crm.openapi.support.model.*
import com.crm.support.application.TicketOrchestrationService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Path("/api/v1/tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TicketResource @Inject constructor(
    private val orchestration: TicketOrchestrationService,
) {

    @POST
    fun createTicket(request: CreateTicketRequest): Response {
        val ticket = orchestration.createTicket(
            requesterId = request.requesterId.toString(),
            subject = request.subject,
            description = request.description,
            priority = request.priority.toDomain(),
            queueId = request.queueId?.toString(),
            now = Instant.now(),
        )
        return Response.status(Response.Status.CREATED).entity(ticket.toResponse()).build()
    }

    @GET
    @Path("/{ticketId}")
    fun getTicket(@PathParam("ticketId") ticketId: UUID): Response {
        val ticket = orchestration.findById(ticketId)
            ?: return Response.status(Response.Status.NOT_FOUND).build()
        return Response.ok(ticket.toResponse()).build()
    }

    @POST
    @Path("/{ticketId}/assign")
    fun assignTicket(@PathParam("ticketId") ticketId: UUID, request: AssignTicketRequest): Response {
        val ticket = orchestration.assignTicket(ticketId, request.assigneeId.toString(), Instant.now())
        return Response.ok(ticket.toResponse()).build()
    }

    @POST
    @Path("/{ticketId}/change-priority")
    fun changePriority(@PathParam("ticketId") ticketId: UUID, request: ChangePriorityRequest): Response {
        val ticket = orchestration.changePriority(ticketId, request.priority.toDomain(), Instant.now())
        return Response.ok(ticket.toResponse()).build()
    }

    @POST
    @Path("/{ticketId}/resolve")
    fun resolveTicket(@PathParam("ticketId") ticketId: UUID, request: ResolveTicketRequest): Response {
        val ticket = orchestration.resolveTicket(ticketId, request.resolutionSummary, Instant.now())
        return Response.ok(ticket.toResponse()).build()
    }

    @POST
    @Path("/{ticketId}/close")
    fun closeTicket(@PathParam("ticketId") ticketId: UUID): Response {
        val ticket = orchestration.closeTicket(ticketId, Instant.now())
        return Response.ok(ticket.toResponse()).build()
    }

    @POST
    @Path("/{ticketId}/reopen")
    fun reopenTicket(@PathParam("ticketId") ticketId: UUID): Response {
        val ticket = orchestration.reopenTicket(ticketId, Instant.now())
        return Response.ok(ticket.toResponse()).build()
    }
}

private fun com.crm.openapi.support.model.TicketPriority.toDomain(): com.crm.support.domain.ticket.TicketPriority =
    when (this) {
        com.crm.openapi.support.model.TicketPriority.CRITICAL -> com.crm.support.domain.ticket.TicketPriority.URGENT
        else -> com.crm.support.domain.ticket.TicketPriority.valueOf(this.name)
    }

private fun com.crm.support.domain.ticket.Ticket.toResponse(): TicketResponse {
    val sla = checkSla(Instant.now())
    return TicketResponse(
        ticketId = ticketId,
        requesterId = UUID.fromString(requesterId),
        subject = subject,
        description = description ?: "",
        status = com.crm.openapi.support.model.TicketStatus.valueOf(status.name),
        priority = when (priority) {
            com.crm.support.domain.ticket.TicketPriority.URGENT -> com.crm.openapi.support.model.TicketPriority.CRITICAL
            else -> com.crm.openapi.support.model.TicketPriority.valueOf(priority.name)
        },
        queueId = queueId?.let { UUID.fromString(it) },
        assigneeId = assigneeId?.let { UUID.fromString(it) },
        slaDeadline = sla.deadline?.let { java.time.OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
        createdAt = java.time.OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
        updatedAt = java.time.OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC),
        resolvedAt = resolvedAt?.let { java.time.OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
        slaStatus = SLAStatus(
            isBreached = sla.isBreached,
            breachTime = if (sla.isBreached) sla.deadline?.let {
                java.time.OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
            } else null,
            timeRemaining = if (!sla.isBreached) sla.timeRemaining?.toString() else null,
        ),
    )
}
