package com.crm.marketing.domain.campaign

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Campaign(
    val campaignId: UUID = UUID.randomUUID(),
    val name: String,
    val channel: MarketingChannel,
    val status: CampaignStatus = CampaignStatus.DRAFT,
    val targetSegment: String,       // Reference to a segment definition
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val budget: String? = null,      // e.g. "5000.00 USD"
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun launch(): Campaign {
        require(status == CampaignStatus.DRAFT) { "Only draft campaigns can be launched" }
        return copy(status = CampaignStatus.ACTIVE, startDate = LocalDate.now(), updatedAt = Instant.now())
    }

    fun complete(): Campaign {
        require(status == CampaignStatus.ACTIVE) { "Only active campaigns can be completed" }
        return copy(status = CampaignStatus.COMPLETED, endDate = LocalDate.now(), updatedAt = Instant.now())
    }

    fun cancel(): Campaign =
        copy(status = CampaignStatus.CANCELLED, updatedAt = Instant.now())
}

enum class MarketingChannel { EMAIL, SMS, PUSH, SOCIAL, DIRECT_MAIL }
enum class CampaignStatus { DRAFT, ACTIVE, COMPLETED, CANCELLED }
