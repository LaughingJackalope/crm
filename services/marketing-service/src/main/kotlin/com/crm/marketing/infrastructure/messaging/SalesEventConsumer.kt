package com.crm.marketing.infrastructure.messaging

import com.crm.marketing.domain.CampaignDomainException
import com.crm.marketing.domain.campaign.AdNetworkSource
import com.crm.marketing.domain.campaign.Campaign
import com.crm.marketing.domain.campaign.CampaignMetrics
import com.crm.marketing.domain.campaign.CampaignStatus
import com.crm.marketing.domain.event.CampaignDomainEvent
import com.crm.marketing.domain.event.CampaignDomainEvent.RevenueAttributedToCampaign
import com.crm.marketing.domain.event.CampaignDomainEvent.ConversionDeclared
import com.crm.marketing.infrastructure.persistence.CampaignRepository
import com.crm.marketing.infrastructure.persistence.toJson
import com.crm.marketing.infrastructure.persistence.IncomingEventLogRepository
import com.crm.marketing.infrastructure.persistence.OutboxEventEntity
import com.crm.marketing.infrastructure.persistence.OutboxEventRepository
import com.crm.common.messaging.EventEnvelope
import io.smallrye.common.annotation.Blocking
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Consumes Sales events for closed-loop attribution and campaign lifecycle.
 *
 * ## Flow
 * 1. Idempotency check via [IncomingEventLogRepository].
 * 2. Route `OpportunityClosed` (won) → attribute revenue to campaign via utm_campaign_id.
 * 3. Record idempotency after successful processing (same TX).
 */
@ApplicationScoped
class SalesEventConsumer @Inject constructor(
    private val campaignRepository: CampaignRepository,
    private val eventLogRepository: IncomingEventLogRepository,
    private val outboxRepository: OutboxEventRepository,
) {
    private val log = Logger.getLogger(SalesEventConsumer::class.java)

    @Incoming("sales-opportunity-events")
    @Blocking
    @Transactional
    fun consume(envelope: SalesEventEnvelope) {
        val opportunityId = envelope.payload.opportunityId
            ?: throw CampaignDomainException.ConsumerProcessingException(
                "${envelope.eventType} event missing opportunityId"
            )

        val idempotencyKey = buildIdempotencyKey(opportunityId)

        if (eventLogRepository.isProcessed(idempotencyKey)) {
            log.infof("Skipping duplicate event: type=%s, opportunity=%s", envelope.eventType, opportunityId)
            return
        }

        log.infof("Processing sales event: type=%s, opportunity=%s, outcome=%s",
            envelope.eventType, opportunityId, envelope.payload.outcome)

        when (envelope.eventType) {
            "OpportunityClosed" -> handleOpportunityClosed(envelope, opportunityId)
            else -> log.debugf("Ignoring unhandled sales event type: %s", envelope.eventType)
        }

        eventLogRepository.markProcessed(
            eventId = idempotencyKey,
            eventType = envelope.eventType,
            source = envelope.source,
            entityId = opportunityId,
        )
    }

    private fun handleOpportunityClosed(envelope: SalesEventEnvelope, opportunityId: String) {
        val isWon = envelope.payload.isWon ?: (envelope.payload.outcome == "won")
        if (!isWon) {
            log.infof("Opportunity %s was lost — no attribution", opportunityId)
            return
        }

        val utmCampaignId = envelope.payload.utmCampaignId ?: envelope.payload.attributionTag
        if (utmCampaignId.isNullOrBlank()) {
            log.infof("Opportunity %s has no attribution tag — skipping", opportunityId)
            return
        }

        val campaignId = try { UUID.fromString(utmCampaignId) } catch (ex: IllegalArgumentException) {
            log.warnf("Invalid campaign ID in attribution tag: %s", utmCampaignId)
            return
        }

        val campaign = campaignRepository.findById(campaignId)
        if (campaign == null) {
            log.warnf("Campaign %s not found for attribution — skipping", campaignId)
            return
        }

        val amount = parseAmount(envelope.payload.totalAmount ?: envelope.payload.amount)
        if (amount <= BigDecimal.ZERO) {
            log.warnf("Invalid attribution amount for opportunity %s — skipping", opportunityId)
            return
        }

        val now = envelope.payload.closedAt?.toInstant() ?: Instant.now()
        val updated = campaign.attributeSale(opportunityId, amount, now)
        val saved = campaignRepository.save(updated)

        log.infof("Attributed %s from opportunity %s to campaign %s (total: %s)",
            amount, opportunityId, campaignId, saved.metrics.attributedRevenue)

        // Write outbox events
        val customerId = envelope.payload.customerId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
        saveOutbox(
            RevenueAttributedToCampaign(
                entityId = saved.campaignId.toString(),
                campaignId = saved.campaignId,
                opportunityId = opportunityId,
                amount = amount,
                totalAttributedRevenue = saved.metrics.attributedRevenue,
                attributedAt = now,
            ), "campaign", now
        )

        saveOutbox(
            ConversionDeclared(
                entityId = saved.campaignId.toString(),
                campaignId = saved.campaignId,
                customerId = customerId,
                revenue = amount,
                occurredAt = now,
            ), "campaign", now
        )
    }

    private fun parseAmount(raw: String?): BigDecimal {
        if (raw.isNullOrBlank()) return BigDecimal.ZERO
        // Handle formats like "1500.00 USD" or "1500.00"
        val numeric = raw.trim().split(" ").firstOrNull() ?: return BigDecimal.ZERO
        return try { BigDecimal(numeric) } catch (ex: NumberFormatException) { BigDecimal.ZERO }
    }

    private fun buildIdempotencyKey(opportunityId: String): UUID =
        UUID.nameUUIDFromBytes("attribution:$opportunityId".toByteArray())

    private fun saveOutbox(event: CampaignDomainEvent, entityType: String, now: Instant) {
        outboxRepository.save(
            OutboxEventEntity().apply {
                eventId = UUID.randomUUID()
                entityId = event.entityId
                this.entityType = entityType
                eventType = event::class.simpleName!!
                source = "marketing"
                this@apply.payload = EventEnvelope(
                    eventType = event::class.simpleName!!, source = "marketing",
                    correlationId = null, actorId = null, payload = event,
                ).toJson()
                this@apply.createdAt = now
            }
        )
    }
}
