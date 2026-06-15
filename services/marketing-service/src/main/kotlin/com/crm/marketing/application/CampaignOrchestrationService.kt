package com.crm.marketing.application

import com.crm.marketing.domain.CampaignDomainException
import com.crm.marketing.domain.campaign.AdNetworkAdapterRegistry
import com.crm.marketing.domain.campaign.Campaign
import com.crm.marketing.domain.campaign.AdNetworkSource
import com.crm.marketing.domain.campaign.CampaignStatus
import com.crm.marketing.domain.event.CampaignDomainEvent
import com.crm.marketing.domain.event.CampaignDomainEvent.*
import com.crm.marketing.infrastructure.persistence.CampaignRepository
import com.crm.marketing.infrastructure.persistence.OutboxEventEntity
import com.crm.marketing.infrastructure.persistence.OutboxEventRepository
import com.crm.marketing.infrastructure.persistence.toJson
import com.crm.common.messaging.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@ApplicationScoped
class CampaignOrchestrationService @Inject constructor(
    private val campaignRepository: CampaignRepository,
    private val outboxRepository: OutboxEventRepository,
) {

    @Transactional
    fun createCampaign(
        name: String,
        source: AdNetworkSource,
        targetSegment: String,
        budget: BigDecimal = BigDecimal.ZERO,
        now: Instant = Instant.now(),
    ): Campaign {
        val campaign = Campaign(
            name = name, source = source, targetSegment = targetSegment,
            budget = budget, createdAt = now, updatedAt = now,
        )
        val saved = campaignRepository.save(campaign)
        saveOutbox(CampaignCreated(
            entityId = saved.campaignId.toString(), campaignId = saved.campaignId,
            name = name, source = source.name, createdAt = now,
        ), "campaign", now)
        return saved
    }

    @Transactional
    fun launchCampaign(campaignId: UUID, now: Instant = Instant.now()): Campaign {
        val campaign = campaignRepository.findById(campaignId)
            ?: throw CampaignDomainException.CampaignNotFound(campaignId)
        val launched = campaign.launch()
        val saved = campaignRepository.save(launched)
        saveOutbox(CampaignLaunched(
            entityId = saved.campaignId.toString(), campaignId = saved.campaignId, launchedAt = now,
        ), "campaign", now)
        return saved
    }

    @Transactional
    fun pauseCampaign(campaignId: UUID, now: Instant = Instant.now()): Campaign {
        val campaign = campaignRepository.findById(campaignId)
            ?: throw CampaignDomainException.CampaignNotFound(campaignId)
        val paused = campaign.pause()
        return campaignRepository.save(paused)
    }

    @Transactional
    fun completeCampaign(campaignId: UUID, now: Instant = Instant.now()): Campaign {
        val campaign = campaignRepository.findById(campaignId)
            ?: throw CampaignDomainException.CampaignNotFound(campaignId)
        val completed = campaign.complete()
        val saved = campaignRepository.save(completed)
        saveOutbox(CampaignCompleted(
            entityId = saved.campaignId.toString(), campaignId = saved.campaignId, completedAt = now,
        ), "campaign", now)
        return saved
    }

    @Transactional
    fun syncCampaignMetrics(campaignId: UUID, now: Instant = Instant.now()): Campaign {
        val campaign = campaignRepository.findById(campaignId)
            ?: throw CampaignDomainException.CampaignNotFound(campaignId)
        val adapter = AdNetworkAdapterRegistry.get(campaign.source)
        val newMetrics = adapter.syncCampaignPerformance(campaign)
        val updated = campaign.updateMetrics(newMetrics)
        val saved = campaignRepository.save(updated)
        saveOutbox(CampaignMetricsSynced(
            entityId = saved.campaignId.toString(), campaignId = saved.campaignId,
            source = campaign.source.name, impressions = newMetrics.impressions,
            clicks = newMetrics.clicks, spend = newMetrics.spend, syncedAt = now,
        ), "campaign", now)
        return saved
    }

    fun getCampaign(campaignId: UUID): Campaign? = campaignRepository.findById(campaignId)

    fun getCampaignRoas(campaignId: UUID): BigDecimal {
        val campaign = campaignRepository.findById(campaignId)
            ?: throw CampaignDomainException.CampaignNotFound(campaignId)
        return campaign.calculateRoas()
    }

    fun getCampaignCpa(campaignId: UUID): BigDecimal {
        val campaign = campaignRepository.findById(campaignId)
            ?: throw CampaignDomainException.CampaignNotFound(campaignId)
        return campaign.calculateCpa()
    }

    private fun saveOutbox(event: CampaignDomainEvent, entityType: String, now: Instant) {
        outboxRepository.save(OutboxEventEntity().apply {
            eventId = UUID.randomUUID(); entityId = event.entityId
            this.entityType = entityType; eventType = event::class.simpleName!!
            source = "marketing"
            this@apply.payload = EventEnvelope(
                eventType = event::class.simpleName!!, source = "marketing",
                correlationId = null, actorId = null, payload = event,
            ).toJson()
            this@apply.createdAt = now
        })
    }
}
