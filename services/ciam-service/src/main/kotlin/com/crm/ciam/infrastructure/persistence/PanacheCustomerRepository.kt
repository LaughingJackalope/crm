package com.crm.ciam.infrastructure.persistence

import com.crm.ciam.domain.customer.*
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Panache-based implementation of CustomerRepository.
 * Maps between JPA entities and domain aggregates.
 */
@ApplicationScoped
class PanacheCustomerRepository : CustomerRepository {

    override fun findById(id: UUID): Customer? =
        CustomerEntity.findByCustomerId(id)?.toDomain()

    override fun findByEmail(email: EmailAddress): Customer? =
        CustomerEntity.findByEmail(email.value)?.toDomain()

    override fun findByLifecycleStage(stage: LifecycleStage): List<Customer> =
        CustomerEntity.findByStage(stage).map { it.toDomain() }

    override fun findAllActive(): List<Customer> =
        CustomerEntity.findAllActive().map { it.toDomain() }

    override fun save(customer: Customer): Customer {
        val entity = CustomerEntity.findByCustomerId(customer.customerId)
            ?: CustomerEntity().apply {
                customerId = customer.customerId
                createdAt = customer.createdAt
            }

        entity.apply {
            displayName = customer.displayName
            lifecycleStage = customer.lifecycleStage
            source = customer.source
            isActive = customer.isActive
            updatedAt = customer.updatedAt
        }

        entity.persist()
        return entity.toDomain()
    }

    override fun delete(id: UUID): Boolean =
        CustomerEntity.delete("customerId", id) > 0

    override fun existsByEmail(email: EmailAddress): Boolean =
        CustomerEntity.findByEmail(email.value) != null

    // ── Mapping: Entity → Domain ─────────────────────────────────────────────

    private fun CustomerEntity.toDomain(): Customer = Customer(
        customerId = customerId,
        displayName = displayName,
        lifecycleStage = lifecycleStage,
        source = source,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        contacts = contactEntities().map { it.toDomain() }.toMutableList(),
    )

    private fun ContactEntity.toDomain(): Contact = Contact(
        contactId = contactId,
        firstName = firstName,
        lastName = lastName,
        title = title,
        email = EmailAddress(email.value),
        phone = phone?.let { PhoneNumber(it.value ?: "", it.countryCode, it.type) },
        createdAt = createdAt,
    )

    private fun CustomerEntity.contactEntities(): List<ContactEntity> =
        ContactEntity.list("customer", this)
}
