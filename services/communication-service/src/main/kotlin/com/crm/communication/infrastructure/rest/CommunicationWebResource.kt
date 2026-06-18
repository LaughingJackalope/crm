package com.crm.communication.infrastructure.rest

import com.crm.communication.domain.notification.NotificationChannel
import com.crm.communication.infrastructure.persistence.NotificationRepository
import com.crm.communication.domain.notification.NotificationStatus
import io.quarkus.qute.Template
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Serves Qute-rendered HTML pages for the Communication web UI.
 *
 * ## Routing convention
 * - `GET /comms/messages` → full page with message list
 * - `GET /comms/messages?page=2&size=25&q=hello&channel=EMAIL&status=SENT` → filtered
 * - `GET /comms/messages/{id}` → message detail page
 */
@Path("/comms")
class CommunicationWebResource @Inject constructor(
    private val notificationRepository: NotificationRepository,
) {

    @Inject
    @io.quarkus.qute.Location("messages/list.html")
    lateinit var listTemplate: Template

    @Inject
    @io.quarkus.qute.Location("messages/detail.html")
    lateinit var detailTemplate: Template

    // ── Message list ─────────────────────────────────────────────────────────

    @GET
    @Path("/messages")
    @Produces(MediaType.TEXT_HTML)
    fun listMessages(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("size") @DefaultValue("25") size: Int,
        @QueryParam("q") q: String? = null,
        @QueryParam("channel") channelFilter: String? = null,
        @QueryParam("direction") directionFilter: String? = null,
        @QueryParam("status") statusFilter: String? = null,
    ): Response {
        var notifications = notificationRepository.findAll()

        // Apply search filter (matches against body or subject)
        if (!q.isNullOrBlank()) {
            notifications = notifications.filter { n ->
                n.body.contains(q, ignoreCase = true) ||
                    (n.subject?.contains(q, ignoreCase = true) == true)
            }
        }

        // Apply channel filter
        if (!channelFilter.isNullOrBlank()) {
            val ch = runCatching { NotificationChannel.valueOf(channelFilter.uppercase()) }.getOrNull()
            if (ch != null) notifications = notifications.filter { it.channelType == ch }
        }

        // Apply direction filter: domain has no direction, all are OUTBOUND
        if (!directionFilter.isNullOrBlank() && directionFilter.equals("inbound", ignoreCase = true)) {
            notifications = emptyList()
        }

        // Apply status filter
        if (!statusFilter.isNullOrBlank()) {
            val st = runCatching { NotificationStatus.valueOf(statusFilter.uppercase()) }.getOrNull()
            if (st != null) notifications = notifications.filter { it.status == st }
        }

        val total = notifications.size
        val totalPages = if (total == 0) 1 else kotlin.math.ceil(total.toDouble() / size).toInt()
        val safePage = page.coerceIn(1, totalPages)
        val offset = (safePage - 1) * size
        val pageItems = notifications.drop(offset).take(size)

        val pages = buildPageRange(safePage, totalPages)

        val html = listTemplate
            .data("messages", pageItems.map { it.toWebMessage() })
            .data("page", safePage)
            .data("size", size)
            .data("totalPages", totalPages)
            .data("total", total)
            .data("pages", pages)
            .data("q", q)
            .data("selectedChannel", channelFilter)
            .data("selectedDirection", directionFilter)
            .data("selectedStatus", statusFilter)
            .data("channels", NotificationChannel.entries.map { ChannelInfo(it.name, channelLabel(it)) })
            .data("statuses", NotificationStatus.entries.map { StatusInfo(it.name, statusLabel(it)) })
            .data("activePage", "messages")
            .render()

        return Response.ok(html).build()
    }

    // ── Message detail ───────────────────────────────────────────────────────

    @GET
    @Path("/messages/{messageId}")
    @Produces(MediaType.TEXT_HTML)
    fun getMessage(@PathParam("messageId") messageId: UUID): Response {
        val notification = notificationRepository.findById(messageId)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity("<html><body><h1>Message not found</h1><a href='/comms/messages'>← Back</a></body></html>")
                .build()

        val html = detailTemplate
            .data("message", notification.toWebMessage())
            .data("activePage", "messages")
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

    private fun com.crm.communication.domain.notification.Notification.toWebMessage() = WebMessage(
        id = this.notificationId.toString(),
        recipientId = this.recipientId,
        channel = this.channelType,
        channelLabel = channelLabel(this.channelType),
        subject = this.subject,
        status = this.status,
        statusLabel = statusLabel(this.status),
        direction = "Outbound",
        sentAt = this.sentAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
        deliveredAt = this.deliveredAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
        createdAt = DateTimeFormatter.ISO_INSTANT.format(this.createdAt),
    )
}

// ── Web-facing DTOs (flattened for template consumption) ────────────────────

data class WebMessage(
    val id: String,
    val recipientId: String,
    val channel: NotificationChannel,
    val channelLabel: String,
    val subject: String?,
    val status: NotificationStatus,
    val statusLabel: String,
    val direction: String,
    val sentAt: String?,
    val deliveredAt: String?,
    val createdAt: String,
)

data class ChannelInfo(val name: String, val label: String)

data class StatusInfo(val name: String, val label: String)

// ── Label helpers ───────────────────────────────────────────────────────────

fun channelLabel(channel: NotificationChannel): String = when (channel) {
    NotificationChannel.EMAIL -> "Email"
    NotificationChannel.SMS -> "SMS"
    NotificationChannel.PUSH -> "Push"
    NotificationChannel.IN_APP -> "In-App"
    NotificationChannel.SLACK -> "Slack"
}

fun statusLabel(status: NotificationStatus): String = when (status) {
    NotificationStatus.PENDING -> "Pending"
    NotificationStatus.QUEUED -> "Queued"
    NotificationStatus.SENT -> "Sent"
    NotificationStatus.DELIVERED -> "Delivered"
    NotificationStatus.FAILED -> "Failed"
    NotificationStatus.BOUNCED -> "Bounced"
}
