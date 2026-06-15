package com.crm.communication.domain.event

import com.crm.common.messaging.Identifiable
import java.time.Instant
import java.util.UUID

sealed interface NotificationDomainEvent : Identifiable {

    data class NotificationQueued(
        override val entityId: String,
        val notificationId: UUID,
        val recipientId: String,
        val channelType: String,
        val templateId: String?,
        val queuedAt: Instant,
    ) : NotificationDomainEvent

    data class NotificationSent(
        override val entityId: String,
        val notificationId: UUID,
        val recipientId: String,
        val channelType: String,
        val providerMessageId: String?,
        val sentAt: Instant,
    ) : NotificationDomainEvent

    data class NotificationDelivered(
        override val entityId: String,
        val notificationId: UUID,
        val deliveredAt: Instant,
    ) : NotificationDomainEvent

    data class NotificationFailed(
        override val entityId: String,
        val notificationId: UUID,
        val channelType: String,
        val reason: String,
        val retryCount: Int,
        val failedAt: Instant,
    ) : NotificationDomainEvent

    data class NotificationBounced(
        override val entityId: String,
        val notificationId: UUID,
        val channelType: String,
        val reason: String,
        val bouncedAt: Instant,
    ) : NotificationDomainEvent

    data class MessageFailed(
        override val entityId: String,
        val notificationId: UUID,
        val channelType: String,
        val reason: String,
        val failedAt: Instant,
    ) : NotificationDomainEvent
}
