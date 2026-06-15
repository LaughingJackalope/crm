package com.crm.marketing.domain.event

import com.crm.common.messaging.Identifiable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Sealed hierarchy of all domain events emitted by the Marketing bounded context.
 */
sealed interface CampaignDomainEvent : Identifiable {

    data class CampaignCreated(
        override val entityId: String,
        val campaignId: UUID,
        val name: String,
        val source: String,
        val createdAt: Instant,
    ) : CampaignDomainEvent

    data class CampaignLaunched(
        override val entityId: String,
        val campaignId: UUID,
        val launchedAt: Instant,
    ) : CampaignDomainEvent

    data class CampaignPaused(
        override val entityId: String,
        val campaignId: UUID,
        val pausedAt: Instant,
    ) : CampaignDomainEvent

    data class CampaignCompleted(
        override val entityId: String,
        val campaignId: UUID,
        val completedAt: Instant,
    ) : CampaignDomainEvent

    data class CampaignMetricsSynced(
        override val entityId: String,
        val campaignId: UUID,
        val source: String,
        val impressions: Long,
        val clicks: Long,
        val spend: BigDecimal,
        val syncedAt: Instant,
    ) : CampaignDomainEvent

    data class RevenueAttributedToCampaign(
        override val entityId: String,
        val campaignId: UUID,
        val opportunityId: String,
        val amount: BigDecimal,
        val totalAttributedRevenue: BigDecimal,
        val attributedAt: Instant,
    ) : CampaignDomainEvent

    data class ConversionDeclared(
        override val entityId: String,
        val campaignId: UUID,
        val customerId: UUID,
        val revenue: BigDecimal,
        val occurredAt: Instant,
    ) : CampaignDomainEvent
}
