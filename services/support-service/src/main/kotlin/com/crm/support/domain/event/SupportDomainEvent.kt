package com.crm.support.domain.event

import com.crm.common.messaging.Identifiable
import java.time.Instant
import java.util.UUID

/**
 * Sealed hierarchy of all domain events emitted by the Support bounded context.
 */
sealed interface SupportDomainEvent : Identifiable {

    data class TicketCreated(
        override val entityId: String,
        val ticketId: UUID,
        val requesterId: String,
        val subject: String,
        val priority: String,
        val slaDeadline: Instant?,
        val createdAt: Instant,
    ) : SupportDomainEvent

    data class TicketAssigned(
        override val entityId: String,
        val ticketId: UUID,
        val assigneeId: String?,
        val queueId: String?,
        val assignedAt: Instant,
    ) : SupportDomainEvent

    data class TicketStatusChanged(
        override val entityId: String,
        val ticketId: UUID,
        val fromStatus: String,
        val toStatus: String,
        val changedAt: Instant,
    ) : SupportDomainEvent

    data class TicketEscalated(
        override val entityId: String,
        val ticketId: UUID,
        val escalatedTo: String,
        val newPriority: String,
        val escalatedAt: Instant,
    ) : SupportDomainEvent

    data class TicketResolved(
        override val entityId: String,
        val ticketId: UUID,
        val resolution: String?,
        val resolvedAt: Instant,
    ) : SupportDomainEvent

    data class TicketClosed(
        override val entityId: String,
        val ticketId: UUID,
        val closedAt: Instant,
    ) : SupportDomainEvent

    data class TicketReopened(
        override val entityId: String,
        val ticketId: UUID,
        val reopenedAt: Instant,
    ) : SupportDomainEvent

    data class SlaBreached(
        override val entityId: String,
        val ticketId: UUID,
        val slaDeadline: Instant,
        val breachDurationSeconds: Long,
        val breachedAt: Instant,
    ) : SupportDomainEvent

    data class CsatSubmitted(
        override val entityId: String,
        val ticketId: UUID,
        val customerId: String,
        val score: Int,
        val submittedAt: Instant,
    ) : SupportDomainEvent
}
