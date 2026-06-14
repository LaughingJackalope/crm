package com.crm.ciam.application

import com.crm.common.messaging.EventEnvelope

/** Port for publishing domain events — implemented in infrastructure. */
fun interface DomainEventPublisher {
    fun publish(envelope: EventEnvelope<*>)
}
