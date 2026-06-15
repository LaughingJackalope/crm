package com.crm.communication.infrastructure.persistence

import com.crm.communication.domain.notification.Notification
import com.crm.communication.domain.notification.NotificationChannel
import com.crm.communication.domain.notification.NotificationStatus
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notification", schema = "communication")
class NotificationEntity : PanacheEntityBase {

    @Id
    @Column(name = "notification_id", nullable = false)
    lateinit var notificationId: UUID

    @Column(name = "recipient_id", nullable = false, length = 36)
    lateinit var recipientId: String

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 16)
    lateinit var channelType: NotificationChannel

    @Column(name = "template_id", length = 64)
    var templateId: String? = null

    @Column(name = "subject", length = 500)
    var subject: String? = null

    @Column(name = "body", nullable = false, columnDefinition = "text")
    lateinit var body: String

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    lateinit var status: NotificationStatus

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0

    @Column(name = "max_retries", nullable = false)
    var maxRetries: Int = 3

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_template_vars", schema = "communication",
        joinColumns = [JoinColumn(name = "notification_id")])
    @MapKeyColumn(name = "var_key", length = 64)
    @Column(name = "var_value", length = 500)
    var templateVariables: MutableMap<String, String> = mutableMapOf()

    @Column(name = "provider_message_id", length = 128)
    var providerMessageId: String? = null

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null

    @Column(name = "sent_at")
    var sentAt: Instant? = null

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant

    companion object : PanacheCompanion<NotificationEntity> {
        fun findByStatus(status: NotificationStatus): List<NotificationEntity> =
            list("status", status)

        fun findRetryable(): List<NotificationEntity> =
            list("status = ?1 AND retryCount < ?2", NotificationStatus.FAILED, 3)

        fun findByRecipientId(recipientId: String): List<NotificationEntity> =
            list("recipientId", recipientId)
    }
}

fun NotificationEntity.toDomain(): Notification = Notification(
    notificationId = notificationId, recipientId = recipientId,
    channelType = channelType, templateId = templateId, subject = subject,
    body = body, status = status, retryCount = retryCount, maxRetries = maxRetries,
    templateVariables = templateVariables.toMap(), providerMessageId = providerMessageId,
    failureReason = failureReason, sentAt = sentAt, deliveredAt = deliveredAt,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun Notification.toEntity(): NotificationEntity {
    val existing = NotificationEntity.find("notificationId", notificationId).firstResult()
    val entity = existing ?: NotificationEntity()
    entity.apply {
        notificationId = this@toEntity.notificationId
        recipientId = this@toEntity.recipientId
        channelType = this@toEntity.channelType
        templateId = this@toEntity.templateId
        subject = this@toEntity.subject
        body = this@toEntity.body
        status = this@toEntity.status
        retryCount = this@toEntity.retryCount
        maxRetries = this@toEntity.maxRetries
        templateVariables = this@toEntity.templateVariables.toMutableMap()
        providerMessageId = this@toEntity.providerMessageId
        failureReason = this@toEntity.failureReason
        sentAt = this@toEntity.sentAt
        deliveredAt = this@toEntity.deliveredAt
        if (existing == null) createdAt = this@toEntity.createdAt
        updatedAt = this@toEntity.updatedAt
    }
    return entity
}
