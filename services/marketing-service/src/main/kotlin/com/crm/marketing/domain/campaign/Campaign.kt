package com.crm.marketing.domain.campaign

import com.crm.marketing.domain.CampaignDomainException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Campaign Aggregate Root.
 *
 * Manages marketing campaign lifecycle, performance metrics, and
 * closed-loop revenue attribution from Sales context events.
 *
 * ## Invariants
 * - Budget and monetary values use BigDecimal with HALF_UP rounding.
 * - Attribution is idempotent — duplicate opportunity IDs are ignored.
 * - Only ACTIVE or PAUSED campaigns can receive attributions.
 * - Metrics are always non-negative.
 */
data class Campaign(
    val campaignId: UUID = UUID.randomUUID(),
    val name: String,
    val source: AdNetworkSource,
    val status: CampaignStatus = CampaignStatus.DRAFT,
    val targetSegment: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val budget: BigDecimal = BigDecimal.ZERO,
    val metrics: CampaignMetrics = CampaignMetrics.EMPTY,
    val attributedOpportunityIds: Set<String> = emptySet(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    init {
        require(name.isNotBlank()) { "Campaign name must not be blank" }
        require(budget >= BigDecimal.ZERO) { "Budget must be non-negative" }
    }

    fun launch(): Campaign {
        require(status == CampaignStatus.DRAFT) { "Only DRAFT campaigns can be launched" }
        return copy(status = CampaignStatus.ACTIVE, startDate = LocalDate.now(), updatedAt = Instant.now())
    }

    fun pause(): Campaign {
        require(status == CampaignStatus.ACTIVE) { "Only ACTIVE campaigns can be paused" }
        return copy(status = CampaignStatus.PAUSED, updatedAt = Instant.now())
    }

    fun resume(): Campaign {
        require(status == CampaignStatus.PAUSED) { "Only PAUSED campaigns can be resumed" }
        return copy(status = CampaignStatus.ACTIVE, updatedAt = Instant.now())
    }

    fun complete(): Campaign {
        require(status == CampaignStatus.ACTIVE || status == CampaignStatus.PAUSED) {
            "Only ACTIVE or PAUSED campaigns can be completed"
        }
        return copy(status = CampaignStatus.COMPLETED, endDate = LocalDate.now(), updatedAt = Instant.now())
    }

    fun cancel(): Campaign {
        require(status != CampaignStatus.COMPLETED) { "Cannot cancel a completed campaign" }
        return copy(status = CampaignStatus.CANCELLED, updatedAt = Instant.now())
    }

    /**
     * Update performance metrics from an external ad network sync.
     * Pure domain method — no external API coupling.
     */
    fun updateMetrics(newMetrics: CampaignMetrics): Campaign {
        return copy(metrics = newMetrics, updatedAt = Instant.now())
    }

    /**
     * Attribute a won opportunity's revenue to this campaign.
     * Idempotent — if the opportunityId was already attributed, returns unchanged.
     *
     * @param opportunityId The won opportunity ID from the Sales context.
     * @param amount The revenue amount to attribute.
     * @param now The current timestamp.
     * @return Updated campaign with incremented attributedRevenue.
     */
    fun attributeSale(opportunityId: String, amount: BigDecimal, now: Instant): Campaign {
        require(status == CampaignStatus.ACTIVE || status == CampaignStatus.PAUSED) {
            "Cannot attribute sales to a $status campaign"
        }
        require(amount > BigDecimal.ZERO) { "Attribution amount must be positive" }

        if (attributedOpportunityIds.contains(opportunityId)) {
            return this // Idempotent: already attributed
        }

        val newRevenue = metrics.attributedRevenue.add(amount)
            .setScale(2, RoundingMode.HALF_UP)

        return copy(
            metrics = metrics.copy(attributedRevenue = newRevenue),
            attributedOpportunityIds = attributedOpportunityIds + opportunityId,
            updatedAt = now,
        )
    }

    /**
     * Calculate return on ad spend (ROAS).
     * ROAS = attributedRevenue / spend.
     */
    fun calculateRoas(): BigDecimal {
        if (metrics.spend.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        return metrics.attributedRevenue.divide(metrics.spend, 4, RoundingMode.HALF_UP)
    }

    /**
     * Calculate cost per acquisition (CPA).
     * CPA = spend / attributedConversions.
     */
    fun calculateCpa(): BigDecimal {
        if (metrics.attributedConversions == 0L) return BigDecimal.ZERO
        return metrics.spend.divide(
            BigDecimal(metrics.attributedConversions), 2, RoundingMode.HALF_UP
        )
    }
}

enum class CampaignStatus { DRAFT, ACTIVE, PAUSED, COMPLETED, CANCELLED }

enum class AdNetworkSource { GOOGLE, META, DIRECT, EMAIL, SMS, DISPLAY, AFFILIATE }

/**
 * Value object encapsulating campaign performance metrics.
 * All monetary values use BigDecimal. All counts are non-negative.
 */
data class CampaignMetrics(
    val impressions: Long = 0,
    val clicks: Long = 0,
    val spend: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
    val attributedRevenue: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
    val attributedConversions: Long = 0,
) {
    companion object {
        val EMPTY = CampaignMetrics()
    }

    val clickThroughRate: BigDecimal
        get() = if (impressions == 0L) BigDecimal.ZERO
        else BigDecimal(clicks).divide(BigDecimal(impressions), 4, RoundingMode.HALF_UP)

    val costPerClick: BigDecimal
        get() = if (clicks == 0L) BigDecimal.ZERO
        else spend.divide(BigDecimal(clicks), 2, RoundingMode.HALF_UP)
}
