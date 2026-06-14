package com.crm.communication.domain.notification

import java.time.Instant
import java.util.UUID

data class Notification(
    val notificationId: UUID = UUID.randomUUID(),
    val recipientId: String,         // Reference to CIAM Contact
    val channel: NotificationChannel,
    val templateId: String? = null,
    val subject: String? = null,
    val body: String,
    val status: NotificationStatus = NotificationStatus.PENDING,
    val sentAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
) {
    fun markSent(): Notification =
        copy(status = NotificationStatus.SENT, sentAt = Instant.now())

    fun markFailed(reason: String): Notification =
        copy(status = NotificationStatus.FAILED)

    fun markDelivered(): Notification =
        copy(status = NotificationStatus.DELIVERED)
}

enum class NotificationChannel { EMAIL, SMS, PUSH, IN_APP }
enum class NotificationStatus { PENDING, SENT, DELIVERED, FAILED, BOUNCED }
