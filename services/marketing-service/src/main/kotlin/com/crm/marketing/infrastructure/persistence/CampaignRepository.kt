package com.crm.marketing.infrastructure.persistence

import com.crm.marketing.domain.campaign.Campaign
import com.crm.marketing.domain.campaign.AdNetworkSource
import com.crm.marketing.domain.campaign.CampaignStatus
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class CampaignRepository {

    fun findById(id: UUID): Campaign? =
        CampaignEntity.find("campaignId", id).firstResult()?.toDomain()

    fun findByStatus(status: CampaignStatus): List<Campaign> =
        CampaignEntity.findByStatus(status).map { it.toDomain() }

    fun findBySource(source: AdNetworkSource): List<Campaign> =
        CampaignEntity.findBySource(source).map { it.toDomain() }

    fun findActiveCampaigns(): List<Campaign> =
        CampaignEntity.findActiveCampaigns().map { it.toDomain() }

    fun findAll(): List<Campaign> =
        CampaignEntity.listAll().map { it.toDomain() }

    fun save(campaign: Campaign): Campaign {
        val entity = campaign.toEntity()
        entity.persist()
        return entity.toDomain()
    }

    fun delete(id: UUID) {
        CampaignEntity.delete("campaignId", id)
    }
}
