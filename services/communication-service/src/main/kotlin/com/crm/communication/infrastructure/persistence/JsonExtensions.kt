package com.crm.communication.infrastructure.persistence

import com.crm.communication.domain.event.NotificationDomainEvent
import com.crm.common.messaging.EventEnvelope

internal fun EventEnvelope<*>.toJson(): String {
    val payloadJson = when (val p = payload) {
        is NotificationDomainEvent -> p.toJson()
        else -> p.toString()
    }
    return """{"eventType":"$eventType","source":"$source","payload":$payloadJson}"""
}

internal fun NotificationDomainEvent.toJson(): String = when (this) {
    is NotificationDomainEvent.NotificationQueued -> """{"notificationId":"$notificationId","recipientId":"$recipientId","channelType":"$channelType","templateId":"${templateId ?: ""}","queuedAt":"$queuedAt"}"""
    is NotificationDomainEvent.NotificationSent -> """{"notificationId":"$notificationId","recipientId":"$recipientId","channelType":"$channelType","providerMessageId":"${providerMessageId ?: ""}","sentAt":"$sentAt"}"""
    is NotificationDomainEvent.NotificationDelivered -> """{"notificationId":"$notificationId","deliveredAt":"$deliveredAt"}"""
    is NotificationDomainEvent.NotificationFailed -> """{"notificationId":"$notificationId","channelType":"$channelType","reason":"${reason ?: ""}","retryCount":$retryCount,"failedAt":"$failedAt"}"""
    is NotificationDomainEvent.NotificationBounced -> """{"notificationId":"$notificationId","channelType":"$channelType","reason":"${reason ?: ""}","bouncedAt":"$bouncedAt"}"""
    is NotificationDomainEvent.MessageFailed -> """{"notificationId":"$notificationId","channelType":"$channelType","reason":"${reason ?: ""}","failedAt":"$failedAt"}"""
}
