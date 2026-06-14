package com.crm.support.domain.ticket

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class Ticket(
    val ticketId: UUID = UUID.randomUUID(),
    val requesterId: String,          // Reference to CIAM Contact
    val assigneeId: String? = null,
    val subject: String,
    val description: String,
    val status: TicketStatus = TicketStatus.OPEN,
    val priority: TicketPriority = TicketPriority.MEDIUM,
    val queueId: String? = null,
    val slaDeadline: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val resolvedAt: Instant? = null,
    val comments: MutableList<Comment> = mutableListOf(),
) {
    fun assign(agentId: String): Ticket =
        copy(assigneeId = agentId, updatedAt = Instant.now())

    fun changePriority(newPriority: TicketPriority, slaDeadline: Instant?): Ticket =
        copy(priority = newPriority, slaDeadline = slaDeadline, updatedAt = Instant.now())

    fun escalate(escalatedTo: String): Ticket =
        copy(assigneeId = escalatedTo, priority = TicketPriority.CRITICAL, updatedAt = Instant.now())

    fun resolve(resolution: String): Ticket =
        copy(status = TicketStatus.RESOLVED, resolvedAt = Instant.now(), updatedAt = Instant.now())

    fun close(): Ticket =
        copy(status = TicketStatus.CLOSED, updatedAt = Instant.now())

    fun reopen(): Ticket =
        copy(status = TicketStatus.OPEN, resolvedAt = null, updatedAt = Instant.now())

    fun addComment(authorId: String, body: String, isInternal: Boolean = false): Ticket {
        comments.add(Comment(authorId = authorId, body = body, isInternal = isInternal))
        return copy(updatedAt = Instant.now())
    }

    val slaStatus: SLAStatus
        get() = when {
            slaDeadline == null -> SLAStatus(isBreached = false, timeRemaining = null)
            resolvedAt != null -> SLAStatus(isBreached = false, timeRemaining = null)
            Instant.now().isAfter(slaDeadline) -> SLAStatus(isBreached = true, breachTime = slaDeadline, timeRemaining = null)
            else -> SLAStatus(isBreached = false, timeRemaining = Duration.between(Instant.now(), slaDeadline))
        }
}

data class Comment(
    val commentId: UUID = UUID.randomUUID(),
    val authorId: String,
    val body: String,
    val isInternal: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

data class SLAStatus(
    val isBreached: Boolean,
    val breachTime: Instant? = null,
    val timeRemaining: Duration? = null,
)

data class CSATRating(
    val score: Int,
    val comment: String? = null,
    val submittedAt: Instant = Instant.now(),
) {
    init { require(score in 1..5) { "CSAT score must be 1-5" } }
}

enum class TicketStatus { OPEN, PENDING, RESOLVED, CLOSED }
enum class TicketPriority { LOW, MEDIUM, HIGH, CRITICAL }
