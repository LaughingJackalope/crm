package com.crm.support.domain
import java.util.UUID

/**
 * Sealed exception hierarchy for Support domain errors.
 */
sealed class SupportDomainException(
    override val message: String,
) : RuntimeException(message) {

    class InvalidTicketState(
        val ticketId: UUID,
        val currentStatus: String,
        val requiredStatus: String,
    ) : SupportDomainException(
        "Ticket $ticketId is in status $currentStatus, required: $requiredStatus"
    )

    class TicketNotFound(
        val ticketId: UUID,
    ) : SupportDomainException("Ticket $ticketId not found")

    class DuplicateTicket(
        val externalId: String,
    ) : SupportDomainException("A ticket already exists for external reference: $externalId")

    class InvalidSlaCalculation(
        val detail: String,
    ) : SupportDomainException("SLA calculation failed: $detail")

    class ConsumerProcessingException(
        override val message: String,
        override val cause: Throwable? = null,
    ) : SupportDomainException(message)
}
