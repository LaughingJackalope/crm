package com.crm.support.domain.ticket

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class Ticket(
    val ticketId: UUID = UUID.randomUUID(),
    val requesterId: String,
    val assigneeId: String? = null,
    val subject: String,
    val description: String? = null,
    val status: TicketStatus = TicketStatus.OPEN,
    val priority: TicketPriority = TicketPriority.MEDIUM,
    val queueId: String? = null,
    val slaDeadline: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant? = null,
    val closedAt: Instant? = null,
) {
    init {
        require(subject.isNotBlank()) { "Ticket subject must not be blank" }
        require(requesterId.isNotBlank()) { "Requester ID must not be blank" }
    }

    fun assign(assigneeId: String, now: Instant): Ticket {
        if (status != TicketStatus.OPEN && status != TicketStatus.IN_PROGRESS && status != TicketStatus.PENDING_CUSTOMER) {
            throw IllegalStateException("Cannot assign ticket in status $status")
        }
        return copy(assigneeId = assigneeId, status = TicketStatus.IN_PROGRESS, updatedAt = now)
    }

    fun changePriority(newPriority: TicketPriority, newDeadline: Instant?, now: Instant): Ticket {
        if (status == TicketStatus.CLOSED || status == TicketStatus.RESOLVED) {
            throw IllegalStateException("Cannot change priority of a $status ticket")
        }
        return copy(priority = newPriority, slaDeadline = newDeadline, updatedAt = now)
    }

    fun escalate(escalatedTo: String, newDeadline: Instant?, now: Instant): Ticket {
        if (status == TicketStatus.CLOSED) throw IllegalStateException("Cannot escalate a closed ticket")
        return copy(
            assigneeId = escalatedTo,
            priority = escalatePriority(),
            slaDeadline = newDeadline,
            updatedAt = now,
        )
    }

    fun pendingCustomer(now: Instant): Ticket {
        if (status != TicketStatus.IN_PROGRESS) {
            throw IllegalStateException("Ticket must be IN_PROGRESS to set PENDING_CUSTOMER, got: $status")
        }
        return copy(status = TicketStatus.PENDING_CUSTOMER, updatedAt = now)
    }

    fun resolve(now: Instant): Ticket {
        if (status == TicketStatus.CLOSED || status == TicketStatus.RESOLVED) {
            throw IllegalStateException("Cannot resolve a $status ticket")
        }
        return copy(status = TicketStatus.RESOLVED, resolvedAt = now, updatedAt = now)
    }

    fun close(now: Instant): Ticket {
        if (status != TicketStatus.RESOLVED) {
            throw IllegalStateException("Only RESOLVED tickets can be closed, got: $status")
        }
        return copy(status = TicketStatus.CLOSED, closedAt = now, updatedAt = now)
    }

    fun reopen(now: Instant): Ticket {
        if (status != TicketStatus.CLOSED && status != TicketStatus.RESOLVED) {
            throw IllegalStateException("Only CLOSED or RESOLVED tickets can be reopened, got: $status")
        }
        return copy(
            status = TicketStatus.OPEN, assigneeId = null,
            resolvedAt = null, closedAt = null, slaDeadline = null, updatedAt = now,
        )
    }

    fun checkSla(now: Instant): SlaStatus {
        val deadline = slaDeadline ?: return SlaStatus(isBreached = false)
        if (closedAt != null || resolvedAt != null) return SlaStatus(isBreached = false)
        return if (now.isAfter(deadline)) {
            SlaStatus(isBreached = true, deadline = deadline,
                breachDuration = Duration.between(deadline, now))
        } else {
            SlaStatus(isBreached = false, deadline = deadline,
                timeRemaining = Duration.between(now, deadline))
        }
    }

    private fun escalatePriority(): TicketPriority = when (priority) {
        TicketPriority.LOW -> TicketPriority.MEDIUM
        TicketPriority.MEDIUM -> TicketPriority.HIGH
        TicketPriority.HIGH, TicketPriority.URGENT -> TicketPriority.URGENT
    }
}

enum class TicketStatus { OPEN, IN_PROGRESS, PENDING_CUSTOMER, RESOLVED, CLOSED }
enum class TicketPriority { LOW, MEDIUM, HIGH, URGENT }

data class SlaStatus(
    val isBreached: Boolean,
    val deadline: Instant? = null,
    val timeRemaining: Duration? = null,
    val breachDuration: Duration? = null,
)

object SlaEngine {

    fun calculateDeadline(createdAt: Instant, priority: TicketPriority, tier: CustomerTier): Instant {
        val hours = getSlaHours(priority, tier)
        return createdAt.plus(Duration.ofHours(hours))
    }

    fun recalculateDeadline(createdAt: Instant, newPriority: TicketPriority, newTier: CustomerTier): Instant =
        calculateDeadline(createdAt, newPriority, newTier)

    private fun getSlaHours(priority: TicketPriority, tier: CustomerTier): Long =
        when (priority) {
            TicketPriority.URGENT -> when (tier) {
                CustomerTier.ENTERPRISE -> 1L; CustomerTier.PREMIUM -> 2L; CustomerTier.STANDARD -> 4L
            }
            TicketPriority.HIGH -> when (tier) {
                CustomerTier.ENTERPRISE -> 4L; CustomerTier.PREMIUM -> 12L; CustomerTier.STANDARD -> 24L
            }
            TicketPriority.MEDIUM -> when (tier) {
                CustomerTier.ENTERPRISE -> 12L; CustomerTier.PREMIUM -> 24L; CustomerTier.STANDARD -> 48L
            }
            TicketPriority.LOW -> when (tier) {
                CustomerTier.ENTERPRISE -> 24L; CustomerTier.PREMIUM -> 48L; CustomerTier.STANDARD -> 72L
            }
        }
}

enum class CustomerTier { STANDARD, PREMIUM, ENTERPRISE }
