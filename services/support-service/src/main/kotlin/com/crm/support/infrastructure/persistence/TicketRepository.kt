package com.crm.support.infrastructure.persistence

import com.crm.support.domain.ticket.Ticket
import com.crm.support.domain.ticket.TicketStatus
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class TicketRepository {

    fun findById(id: UUID): Ticket? =
        TicketEntity.find("ticketId", id).firstResult()?.toDomain()

    fun findByRequesterId(requesterId: String): List<Ticket> =
        TicketEntity.findByRequesterId(requesterId).map { it.toDomain() }

    fun findByAssigneeId(assigneeId: String): List<Ticket> =
        TicketEntity.findByAssigneeId(assigneeId).map { it.toDomain() }

    fun findByStatus(status: TicketStatus): List<Ticket> =
        TicketEntity.findByStatus(status).map { it.toDomain() }

    fun findOpenTickets(): List<Ticket> =
        TicketEntity.findOpenTickets().map { it.toDomain() }

    fun findOverdueTickets(now: Instant): List<Ticket> =
        TicketEntity.findOverdueTickets(now).map { it.toDomain() }

    fun save(ticket: Ticket): Ticket {
        val entity = ticket.toEntity()
        entity.persist()
        return entity.toDomain()
    }

    fun delete(id: UUID) {
        TicketEntity.delete("ticketId", id)
    }
}
