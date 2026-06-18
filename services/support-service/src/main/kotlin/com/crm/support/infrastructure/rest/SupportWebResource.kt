package com.crm.support.infrastructure.rest

import com.crm.support.application.TicketOrchestrationService
import com.crm.support.domain.ticket.TicketPriority
import com.crm.support.domain.ticket.TicketStatus
import com.crm.support.infrastructure.persistence.TicketRepository
import io.quarkus.qute.Template
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Serves Qute-rendered HTML pages for the Support web UI.
 *
 * These endpoints return full HTML pages for browser navigation, while the
 * underlying REST API endpoints in [TicketResource] serve JSON.
 *
 * ## Routing convention
 * - `GET /support/tickets` → full page with ticket list
 * - `GET /support/tickets?page=2&size=25&q=login&status=OPEN&priority=HIGH` → filtered
 * - `GET /support/tickets/{id}` → ticket detail page
 */
@Path("/support")
class SupportWebResource @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val orchestration: TicketOrchestrationService,
) {

    @Inject
    @io.quarkus.qute.Location("tickets/list.html")
    lateinit var listTemplate: Template

    @Inject
    @io.quarkus.qute.Location("tickets/detail.html")
    lateinit var detailTemplate: Template

    // ── Ticket list ─────────────────────────────────────────────────────────

    @GET
    @Path("/tickets")
    @Produces(MediaType.TEXT_HTML)
    fun listTickets(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("size") @DefaultValue("25") size: Int,
        @QueryParam("q") q: String? = null,
        @QueryParam("status") statusFilter: String? = null,
        @QueryParam("priority") priorityFilter: String? = null,
    ): Response {
        val status = statusFilter?.let { runCatching { TicketStatus.valueOf(it) }.getOrNull() }
        val priority = priorityFilter?.let { runCatching { TicketPriority.valueOf(it) }.getOrNull() }

        val allTickets = ticketRepository.findAll()
        val filtered = allTickets.filter { ticket ->
            val matchesQuery = q.isNullOrBlank() ||
                ticket.subject.contains(q, ignoreCase = true) ||
                ticket.description?.contains(q, ignoreCase = true) == true
            val matchesStatus = status == null || ticket.status == status
            val matchesPriority = priority == null || ticket.priority == priority
            matchesQuery && matchesStatus && matchesPriority
        }

        val total = filtered.size
        val totalPages = if (total == 0) 1 else kotlin.math.ceil(total.toDouble() / size).toInt()
        val safePage = page.coerceIn(1, totalPages)
        val offset = (safePage - 1) * size
        val pageItems = filtered.drop(offset).take(size)

        val pages = buildPageRange(safePage, totalPages)

        val html = listTemplate
            .data("tickets", pageItems.map { it.toWebTicket() })
            .data("page", safePage)
            .data("size", size)
            .data("totalPages", totalPages)
            .data("total", total)
            .data("pages", pages)
            .data("q", q)
            .data("selectedStatus", statusFilter)
            .data("selectedPriority", priorityFilter)
            .data("statuses", TicketStatus.entries.map { StatusInfo(it.name, statusLabel(it)) })
            .data("priorities", TicketPriority.entries.map { PriorityInfo(it.name, priorityLabel(it)) })
            .data("activePage", "tickets")
            .render()

        return Response.ok(html).build()
    }

    // ── Ticket detail ───────────────────────────────────────────────────────

    @GET
    @Path("/tickets/{id}")
    @Produces(MediaType.TEXT_HTML)
    fun getTicket(@PathParam("id") id: UUID): Response {
        val ticket = orchestration.findById(id)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity("<html><body><h1>Ticket not found</h1><a href='/support/tickets'>← Back</a></body></html>")
                .build()

        val html = detailTemplate
            .data("ticket", ticket.toWebTicket())
            .data("statuses", TicketStatus.entries.map { StatusInfo(it.name, statusLabel(it)) })
            .data("priorities", TicketPriority.entries.map { PriorityInfo(it.name, priorityLabel(it)) })
            .data("activePage", "tickets")
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

    private fun statusLabel(status: TicketStatus): String = when (status) {
        TicketStatus.OPEN -> "Open"
        TicketStatus.IN_PROGRESS -> "In Progress"
        TicketStatus.PENDING_CUSTOMER -> "Pending Customer"
        TicketStatus.RESOLVED -> "Resolved"
        TicketStatus.CLOSED -> "Closed"
    }

    private fun priorityLabel(priority: TicketPriority): String = when (priority) {
        TicketPriority.LOW -> "Low"
        TicketPriority.MEDIUM -> "Medium"
        TicketPriority.HIGH -> "High"
        TicketPriority.URGENT -> "Critical"
    }

    private fun com.crm.support.domain.ticket.Ticket.toWebTicket() = WebTicket(
        id = ticketId.toString(),
        subject = subject,
        status = status,
        statusLabel = statusLabel(status),
        priority = priority,
        priorityLabel = priorityLabel(priority),
        assigneeId = assigneeId,
        requesterId = requesterId,
        queueId = queueId,
        slaDeadline = slaDeadline?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
        isSlaBreached = checkSla(Instant.now()).isBreached,
        createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt),
        updatedAt = DateTimeFormatter.ISO_INSTANT.format(updatedAt),
    )
}

// ── Web-facing DTOs (flattened for template consumption) ────────────────────

data class WebTicket(
    val id: String,
    val subject: String,
    val status: TicketStatus,
    val statusLabel: String,
    val priority: TicketPriority,
    val priorityLabel: String,
    val assigneeId: String?,
    val requesterId: String,
    val queueId: String?,
    val slaDeadline: String?,
    val isSlaBreached: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

data class StatusInfo(val name: String, val label: String)

data class PriorityInfo(val name: String, val label: String)
