package com.crm.support.application

import com.crm.support.domain.event.SupportDomainEvent
import com.crm.common.messaging.EventEnvelope

internal fun EventEnvelope<*>.toJson(): String {
    val payloadJson = when (val p = payload) {
        is SupportDomainEvent -> p.toJson()
        else -> p.toString()
    }
    return """{"eventType":"$eventType","source":"$source","payload":$payloadJson}"""
}

internal fun SupportDomainEvent.toJson(): String = when (this) {
    is SupportDomainEvent.TicketCreated -> """{"ticketId":"$ticketId","requesterId":"$requesterId","subject":"$subject","priority":"$priority","slaDeadline":"${slaDeadline ?: ""}","createdAt":"$createdAt"}"""
    is SupportDomainEvent.TicketAssigned -> """{"ticketId":"$ticketId","assigneeId":"${assigneeId ?: ""}","queueId":"${queueId ?: ""}","assignedAt":"$assignedAt"}"""
    is SupportDomainEvent.TicketStatusChanged -> """{"ticketId":"$ticketId","fromStatus":"$fromStatus","toStatus":"$toStatus","changedAt":"$changedAt"}"""
    is SupportDomainEvent.TicketEscalated -> """{"ticketId":"$ticketId","escalatedTo":"$escalatedTo","newPriority":"$newPriority","escalatedAt":"$escalatedAt"}"""
    is SupportDomainEvent.TicketResolved -> """{"ticketId":"$ticketId","resolution":"${resolution ?: ""}","resolvedAt":"$resolvedAt"}"""
    is SupportDomainEvent.TicketClosed -> """{"ticketId":"$ticketId","closedAt":"$closedAt"}"""
    is SupportDomainEvent.TicketReopened -> """{"ticketId":"$ticketId","reopenedAt":"$reopenedAt"}"""
    is SupportDomainEvent.SlaBreached -> """{"ticketId":"$ticketId","slaDeadline":"$slaDeadline","breachDurationSeconds":$breachDurationSeconds,"breachedAt":"$breachedAt"}"""
    is SupportDomainEvent.CsatSubmitted -> """{"ticketId":"$ticketId","customerId":"$customerId","score":$score,"submittedAt":"$submittedAt"}"""
}
