package com.crm.support.infrastructure.persistence

import com.crm.support.domain.ticket.SlaStatus
import com.crm.support.domain.ticket.Ticket
import com.crm.support.domain.ticket.TicketPriority
import com.crm.support.domain.ticket.TicketStatus
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for Ticket — maps to the `crm_support.ticket` table.
 */
@Entity
@Table(name = "ticket", schema = "support")
class TicketEntity : PanacheEntityBase {

    @Id
    @Column(name = "ticket_id", nullable = false)
    lateinit var ticketId: UUID

    @Column(name = "requester_id", nullable = false, length = 36)
    lateinit var requesterId: String

    @Column(name = "assignee_id", length = 36)
    var assigneeId: String? = null

    @Column(name = "subject", nullable = false, length = 500)
    lateinit var subject: String

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    lateinit var status: TicketStatus

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    lateinit var priority: TicketPriority

    @Column(name = "queue_id", length = 36)
    var queueId: String? = null

    @Column(name = "sla_deadline")
    var slaDeadline: Instant? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null

    @Column(name = "closed_at")
    var closedAt: Instant? = null

    companion object : PanacheCompanion<TicketEntity> {
        fun findByRequesterId(requesterId: String): List<TicketEntity> =
            list("requesterId", requesterId)

        fun findByAssigneeId(assigneeId: String): List<TicketEntity> =
            list("assigneeId", assigneeId)

        fun findByStatus(status: TicketStatus): List<TicketEntity> =
            list("status", status)

        fun findOpenTickets(): List<TicketEntity> =
            list("status NOT IN (?1, ?2)", TicketStatus.CLOSED, TicketStatus.RESOLVED)

        fun findOverdueTickets(now: Instant): List<TicketEntity> =
            find("slaDeadline IS NOT NULL AND slaDeadline < ?1 AND status NOT IN (?2, ?3)",
                now, TicketStatus.CLOSED, TicketStatus.RESOLVED).list()
    }
}

// ── Mapping: Entity → Domain ──────────────────────────────────────────────────

fun TicketEntity.toDomain(): Ticket = Ticket(
    ticketId = ticketId,
    requesterId = requesterId,
    assigneeId = assigneeId,
    subject = subject,
    description = description,
    status = status,
    priority = priority,
    queueId = queueId,
    slaDeadline = slaDeadline,
    createdAt = createdAt,
    updatedAt = updatedAt,
    resolvedAt = resolvedAt,
    closedAt = closedAt,
)

// ── Mapping: Domain → Entity ──────────────────────────────────────────────────

fun Ticket.toEntity(): TicketEntity {
    val existing = TicketEntity.find("ticketId", ticketId).firstResult()
    val entity = existing ?: TicketEntity()

    entity.apply {
        ticketId = this@toEntity.ticketId
        requesterId = this@toEntity.requesterId
        assigneeId = this@toEntity.assigneeId
        subject = this@toEntity.subject
        description = this@toEntity.description
        status = this@toEntity.status
        priority = this@toEntity.priority
        queueId = this@toEntity.queueId
        slaDeadline = this@toEntity.slaDeadline
        if (existing == null) createdAt = this@toEntity.createdAt
        updatedAt = this@toEntity.updatedAt
        resolvedAt = this@toEntity.resolvedAt
        closedAt = this@toEntity.closedAt
    }

    return entity
}
