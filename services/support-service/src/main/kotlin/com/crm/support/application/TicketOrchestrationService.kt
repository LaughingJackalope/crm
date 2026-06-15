package com.crm.support.application

import com.crm.support.domain.event.SupportDomainEvent
import com.crm.support.domain.event.SupportDomainEvent.*
import com.crm.support.domain.ticket.CustomerTier
import com.crm.support.domain.ticket.CustomerTierProjection
import com.crm.support.domain.ticket.SlaEngine
import com.crm.support.domain.ticket.Ticket
import com.crm.support.domain.ticket.TicketPriority
import com.crm.support.infrastructure.persistence.OutboxEventEntity
import com.crm.support.infrastructure.persistence.OutboxEventRepository
import com.crm.support.infrastructure.persistence.TicketRepository
import com.crm.common.messaging.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class TicketOrchestrationService @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val outboxRepository: OutboxEventRepository,
) {

    fun findById(ticketId: UUID): Ticket? = ticketRepository.findById(ticketId)

    @Transactional
    fun createTicket(
        requesterId: String,
        subject: String,
        description: String? = null,
        priority: TicketPriority = TicketPriority.MEDIUM,
        queueId: String? = null,
        now: Instant = Instant.now(),
    ): Ticket {
        val customerTier = resolveCustomerTier(requesterId)
        val slaDeadline = SlaEngine.calculateDeadline(now, priority, customerTier)

        val ticket = Ticket(
            requesterId = requesterId,
            subject = subject,
            description = description,
            priority = priority,
            queueId = queueId,
            slaDeadline = slaDeadline,
            createdAt = now,
            updatedAt = now,
        )

        val saved = ticketRepository.save(ticket)
        saveOutbox(
            TicketCreated(
                entityId = saved.ticketId.toString(),
                ticketId = saved.ticketId,
                requesterId = requesterId,
                subject = subject,
                priority = priority.name,
                slaDeadline = slaDeadline,
                createdAt = now,
            ), "ticket", now
        )
        return saved
    }

    @Transactional
    fun assignTicket(ticketId: UUID, assigneeId: String, now: Instant = Instant.now()): Ticket {
        val ticket = ticketRepository.findById(ticketId)
            ?: throw com.crm.support.domain.SupportDomainException.TicketNotFound(ticketId)
        val updated = ticket.assign(assigneeId, now)
        val saved = ticketRepository.save(updated)
        saveOutbox(
            TicketAssigned(
                entityId = saved.ticketId.toString(),
                ticketId = saved.ticketId,
                assigneeId = assigneeId,
                queueId = saved.queueId,
                assignedAt = now,
            ), "ticket", now
        )
        return saved
    }

    @Transactional
    fun changePriority(ticketId: UUID, newPriority: TicketPriority, now: Instant = Instant.now()): Ticket {
        val ticket = ticketRepository.findById(ticketId)
            ?: throw com.crm.support.domain.SupportDomainException.TicketNotFound(ticketId)
        val customerTier = resolveCustomerTier(ticket.requesterId)
        val newDeadline = SlaEngine.recalculateDeadline(ticket.createdAt, newPriority, customerTier)
        val updated = ticket.changePriority(newPriority, newDeadline, now)
        return ticketRepository.save(updated)
    }

    @Transactional
    fun resolveTicket(ticketId: UUID, resolution: String? = null, now: Instant = Instant.now()): Ticket {
        val ticket = ticketRepository.findById(ticketId)
            ?: throw com.crm.support.domain.SupportDomainException.TicketNotFound(ticketId)
        val updated = ticket.resolve(now)
        val saved = ticketRepository.save(updated)
        saveOutbox(
            TicketResolved(
                entityId = saved.ticketId.toString(),
                ticketId = saved.ticketId,
                resolution = resolution,
                resolvedAt = now,
            ), "ticket", now
        )
        return saved
    }

    @Transactional
    fun closeTicket(ticketId: UUID, now: Instant = Instant.now()): Ticket {
        val ticket = ticketRepository.findById(ticketId)
            ?: throw com.crm.support.domain.SupportDomainException.TicketNotFound(ticketId)
        val updated = ticket.close(now)
        val saved = ticketRepository.save(updated)
        saveOutbox(
            TicketClosed(
                entityId = saved.ticketId.toString(),
                ticketId = saved.ticketId,
                closedAt = now,
            ), "ticket", now
        )
        return saved
    }

    @Transactional
    fun reopenTicket(ticketId: UUID, now: Instant = Instant.now()): Ticket {
        val ticket = ticketRepository.findById(ticketId)
            ?: throw com.crm.support.domain.SupportDomainException.TicketNotFound(ticketId)
        val updated = ticket.reopen(now)
        return ticketRepository.save(updated)
    }

    private fun resolveCustomerTier(requesterId: String): CustomerTier {
        val projection = CustomerTierProjection.findByContactId(requesterId)
        return projection?.tier ?: CustomerTier.STANDARD
    }

    private fun saveOutbox(event: SupportDomainEvent, entityType: String, now: Instant) {
        outboxRepository.save(
            OutboxEventEntity().apply {
                eventId = UUID.randomUUID()
                entityId = event.entityId
                this.entityType = entityType
                eventType = event::class.simpleName!!
                source = "support"
                this@apply.payload = EventEnvelope(
                    eventType = event::class.simpleName!!,
                    source = "support",
                    correlationId = null,
                    actorId = null,
                    payload = event,
                ).toJson()
                this@apply.createdAt = now
            }
        )
    }
}
