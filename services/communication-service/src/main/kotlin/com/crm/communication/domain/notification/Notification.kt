package com.crm.communication.domain.notification

import java.time.Instant
import java.util.UUID

/**
 * Notification Aggregate Root.
 *
 * Tracks the lifecycle of a notification dispatched to a customer via
 * email, SMS, push, or in-app channels. Supports retry semantics and
 * template variable substitution.
 *
 * ## State Machine
 * PENDING → QUEUED → SENT → DELIVERED
 *                    ↘ FAILED → (retry) → QUEUED
 *
 * ## Invariants
 * - recipientId must not be blank.
 * - body must not be blank.
 * - retryCount is always non-negative.
 * - A SENT or DELIVERED notification cannot be re-queued.
 */
data class Notification(
    val notificationId: UUID = UUID.randomUUID(),
    val recipientId: String,
    val channelType: NotificationChannel,
    val templateId: String? = null,
    val subject: String? = null,
    val body: String,
    val status: NotificationStatus = NotificationStatus.PENDING,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val templateVariables: Map<String, String> = emptyMap(),
    val providerMessageId: String? = null,
    val failureReason: String? = null,
    val sentAt: Instant? = null,
    val deliveredAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    init {
        require(recipientId.isNotBlank()) { "Recipient ID must not be blank" }
        require(body.isNotBlank()) { "Notification body must not be blank" }
        require(retryCount >= 0) { "Retry count must be non-negative" }
        require(maxRetries >= 0) { "Max retries must be non-negative" }
    }

    fun queue(): Notification {
        check(status == NotificationStatus.PENDING || status == NotificationStatus.FAILED) {
            "Cannot queue a notification in status $status"
        }
        return copy(status = NotificationStatus.QUEUED, updatedAt = Instant.now())
    }

    fun markSent(providerMessageId: String, now: Instant): Notification {
        check(status == NotificationStatus.QUEUED) {
            "Only QUEUED notifications can be marked sent, got: $status"
        }
        return copy(
            status = NotificationStatus.SENT, providerMessageId = providerMessageId,
            sentAt = now, updatedAt = now,
        )
    }

    fun markDelivered(now: Instant): Notification {
        check(status == NotificationStatus.SENT) {
            "Only SENT notifications can be marked delivered, got: $status"
        }
        return copy(status = NotificationStatus.DELIVERED, deliveredAt = now, updatedAt = now)
    }

    fun markFailed(reason: String, now: Instant): Notification {
        check(status == NotificationStatus.QUEUED || status == NotificationStatus.SENT) {
            "Cannot mark failed from status $status"
        }
        return if (retryCount < maxRetries) {
            copy(status = NotificationStatus.FAILED, retryCount = retryCount + 1,
                failureReason = reason, updatedAt = now)
        } else {
            copy(status = NotificationStatus.FAILED, failureReason = reason,
                updatedAt = now)
        }
    }

    fun canRetry(): Boolean =
        status == NotificationStatus.FAILED && retryCount < maxRetries

    fun retry(now: Instant): Notification {
        check(canRetry()) {
            "Cannot retry: status=$status, retryCount=$retryCount, maxRetries=$maxRetries"
        }
        return copy(status = NotificationStatus.QUEUED, updatedAt = now)
    }

    fun markBounced(reason: String, now: Instant): Notification {
        return copy(status = NotificationStatus.BOUNCED, failureReason = reason, updatedAt = now)
    }
}

enum class NotificationChannel { EMAIL, SMS, PUSH, IN_APP, SLACK }

enum class NotificationStatus { PENDING, QUEUED, SENT, DELIVERED, FAILED, BOUNCED }
