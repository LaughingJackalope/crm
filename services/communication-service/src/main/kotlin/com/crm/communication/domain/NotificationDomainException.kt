package com.crm.communication.domain

import java.util.UUID

sealed class NotificationDomainException(
    override val message: String,
) : RuntimeException(message) {

    class InvalidNotificationState(
        val notificationId: UUID,
        val currentStatus: String,
        val requiredStatus: String,
    ) : NotificationDomainException(
        "Notification $notificationId is in status $currentStatus, required: $requiredStatus"
    )

    class NotificationNotFound(
        val notificationId: UUID,
    ) : NotificationDomainException("Notification $notificationId not found")

    class MaxRetriesExceeded(
        val notificationId: UUID,
        val maxRetries: Int,
    ) : NotificationDomainException(
        "Notification $notificationId exceeded max retries: $maxRetries"
    )

    class TemplateRenderException(
        val templateId: String,
        val reason: String,
    ) : NotificationDomainException("Template '$templateId' render failed: $reason")

    class ProviderException(
        val provider: String,
        val reason: String,
    ) : NotificationDomainException("Provider '$provider' failed: $reason")

    class ConsumerProcessingException(
        override val message: String,
        override val cause: Throwable? = null,
    ) : NotificationDomainException(message)
}
