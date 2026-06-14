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
