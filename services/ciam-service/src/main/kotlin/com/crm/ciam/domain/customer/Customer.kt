package com.crm.ciam.domain.customer

import java.time.Instant
import java.util.UUID

/**
 * Customer Aggregate Root.
 *
 * The authoritative identity record in the CRM. Other Bounded Contexts
 * reference customers by customerId only — they never own customer data.
 */
data class Customer(
    val customerId: UUID = UUID.randomUUID(),
    val displayName: String,
    val lifecycleStage: LifecycleStage = LifecycleStage.LEAD,
    val source: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val contacts: MutableList<Contact> = mutableListOf(),
    val accounts: MutableList<Account> = mutableListOf(),
    val consents: MutableList<ConsentRecord> = mutableListOf(),
) {
    fun registerContact(
        firstName: String,
        lastName: String,
        email: EmailAddress,
        phone: PhoneNumber? = null,
        title: String? = null,
    ): Pair<Customer, Contact> {
        require(contacts.none { it.email == email }) { "Contact with email ${email.value} already exists" }
        val contact = Contact(
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone,
            title = title,
        )
        contacts.add(contact)
        val updated = copy(updatedAt = Instant.now())
        return updated to contact
    }

    fun changeLifecycleStage(newStage: LifecycleStage): Customer {
        require(isValidTransition(lifecycleStage, newStage)) {
            "Invalid transition from $lifecycleStage to $newStage"
        }
        return copy(lifecycleStage = newStage, updatedAt = Instant.now())
    }

    fun updateConsent(purpose: String, granted: Boolean): Customer {
        val existing = consents.indexOfFirst { it.purpose == purpose }
        val record = ConsentRecord(
            purpose = purpose,
            granted = granted,
            grantedAt = Instant.now(),
        )
        if (existing >= 0) consents[existing] = record
        else consents.add(record)
        return copy(updatedAt = Instant.now())
    }

    fun deactivate(): Customer = copy(isActive = false, updatedAt = Instant.now())

    private fun isValidTransition(from: LifecycleStage, to: LifecycleStage): Boolean =
        when (from) {
            LifecycleStage.LEAD -> to in setOf(LifecycleStage.QUALIFIED, LifecycleStage.CHURNED)
            LifecycleStage.QUALIFIED -> to in setOf(LifecycleStage.OPPORTUNITY, LifecycleStage.CHURNED)
            LifecycleStage.OPPORTUNITY -> to in setOf(LifecycleStage.CUSTOMER, LifecycleStage.CHURNED)
            LifecycleStage.CUSTOMER -> to in setOf(LifecycleStage.ADVOCATE, LifecycleStage.CHURNED)
            LifecycleStage.ADVOCATE -> to == LifecycleStage.CHURNED
            LifecycleStage.CHURNED -> to == LifecycleStage.LEAD // Re-activation
        }
}

data class Contact(
    val contactId: UUID = UUID.randomUUID(),
    val firstName: String,
    val lastName: String,
    val title: String? = null,
    val email: EmailAddress,
    val phone: PhoneNumber? = null,
    val createdAt: Instant = Instant.now(),
)

data class Account(
    val accountId: UUID = UUID.randomUUID(),
    val companyName: String,
    val industry: String? = null,
    val size: OrganizationSize? = null,
    val billingAddress: PostalAddress? = null,
    val createdAt: Instant = Instant.now(),
)

data class PostalAddress(
    val street: String,
    val city: String,
    val state: String? = null,
    val postalCode: String,
    val country: String,
)
