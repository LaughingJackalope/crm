package com.crm.communication.infrastructure.persistence

import com.crm.communication.domain.notification.Notification
import com.crm.communication.domain.notification.NotificationStatus
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class NotificationRepository {
    fun findById(id: UUID): Notification? =
        NotificationEntity.find("notificationId", id).firstResult()?.toDomain()

    fun findByStatus(status: NotificationStatus): List<Notification> =
        NotificationEntity.findByStatus(status).map { it.toDomain() }

    fun findRetryable(): List<Notification> =
        NotificationEntity.findRetryable().map { it.toDomain() }

    fun findByRecipientId(recipientId: String): List<Notification> =
        NotificationEntity.findByRecipientId(recipientId).map { it.toDomain() }
    fun findAll(): List<Notification> =
        NotificationEntity.listAllSorted().map { it.toDomain() }

    fun save(notification: Notification): Notification {
        val entity = notification.toEntity()
        entity.persist()
        return entity.toDomain()
    }

    fun delete(id: UUID) { NotificationEntity.delete("notificationId", id) }
}
