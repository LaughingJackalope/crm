package com.crm.billing.infrastructure.messaging

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.OnOverflow

/**
 * Provider bean that lazily supplies the domain-events emitter.
 *
 * Workaround for Quarkus issue #17841 / #19034: the scheduler may fire
 * before SmallRye Reactive Messaging has connected the Kafka producer.
 * By injecting the emitter into a separate provider bean, the CDI
 * resolution is deferred until first use rather than failing at
 * OutboxRelay construction time.
 */
@ApplicationScoped
class EmitterProvider @Inject constructor(
    @Channel("domain-events")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 2048)
    private val domainEventsEmitter: Emitter<String>,
) {
    fun domainEvents(): Emitter<String> = domainEventsEmitter
}
