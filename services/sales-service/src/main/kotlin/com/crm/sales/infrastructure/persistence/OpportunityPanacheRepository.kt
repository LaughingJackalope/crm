package com.crm.sales.infrastructure.persistence

import com.crm.sales.application.OpportunityRepository
import com.crm.sales.domain.opportunity.Opportunity
import com.crm.sales.domain.opportunity.SalesStage
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Panache-based implementation of [OpportunityRepository].
 */
@ApplicationScoped
class OpportunityPanacheRepository : OpportunityRepository {

    override fun findById(id: UUID): Opportunity? =
        OpportunityEntity.find("opportunityId", id).firstResult()?.toDomain()
    override fun findByCustomerId(customerId: String): List<Opportunity> =
        OpportunityEntity.findByCustomerId(customerId).map { it.toDomain() }

    override fun findByOwnerId(ownerId: String): List<Opportunity> =
        OpportunityEntity.list("ownerId", ownerId).map { (it as OpportunityEntity).toDomain() }

    override fun findByStage(stage: SalesStage): List<Opportunity> =
        OpportunityEntity.findByStage(stage).map { it.toDomain() }

    override fun save(opportunity: Opportunity): Opportunity {
        val entity = OpportunityEntity.find("opportunityId", opportunity.opportunityId).firstResult()
            ?: OpportunityEntity().apply {
                opportunityId = opportunity.opportunityId
                createdAt = opportunity.createdAt
            }

        entity.apply {
            customerId = opportunity.customerId
            accountId = opportunity.accountId
            name = opportunity.name
            stage = opportunity.stage
            amountValue = opportunity.amount.value
            amountCurrency = opportunity.amount.currency
            probability = opportunity.probability
            expectedCloseDate = opportunity.expectedCloseDate
            ownerId = opportunity.ownerId
            updatedAt = opportunity.updatedAt
        }

        entity.persist()
        return entity.toDomain()
    }
}
