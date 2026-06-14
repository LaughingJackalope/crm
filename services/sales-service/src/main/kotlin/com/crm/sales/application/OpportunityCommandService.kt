package com.crm.sales.application

import com.crm.sales.domain.event.*
import com.crm.sales.domain.opportunity.*
import com.crm.common.messaging.EventEnvelope
import com.crm.common.messaging.Identifiable
import java.util.UUID

class OpportunityCommandService(
    private val opportunityRepository: OpportunityRepository,
    private val eventPublisher: SalesEventPublisher,
) {

    fun createOpportunity(
        customerId: String,
        name: String,
        amount: Money,
        accountId: String? = null,
        expectedCloseDate: java.time.LocalDate? = null,
        ownerId: String? = null,
    ): Opportunity {
        val opportunity = Opportunity(
            customerId = customerId,
            accountId = accountId,
            name = name,
            amount = amount,
            expectedCloseDate = expectedCloseDate,
            ownerId = ownerId,
        )
        val saved = opportunityRepository.save(opportunity)
        eventPublisher.publish(OpportunityCreated(
            entityId = saved.opportunityId.toString(),
            customerId = saved.customerId,
            name = saved.name,
            amount = "${saved.amount.value} ${saved.amount.currency}",
            createdAt = saved.createdAt,
        ))
        return saved
    }

    fun advanceStage(opportunityId: UUID): Opportunity {
        val opp = opportunityRepository.findById(opportunityId)
            ?: throw NotFoundException("Opportunity", opportunityId.toString())
        val fromStage = opp.stage
        val saved = opportunityRepository.save(opp.advanceStage())
        eventPublisher.publish(OpportunityStageAdvanced(
            entityId = saved.opportunityId.toString(),
            fromStage = fromStage.name,
            toStage = saved.stage.name,
            advancedAt = saved.updatedAt,
        ))
        return saved
    }

    fun closeOpportunity(opportunityId: UUID, isWon: Boolean, reason: WinLossReason? = null): Opportunity {
        val opp = opportunityRepository.findById(opportunityId)
            ?: throw NotFoundException("Opportunity", opportunityId.toString())
        val saved = opportunityRepository.save(opp.close(isWon, reason))
        eventPublisher.publish(OpportunityClosed(
            entityId = saved.opportunityId.toString(),
            isWon = isWon,
            reason = reason?.category,
            closedAt = saved.updatedAt,
        ))
        return saved
    }

    fun reassignOwner(opportunityId: UUID, newOwnerId: String): Opportunity {
        val opp = opportunityRepository.findById(opportunityId)
            ?: throw NotFoundException("Opportunity", opportunityId.toString())
        val previousOwner = opp.ownerId
        val saved = opportunityRepository.save(opp.reassignOwner(newOwnerId))
        eventPublisher.publish(OwnerReassigned(
            entityId = saved.opportunityId.toString(),
            previousOwnerId = previousOwner,
            newOwnerId = newOwnerId,
            reassignedAt = saved.updatedAt,
        ))
        return saved
    }
}

interface OpportunityRepository {
    fun findById(id: UUID): Opportunity?
    fun findByCustomerId(customerId: String): List<Opportunity>
    fun findByOwnerId(ownerId: String): List<Opportunity>
    fun findByStage(stage: SalesStage): List<Opportunity>
    fun save(opportunity: Opportunity): Opportunity
}

fun interface SalesEventPublisher {
    fun publish(event: Identifiable)
}

class NotFoundException(resource: String, id: String) : RuntimeException("$resource with id '$id' not found")
