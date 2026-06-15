package com.crm.ciam.domain.customer

import java.time.Instant

@JvmInline
value class EmailAddress(val value: String) {
    init {
        require(value.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
            "Invalid email format: $value"
        }
    }
}

data class PhoneNumber(
    val value: String,
    val countryCode: String = "+1",
    val type: PhoneType = PhoneType.MOBILE,
)

enum class PhoneType { MOBILE, LANDLINE, OTHER }

enum class LifecycleStage {
    LEAD, QUALIFIED, OPPORTUNITY, CUSTOMER, ADVOCATE, CHURNED
}

enum class OrganizationSize {
    SOLO, SMALL, MEDIUM, ENTERPRISE, GLOBAL
}

data class ConsentRecord(
    val purpose: String,
    val granted: Boolean,
    val grantedAt: Instant,
    val expiresAt: Instant? = null,
    val source: String? = null,
)

data class CustomerScore(
    val value: Int,
    val factors: List<String>,
    val computedAt: Instant = Instant.now(),
) {
    init { require(value in 0..100) { "Score must be 0-100, got $value" } }
}

data class Contact(
    val contactId: java.util.UUID = java.util.UUID.randomUUID(),
    val firstName: String,
    val lastName: String,
    val title: String? = null,
    val email: EmailAddress,
    val phone: PhoneNumber? = null,
    val createdAt: Instant = Instant.now(),
)

data class Account(
    val accountId: java.util.UUID = java.util.UUID.randomUUID(),
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
