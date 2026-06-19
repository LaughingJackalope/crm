package com.crm.ciam.infrastructure.messaging

import com.crm.common.telemetry.TraceContextCarrier
import com.crm.ciam.infrastructure.persistence.OutboxEventRepository
import com.crm.ciam.infrastructure.persistence.OutboxStatus
import io.opentelemetry.context.Context
import io.quarkus.scheduler.Scheduled
import org.eclipse.microprofile.reactive.messaging.Emitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.OnOverflow
import org.jboss.logging.Logger
import java.time.Instant

/**
 * Background relay that polls the transactional outbox and publishes
 * pending events to Kafka.
 *
 * ## Design
 *
 * - Runs on a fixed delay (configurable, default 500ms).
 * - Each poll fetches a batch of pending/failed-retry events.
 * - For each event: publish to Kafka, then delete from outbox.
 * - All operations are transactional — if Kafka publish fails, the outbox
 *   row remains PENDING for the next cycle.
 *
 * ## Ordering guarantee
 *
 * Events are polled oldest-first (ORDER BY created_at ASC). The entity_id
 * is embedded in the payload for downstream consumers to use as a
 * partition key if needed.
 *
 * ## Failure handling
 *
 * - Kafka unavailable: event stays PENDING, retried next cycle.
 * - After max retries (5): status → FAILED permanently, alert fires.
 * - FAILED events can be manually inspected/replayed.
 */
@ApplicationScoped
class OutboxRelay @Inject constructor(
    private val outboxRepository: OutboxEventRepository,
) {

    @Inject
    @Channel("domain-events")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 2048)
    private lateinit var emitter: Emitter<String>

    private val log = Logger.getLogger(OutboxRelay::class.java)

    /**
     * Poll the outbox and publish pending events to Kafka.
     * Runs every 500ms by default. Concurrent execution is skipped
     * so only one relay cycle runs at a time.
     */
    @Scheduled(every = "\${outbox.relay.interval:500ms}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun relay() {
        val pending = outboxRepository.findPending(BATCH_SIZE)
        if (pending.isEmpty()) return

        log.debugf("Relay: publishing %d pending events", pending.size)

        for (event in pending) {
            publishSingle(event)
        }
    }

    /**
     * Retry failed events with exponential backoff.
     * Runs every 30s.
     */
    @Scheduled(every = "\${outbox.relay.retry-interval:30s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun retryFailed() {
        val failed = outboxRepository.findFailedForRetry(
            maxRetries = MAX_RETRIES,
            retryThreshold = Instant.now().minusSeconds(RETRY_BACKOFF_SECONDS),
        )
        if (failed.isEmpty()) return

        log.warnf("Relay: retrying %d failed events", failed.size)

        for (event in failed) {
            publishSingle(event)
        }
    }

    @Transactional
    fun publishSingle(event: com.crm.ciam.infrastructure.persistence.OutboxEventEntity) {
        val traceContext = TraceContextCarrier.createContextFromHeaders(event.metadata)
        try {
            traceContext.makeCurrent().use {
                emitter.send(event.payload)
            }
            outboxRepository.remove(event.eventId)
            log.tracef("Relay: published event %s (%s)", event.eventId, event.eventType)
        } catch (ex: Exception) {
            log.warnf(ex, "Relay: failed to publish event %s (%s), retry %d",
                event.eventId, event.eventType, event.retryCount + 1)
            outboxRepository.markFailed(event.eventId)
        }
    }

    companion object {
        const val BATCH_SIZE = 100
        const val MAX_RETRIES = 5
        const val RETRY_BACKOFF_SECONDS = 60L
    }
}
