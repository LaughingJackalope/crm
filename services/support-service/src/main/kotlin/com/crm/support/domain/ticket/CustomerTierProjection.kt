package com.crm.support.domain.ticket

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Local read-model projection of customer tiers from the CIAM bounded context.
 *
 * Populated by consuming `crm.ciam.lifecycle-stage.changed` events.
 * The Support service uses this to calculate SLA deadlines without
 * making cross-service API calls.
 *
 * Schema: `crm_support.customer_tier_projection`
 */
@Entity
@Table(name = "customer_tier_projection", schema = "support")
class CustomerTierProjection : PanacheEntityBase {

    /**
     * The contactId from CIAM — serves as the primary key since
     * it's the identity used in lifecycle events.
     */
    @Id
    @Column(name = "contact_id", nullable = false, length = 36)
    lateinit var contactId: String

    @Column(name = "customer_id", nullable = false, length = 36)
    lateinit var customerId: String

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 16)
    lateinit var tier: CustomerTier

    @Column(name = "lifecycle_stage", nullable = false, length = 16)
    lateinit var lifecycleStage: String

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant

    companion object : PanacheCompanion<CustomerTierProjection> {
        fun findByContactId(contactId: String): CustomerTierProjection? =
            find("contactId", contactId).firstResult()

        fun findByCustomerId(customerId: String): List<CustomerTierProjection> =
            list("customerId", customerId)

        fun findByTier(tier: CustomerTier): List<CustomerTierProjection> =
            list("tier", tier)
    }
}
