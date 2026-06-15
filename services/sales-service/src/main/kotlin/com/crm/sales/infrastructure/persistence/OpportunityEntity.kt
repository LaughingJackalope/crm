package com.crm.sales.infrastructure.persistence

import com.crm.sales.domain.opportunity.Opportunity
import com.crm.sales.domain.opportunity.SalesStage
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA entity for Opportunity — maps to the `crm_sales.opportunity` table.
 */
@Entity
@Table(name = "opportunity", schema = "sales")
class OpportunityEntity : PanacheEntityBase {

    @Id
    @Column(name = "opportunity_id", nullable = false)
    lateinit var opportunityId: UUID

    @Column(name = "customer_id", nullable = false, length = 36)
    lateinit var customerId: String

    @Column(name = "account_id", length = 36)
    var accountId: String? = null

    @Column(name = "name", nullable = false, length = 255)
    lateinit var name: String

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    lateinit var stage: SalesStage

    @Column(name = "amount_value", nullable = false, precision = 19, scale = 4)
    lateinit var amountValue: BigDecimal

    @Column(name = "amount_currency", nullable = false, length = 3)
    lateinit var amountCurrency: String

    @Column(name = "probability", nullable = false)
    var probability: Int = 0

    @Column(name = "expected_close_date")
    var expectedCloseDate: LocalDate? = null

    @Column(name = "owner_id", length = 36)
    var ownerId: String? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant

    companion object : PanacheCompanion<OpportunityEntity> {
        fun findByCustomerId(customerId: String): List<OpportunityEntity> =
            list("customerId", customerId)

        fun findByStage(stage: SalesStage): List<OpportunityEntity> =
            list("stage", stage)
    }
}

/**
 * Map from JPA entity to domain aggregate.
 */
fun OpportunityEntity.toDomain(): Opportunity = Opportunity(
    opportunityId = opportunityId,
    customerId = customerId,
    accountId = accountId,
    name = name,
    stage = stage,
    amount = com.crm.sales.domain.opportunity.Money(amountValue, amountCurrency),
    probability = probability,
    expectedCloseDate = expectedCloseDate,
    ownerId = ownerId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * Map from domain aggregate to JPA entity.
 */
fun Opportunity.toEntity(): OpportunityEntity = OpportunityEntity().apply {
    opportunityId = this@toEntity.opportunityId
    customerId = this@toEntity.customerId
    accountId = this@toEntity.accountId
    name = this@toEntity.name
    stage = this@toEntity.stage
    amountValue = this@toEntity.amount.value
    amountCurrency = this@toEntity.amount.currency
    probability = this@toEntity.probability
    expectedCloseDate = this@toEntity.expectedCloseDate
    ownerId = this@toEntity.ownerId
    createdAt = this@toEntity.createdAt
    updatedAt = this@toEntity.updatedAt
}
