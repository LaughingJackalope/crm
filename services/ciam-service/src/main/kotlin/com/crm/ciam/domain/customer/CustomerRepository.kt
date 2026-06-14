package com.crm.ciam.domain.customer

import java.util.UUID

/**
 * Repository interface — defined in the domain layer.
 * Implemented in infrastructure/persistence (Panache).
 */
interface CustomerRepository {
    fun findById(id: UUID): Customer?
    fun findByEmail(email: EmailAddress): Customer?
    fun findByLifecycleStage(stage: LifecycleStage): List<Customer>
    fun findAllActive(): List<Customer>
    fun save(customer: Customer): Customer
    fun delete(id: UUID): Boolean
    fun existsByEmail(email: EmailAddress): Boolean
}
