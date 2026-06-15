package com.crm.support.infrastructure.messaging

import com.crm.support.domain.SupportDomainException
import com.crm.support.domain.ticket.CustomerTier
import com.crm.support.domain.ticket.CustomerTierProjection
import com.crm.support.infrastructure.persistence.IncomingEventLogRepository
import io.smallrye.common.annotation.Blocking
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import java.time.Instant
import java.util.UUID

/**
 * Consumes CIAM lifecycle events to maintain a local customer tier projection.
 *
 * ## Events consumed
 * - `LifecycleStageChanged` → maps lifecycle stage to customer tier
 * - `CustomerDeactivated` → marks customer as deactivated (tier=STANDARD)
 *
 * ## Tier mapping
 * | Lifecycle Stage | Customer Tier |
 * |-----------------|---------------|
 * | lead, qualified | STANDARD |
 * | opportunity     | PREMIUM |
 * | customer, advocate | ENTERPRISE |
 * | churned         | STANDARD |
 */
@ApplicationScoped
class LifecycleEventConsumer @Inject constructor(
    private val eventLogRepository: IncomingEventLogRepository,
) {

    private val log = Logger.getLogger(LifecycleEventConsumer::class.java)

    @Incoming("ciam-lifecycle-events")
    @Blocking
    @Transactional
    fun consume(envelope: LifecycleEventEnvelope) {
        val contactId = envelope.payload.contactId
            ?: throw SupportDomainException.ConsumerProcessingException(
                "${envelope.eventType} event missing contactId"
            )

        val idempotencyKey = buildIdempotencyKey(envelope)

        if (eventLogRepository.isProcessed(idempotencyKey)) {
            log.infof("Skipping duplicate event: type=%s, contact=%s", envelope.eventType, contactId)
            return
        }

        log.infof("Processing lifecycle event: type=%s, contact=%s", envelope.eventType, contactId)

        when (envelope.eventType) {
            "LifecycleStageChanged" -> handleLifecycleStageChanged(envelope, contactId)
            "CustomerDeactivated" -> handleCustomerDeactivated(envelope, contactId)
            "CustomerRegistered" -> handleCustomerRegistered(envelope, contactId)
            else -> log.debugf("Ignoring unhandled lifecycle event type: %s", envelope.eventType)
        }

        eventLogRepository.markProcessed(
            eventId = idempotencyKey,
            eventType = envelope.eventType,
            source = envelope.source,
            entityId = contactId,
        )
    }

    private fun handleLifecycleStageChanged(envelope: LifecycleEventEnvelope, contactId: String) {
        val stage = envelope.payload.toStage ?: envelope.payload.newStage
            ?: throw SupportDomainException.ConsumerProcessingException(
                "LifecycleStageChanged event missing toStage/newStage"
            )

        val customerId = envelope.payload.customerId ?: "unknown"
        val tier = mapStageToTier(stage)
        val changedAt = envelope.payload.changedAt?.toInstant() ?: Instant.now()

        upsertProjection(contactId, customerId, tier, stage, changedAt)
        log.infof("Updated tier projection: contact=%s, stage=%s, tier=%s", contactId, stage, tier)
    }

    private fun handleCustomerDeactivated(envelope: LifecycleEventEnvelope, contactId: String) {
        val customerId = envelope.payload.customerId ?: "unknown"
        val deactivatedAt = envelope.payload.deactivatedAt?.toInstant() ?: Instant.now()

        upsertProjection(contactId, customerId, CustomerTier.STANDARD, "churned", deactivatedAt)
        log.infof("Contact deactivated: contact=%s", contactId)
    }

    private fun handleCustomerRegistered(envelope: LifecycleEventEnvelope, contactId: String) {
        val customerId = envelope.payload.customerId ?: "unknown"
        val stage = envelope.payload.lifecycleStage ?: "lead"
        val tier = mapStageToTier(stage)

        upsertProjection(contactId, customerId, tier, stage, Instant.now())
        log.infof("New contact registered: contact=%s, tier=%s", contactId, tier)
    }

    private fun upsertProjection(
        contactId: String,
        customerId: String,
        tier: CustomerTier,
        stage: String,
        updatedAt: Instant,
    ) {
        val existing = CustomerTierProjection.findByContactId(contactId)
        val projection = existing ?: CustomerTierProjection()

        projection.apply {
            this.contactId = contactId
            this.customerId = customerId
            this.tier = tier
            this.lifecycleStage = stage
            this.updatedAt = updatedAt
        }

        projection.persist()
    }

    private fun mapStageToTier(stage: String): CustomerTier = when (stage) {
        "lead", "qualified", "churned" -> CustomerTier.STANDARD
        "opportunity" -> CustomerTier.PREMIUM
        "customer", "advocate" -> CustomerTier.ENTERPRISE
        else -> {
            log.warnf("Unknown lifecycle stage '%s', defaulting to STANDARD", stage)
            CustomerTier.STANDARD
        }
    }

    private fun buildIdempotencyKey(envelope: LifecycleEventEnvelope): UUID {
        val contactId = envelope.payload.contactId ?: "unknown"
        val raw = "${envelope.eventType}:$contactId:${envelope.payload.changedAt ?: envelope.payload.deactivatedAt ?: ""}"
        return UUID.nameUUIDFromBytes(raw.toByteArray())
    }
}
