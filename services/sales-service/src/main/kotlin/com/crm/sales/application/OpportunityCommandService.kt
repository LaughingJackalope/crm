package com.crm.sales.application

import com.crm.sales.domain.event.SalesDomainEvent
import com.crm.sales.domain.opportunity.*
import com.crm.common.messaging.EventEnvelope
import com.crm.common.messaging.Identifiable
import java.util.UUID

/**
 * Application service — orchestrates use cases for the Sales context.
 *
 * Delegates to the [Opportunity] aggregate and publishes the domain events
 * it returns. The service never constructs events directly.
 */
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
        eventPublisher.publish(
            SalesDomainEvent.OpportunityCreated(
                entityId = saved.opportunityId.toString(),
                customerId = saved.customerId,
                name = saved.name,
                amount = "${saved.amount.value} ${saved.amount.currency}",
                createdAt = saved.createdAt,
            )
        )
        return saved
    }

    fun advanceStage(opportunityId: UUID): Opportunity {
        val opp = opportunityRepository.findById(opportunityId)
            ?: throw NotFoundException("Opportunity", opportunityId.toString())

        val result = opp.advanceStage()
        val saved = opportunityRepository.save(result.aggregate)

        result.events.forEach { event -> eventPublisher.publish(event) }

        return saved
    }

    fun closeOpportunity(
        opportunityId: UUID,
        isWon: Boolean,
        reason: WinLossReason? = null,
    ): Opportunity {
        val opp = opportunityRepository.findById(opportunityId)
            ?: throw NotFoundException("Opportunity", opportunityId.toString())

        val result = opp.close(isWon, reason)
        val saved = opportunityRepository.save(result.aggregate)

        result.events.forEach { event -> eventPublisher.publish(event) }

        return saved
    }

    fun reassignOwner(opportunityId: UUID, newOwnerId: String): Opportunity {
        val opp = opportunityRepository.findById(opportunityId)
            ?: throw NotFoundException("Opportunity", opportunityId.toString())

        val result = opp.reassignOwner(newOwnerId)
        val saved = opportunityRepository.save(result.aggregate)

        result.events.forEach { event -> eventPublisher.publish(event) }

        return saved
    }

    fun sendQuote(opportunityId: UUID, quoteId: UUID): Opportunity {
        val opp = opportunityRepository.findById(opportunityId)
            ?: throw NotFoundException("Opportunity", opportunityId.toString())

        val result = opp.sendQuote(quoteId)
        val saved = opportunityRepository.save(result.aggregate)

        result.events.forEach { event -> eventPublisher.publish(event) }

        return saved
    }

    fun acceptQuote(opportunityId: UUID, quoteId: UUID): Opportunity {
        val opp = opportunityRepository.findById(opportunityId)
            ?: throw NotFoundException("Opportunity", opportunityId.toString())

        val result = opp.acceptQuote(quoteId)
        val saved = opportunityRepository.save(result.aggregate)

        result.events.forEach { event -> eventPublisher.publish(event) }

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
    fun publish(event: SalesDomainEvent)
}

class NotFoundException(resource: String, id: String) : RuntimeException("$resource with id '$id' not found")
