package com.crm.ciam.application

import com.crm.ciam.domain.customer.*
import com.crm.ciam.domain.event.*
import com.crm.common.error.NotFoundException
import com.crm.common.iam.JwtContext
import com.crm.common.messaging.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Application service — orchestrates use cases for the CIAM context.
 * Handles commands, delegates to the domain, and publishes events.
 */
@ApplicationScoped
class CustomerCommandService(
    private val customerRepository: CustomerRepository,
    private val eventPublisher: DomainEventPublisher,
) {

    fun registerContact(
        displayName: String,
        firstName: String,
        lastName: String,
        email: String,
        phone: String? = null,
        title: String? = null,
        source: String? = null,
        actor: JwtContext? = null,
    ): Customer {
        val emailVo = EmailAddress(email)
        require(!customerRepository.existsByEmail(emailVo)) {
            "Customer with email $email already exists"
        }

        val customer = Customer(
            displayName = displayName,
            source = source,
        )
        val (updated, contact) = customer.registerContact(
            firstName = firstName,
            lastName = lastName,
            email = emailVo,
            phone = phone?.let { PhoneNumber(it) },
            title = title,
        )

        val saved = customerRepository.save(updated)

        eventPublisher.publish(
            envelopeOf(
                event = CustomerRegistered(
                    entityId = saved.customerId.toString(),
                    displayName = saved.displayName,
                    source = source,
                    registeredAt = saved.createdAt,
                ),
                actorId = actor?.subject,
            )
        )

        return saved
    }

    fun qualifyLead(customerId: UUID, actor: JwtContext? = null): Customer {
        val customer = customerRepository.findById(customerId)
            ?: throw NotFoundException("Customer", customerId.toString())

        val updated = customer.changeLifecycleStage(LifecycleStage.QUALIFIED)
        val saved = customerRepository.save(updated)

        eventPublisher.publish(
            envelopeOf(
                event = LeadQualified(
                    entityId = saved.customerId.toString(),
                    previousStage = LifecycleStage.LEAD.name,
                    qualifiedAt = saved.updatedAt,
                ),
                actorId = actor?.subject,
            )
        )

        return saved
    }

    fun updateConsent(
        customerId: UUID,
        purpose: String,
        granted: Boolean,
        actor: JwtContext? = null,
    ): Customer {
        val customer = customerRepository.findById(customerId)
            ?: throw NotFoundException("Customer", customerId.toString())

        val updated = customer.updateConsent(purpose, granted)
        val saved = customerRepository.save(updated)

        eventPublisher.publish(
            envelopeOf(
                event = ConsentChanged(
                    entityId = saved.customerId.toString(),
                    purpose = purpose,
                    granted = granted,
                    changedAt = saved.updatedAt,
                ),
                actorId = actor?.subject,
            )
        )

        return saved
    }

    fun deactivateCustomer(customerId: UUID, reason: String?, actor: JwtContext? = null): Customer {
        val customer = customerRepository.findById(customerId)
            ?: throw NotFoundException("Customer", customerId.toString())

        val saved = customerRepository.save(customer.deactivate())

        eventPublisher.publish(
            envelopeOf(
                event = CustomerDeactivated(
                    entityId = saved.customerId.toString(),
                    reason = reason,
                    deactivatedAt = saved.updatedAt,
                ),
                actorId = actor?.subject,
            )
        )

        return saved
    }
}
