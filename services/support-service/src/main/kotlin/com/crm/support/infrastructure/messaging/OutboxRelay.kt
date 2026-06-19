package com.crm.support.infrastructure.messaging

import com.crm.support.infrastructure.persistence.OutboxEventEntity
import com.crm.support.infrastructure.persistence.OutboxEventRepository
import com.crm.support.infrastructure.persistence.OutboxStatus
import com.crm.common.telemetry.TraceContextCarrier
import io.opentelemetry.context.Context
import io.quarkus.scheduler.Scheduled
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.OnOverflow
import org.jboss.logging.Logger
import java.time.Instant

@ApplicationScoped
class OutboxRelay {

    @Inject
    private lateinit var outboxRepository: OutboxEventRepository

    @Inject
    @Channel("domain-events")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 2048)
    private lateinit var emitter: MutinyEmitter<String>

    private val log = Logger.getLogger(OutboxRelay::class.java)

    @Scheduled(every = "\${outbox.relay.interval:500ms}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun relay() {
        val pending = outboxRepository.findPending(BATCH_SIZE)
        if (pending.isEmpty()) return
        log.debugf("Relay: publishing %d pending events", pending.size)
        for (event in pending) { publishSingle(event) }
    }

    @Scheduled(every = "\${outbox.relay.retry-interval:30s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun retryFailed() {
        val failed = outboxRepository.findFailedForRetry()
        if (failed.isEmpty()) return
        log.warnf("Relay: retrying %d failed events", failed.size)
        for (event in failed) { publishSingle(event) }
    }

    @Transactional
    fun publishSingle(event: OutboxEventEntity) {
        val traceContext = TraceContextCarrier.createContextFromHeaders(event.metadata)
        try {
            traceContext.makeCurrent().use {
                emitter.sendAndAwait(event.payload)
            }
            outboxRepository.remove(event.eventId)
            log.tracef("Relay: published event %s (%s)", event.eventId, event.eventType)
        } catch (ex: Exception) {
            log.warnf(ex, "Relay: failed to publish event %s (%s)", event.eventId, event.eventType)
            outboxRepository.markFailed(event.eventId)
        }
    }

    companion object {
        const val BATCH_SIZE = 100
        const val MAX_RETRIES = 5
        const val RETRY_BACKOFF_SECONDS = 60L
    }
}
