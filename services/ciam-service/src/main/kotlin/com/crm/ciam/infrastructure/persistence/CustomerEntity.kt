package com.crm.ciam.infrastructure.persistence

import com.crm.ciam.domain.customer.*
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for Customer — maps to the `crm_ciam.customer` table.
 * This is the Infrastructure layer's representation; the domain model
 * (Customer aggregate) is mapped to/from this entity in the repository.
 */
@Entity
@Table(name = "customer", schema = "ciam")
class CustomerEntity : PanacheEntityBase {

    @Id
    @Column(name = "customer_id", nullable = false)
    lateinit var customerId: UUID

    @Column(name = "display_name", nullable = false, length = 255)
    lateinit var displayName: String

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_stage", nullable = false, length = 20)
    lateinit var lifecycleStage: LifecycleStage

    @Column(name = "source", length = 100)
    var source: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    var contacts: MutableList<ContactEntity> = mutableListOf()

    companion object : PanacheCompanion<CustomerEntity> {
        fun findByCustomerId(id: UUID): CustomerEntity? = find("customerId", id).firstResult()
        fun findByEmail(email: String): CustomerEntity? =
            find("select c from CustomerEntity c join c.contacts ct where ct.email.value = ?1", email).firstResult()
        fun findAllActive(): List<CustomerEntity> = find("isActive", true).list()
        fun findByStage(stage: LifecycleStage): List<CustomerEntity> = find("lifecycleStage", stage).list()
    }
}

@Entity
@Table(name = "contact", schema = "ciam")
class ContactEntity : PanacheEntityBase {

    @Id
    @Column(name = "contact_id", nullable = false)
    lateinit var contactId: UUID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    lateinit var customer: CustomerEntity

    @Column(name = "first_name", nullable = false, length = 100)
    lateinit var firstName: String

    @Column(name = "last_name", nullable = false, length = 100)
    lateinit var lastName: String

    @Column(name = "title", length = 100)
    var title: String? = null

    @Embedded
    lateinit var email: EmailAddressEmbeddable

    @Embedded
    var phone: PhoneNumberEmbeddable? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    companion object : PanacheCompanion<ContactEntity>
}

@Embeddable
class EmailAddressEmbeddable {
    @Column(name = "email_value", nullable = false, length = 255)
    lateinit var value: String

    @Column(name = "email_is_primary")
    var isPrimary: Boolean = false

    @Column(name = "email_is_verified")
    var isVerified: Boolean = false
}

@Embeddable
class PhoneNumberEmbeddable {
    @Column(name = "phone_value", length = 30)
    var value: String? = null

    @Column(name = "phone_country_code", length = 5)
    var countryCode: String = "+1"

    @Enumerated(EnumType.STRING)
    @Column(name = "phone_type", length = 10)
    var type: PhoneType = PhoneType.MOBILE
}
