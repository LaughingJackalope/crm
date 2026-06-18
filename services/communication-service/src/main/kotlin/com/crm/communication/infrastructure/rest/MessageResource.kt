package com.crm.communication.infrastructure.rest

import com.crm.communication.domain.notification.Notification
import com.crm.communication.domain.notification.NotificationChannel
import com.crm.communication.infrastructure.persistence.NotificationRepository
import com.crm.communication.domain.notification.NotificationStatus
import com.crm.openapi.communication.model.*
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * RESTEasy Reactive endpoint for message (notification) operations.
 *
 * Uses OpenAPI-generated DTOs from [com.crm.openapi.communication.model] for all
 * request/response bodies, ensuring the HTTP contract is always in sync
 * with the API specification.
 */
@Path("/api/v1/messages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class MessageResource @Inject constructor(
    private val notificationRepository: NotificationRepository,
) {

    @GET
    fun listMessages(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("size") @DefaultValue("25") size: Int,
        @QueryParam("customerId") customerId: String? = null,
        @QueryParam("channel") channel: String? = null,
        @QueryParam("direction") direction: String? = null,
        @QueryParam("status") status: String? = null,
        @QueryParam("sort") @DefaultValue("-createdAt") sort: String? = null,
    ): Response {
        var notifications = notificationRepository.findAll()

        // Apply filters
        if (!customerId.isNullOrBlank()) {
            notifications = notifications.filter { it.recipientId == customerId }
        }
        if (!channel.isNullOrBlank()) {
            val ch = runCatching { NotificationChannel.valueOf(channel.uppercase()) }.getOrNull()
            if (ch != null) notifications = notifications.filter { it.channelType == ch }
        }
        if (!status.isNullOrBlank()) {
            val st = runCatching { NotificationStatus.valueOf(status.uppercase()) }.getOrNull()
            if (st != null) notifications = notifications.filter { it.status == st }
        }
        // Direction filter: domain doesn't have direction; all are OUTBOUND
        // If direction=inbound, return empty; if direction=outbound or null, keep all
        if (!direction.isNullOrBlank() && direction.equals("inbound", ignoreCase = true)) {
            notifications = emptyList()
        }

        // Apply sorting
        notifications = when (sort) {
            "createdAt" -> notifications.sortedBy { it.createdAt }
            "-createdAt" -> notifications.sortedByDescending { it.createdAt }
            "sentAt" -> notifications.sortedBy { it.sentAt }
            "-sentAt" -> notifications.sortedByDescending { it.sentAt }
            else -> notifications.sortedByDescending { it.createdAt }
        }

        val total = notifications.size
        val totalPages = if (total == 0) 1 else kotlin.math.ceil(total.toDouble() / size).toInt()
        val safePage = page.coerceIn(1, totalPages)
        val offset = (safePage - 1) * size
        val pageItems = notifications.drop(offset).take(size)

        val response = PaginatedMessages(
            items = pageItems.map { it.toResponse() },
            page = safePage,
            pageSize = size,
            totalItems = total,
            totalPages = totalPages,
        )

        return Response.ok(response).build()
    }

    @GET
    @Path("/{messageId}")
    fun getMessage(@PathParam("messageId") messageId: UUID): Response {
        val notification = notificationRepository.findById(messageId)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(
                    ErrorResponse(
                        error = ErrorDetail(
                            code = "not-found",
                            message = "Message $messageId not found",
                            target = "messageId",
                        ),
                    )
                ).build()
        return Response.ok(notification.toResponse()).build()
    }
}

// ── Mapping Extensions: Domain ↔ OpenAPI DTO ────────────────────────────────

/**
 * Map domain [NotificationStatus] to OpenAPI [MessageStatus].
 * PENDING → Queued, QUEUED → Queued, SENT → Sent, DELIVERED → Delivered,
 * FAILED → Failed, BOUNCED → Failed.
 */
private fun NotificationStatus.toOpenApiStatus(): MessageStatus = when (this) {
    NotificationStatus.PENDING -> MessageStatus.QUEUED
    NotificationStatus.QUEUED -> MessageStatus.QUEUED
    NotificationStatus.SENT -> MessageStatus.SENT
    NotificationStatus.DELIVERED -> MessageStatus.DELIVERED
    NotificationStatus.FAILED -> MessageStatus.FAILED
    NotificationStatus.BOUNCED -> MessageStatus.FAILED
}

/**
 * Map domain [NotificationChannel] to OpenAPI [Channel].
 */
private fun NotificationChannel.toOpenApiChannel(): Channel = when (this) {
    NotificationChannel.EMAIL -> Channel.EMAIL
    NotificationChannel.SMS -> Channel.SMS
    NotificationChannel.PUSH -> Channel.PUSH
    NotificationChannel.IN_APP -> Channel.IN_APP
    NotificationChannel.SLACK -> Channel.WHATS_APP
}

/**
 * Convert a domain [Notification] to the OpenAPI [MessageResponse] DTO.
 */
fun Notification.toResponse(): MessageResponse {
    return MessageResponse(
        messageId = this.notificationId,
        customerId = runCatching { java.util.UUID.fromString(this.recipientId) }.getOrNull() ?: java.util.UUID.nameUUIDFromBytes(this.recipientId.toByteArray()),
        channel = this.channelType.toOpenApiChannel(),
        direction = Direction.OUTBOUND,
        subject = this.subject,
        body = this.body,
        status = this.status.toOpenApiStatus(),
        sentAt = this.sentAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
        deliveredAt = this.deliveredAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
        createdAt = OffsetDateTime.ofInstant(this.createdAt, ZoneOffset.UTC),
    )
}
