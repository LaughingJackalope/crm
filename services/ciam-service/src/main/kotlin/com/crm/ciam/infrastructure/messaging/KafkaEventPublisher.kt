package com.crm.ciam.infrastructure.messaging

import com.crm.ciam.application.DomainEventPublisher
import com.crm.common.messaging.EventEnvelope
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.OnOverflow

/**
 * Publishes domain events to Kafka via SmallRye Reactive Messaging.
 * Implements the DomainEventPublisher port from the application layer.
 */
@ApplicationScoped
class KafkaEventPublisher(
    @Channel("domain-events")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 2048)
    private val emitter: MutinyEmitter<EventEnvelope<*>>,
) : DomainEventPublisher {

    override fun publish(envelope: EventEnvelope<*>) {
        emitter.sendAndAwait(envelope)
    }
}
