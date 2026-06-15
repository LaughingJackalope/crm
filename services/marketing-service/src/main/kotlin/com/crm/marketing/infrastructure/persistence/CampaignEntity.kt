package com.crm.marketing.infrastructure.persistence

import com.crm.marketing.domain.campaign.AdNetworkSource
import com.crm.marketing.domain.campaign.Campaign
import com.crm.marketing.domain.campaign.CampaignMetrics
import com.crm.marketing.domain.campaign.CampaignStatus
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA entity for Campaign — maps to the `crm_marketing.campaign` table.
 */
@Entity
@Table(name = "campaign", schema = "marketing")
class CampaignEntity : PanacheEntityBase {

    @Id
    @Column(name = "campaign_id", nullable = false)
    lateinit var campaignId: UUID

    @Column(name = "name", nullable = false, length = 255)
    lateinit var name: String

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    lateinit var source: AdNetworkSource

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    lateinit var status: CampaignStatus

    @Column(name = "target_segment", nullable = false, length = 255)
    lateinit var targetSegment: String

    @Column(name = "start_date")
    var startDate: LocalDate? = null

    @Column(name = "end_date")
    var endDate: LocalDate? = null

    @Column(name = "budget", precision = 19, scale = 2)
    var budget: BigDecimal = BigDecimal.ZERO

    // ── Metrics fields ──
    @Column(name = "impressions", nullable = false)
    var impressions: Long = 0

    @Column(name = "clicks", nullable = false)
    var clicks: Long = 0

    @Column(name = "spend", nullable = false, precision = 19, scale = 2)
    var spend: BigDecimal = BigDecimal.ZERO

    @Column(name = "attributed_revenue", nullable = false, precision = 19, scale = 2)
    var attributedRevenue: BigDecimal = BigDecimal.ZERO

    @Column(name = "attributed_conversions", nullable = false)
    var attributedConversions: Long = 0

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_attributed_opportunities", schema = "marketing",
        joinColumns = [JoinColumn(name = "campaign_id")])
    @Column(name = "opportunity_id", length = 36)
    var attributedOpportunityIds: MutableSet<String> = mutableSetOf()

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant

    companion object : PanacheCompanion<CampaignEntity> {
        fun findByStatus(status: CampaignStatus): List<CampaignEntity> =
            list("status", status)

        fun findBySource(source: AdNetworkSource): List<CampaignEntity> =
            list("source", source)

        fun findActiveCampaigns(): List<CampaignEntity> =
            list("status IN (?1, ?2)", CampaignStatus.ACTIVE, CampaignStatus.PAUSED)
    }
}

// ── Mapping: Entity → Domain ──────────────────────────────────────────────────

fun CampaignEntity.toDomain(): Campaign = Campaign(
    campaignId = campaignId,
    name = name,
    source = source,
    status = status,
    targetSegment = targetSegment,
    startDate = startDate,
    endDate = endDate,
    budget = budget,
    metrics = CampaignMetrics(
        impressions = impressions,
        clicks = clicks,
        spend = spend,
        attributedRevenue = attributedRevenue,
        attributedConversions = attributedConversions,
    ),
    attributedOpportunityIds = attributedOpportunityIds.toSet(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ── Mapping: Domain → Entity ──────────────────────────────────────────────────

fun Campaign.toEntity(): CampaignEntity {
    val existing = CampaignEntity.find("campaignId", campaignId).firstResult()
    val entity = existing ?: CampaignEntity()

    entity.apply {
        campaignId = this@toEntity.campaignId
        name = this@toEntity.name
        source = this@toEntity.source
        status = this@toEntity.status
        targetSegment = this@toEntity.targetSegment
        startDate = this@toEntity.startDate
        endDate = this@toEntity.endDate
        budget = this@toEntity.budget
        impressions = this@toEntity.metrics.impressions
        clicks = this@toEntity.metrics.clicks
        spend = this@toEntity.metrics.spend
        attributedRevenue = this@toEntity.metrics.attributedRevenue
        attributedConversions = this@toEntity.metrics.attributedConversions
        attributedOpportunityIds = this@toEntity.attributedOpportunityIds.toMutableSet()
        if (existing == null) createdAt = this@toEntity.createdAt
        updatedAt = this@toEntity.updatedAt
    }

    return entity
}
