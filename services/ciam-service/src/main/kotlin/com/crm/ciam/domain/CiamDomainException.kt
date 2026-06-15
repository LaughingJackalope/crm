package com.crm.ciam.domain

/**
 * Sealed exception hierarchy for CIAM domain errors.
 *
 * Each subtype represents a specific invariant violation. Application services
 * catch these and map them to the appropriate HTTP status via [CrmException].
 *
 * Using a sealed class gives us exhaustive `when` handling at the call site
 * and prevents unknown subtypes from being thrown.
 */
sealed class CiamDomainException(
    override val message: String,
) : RuntimeException(message) {

    /**
     * Thrown when a lifecycle stage transition violates the allowed graph.
     *
     * Example: LEAD → CHURNED is invalid; must pass through QUALIFIED first.
     */
    class InvalidLifecycleTransition(
        val from: String,
        val to: String,
    ) : CiamDomainException(
        "Invalid lifecycle transition: $from → $to. " +
            "Consult the lifecycle state graph for allowed transitions."
    )

    /**
     * Thrown when an operation requires an active customer but the customer
     * is deactivated (churned).
     */
    class CustomerInactive(
        val customerId: String,
    ) : CiamDomainException(
        "Customer $customerId is inactive and cannot be modified."
    )

    /**
     * Thrown when attempting to qualify a lead that is not in LEAD stage.
     */
    class LeadNotQualified(
        val customerId: String,
        val currentStage: String,
    ) : CiamDomainException(
        "Customer $customerId is in stage $currentStage, not LEAD. " +
            "Only leads can be qualified."
    )

    /**
     * Thrown when a contact with the same email already exists.
     */
    class DuplicateContact(
        val email: String,
    ) : CiamDomainException(
        "Contact with email '$email' already exists in this customer."
    )

    /**
     * Thrown when attempting to reactivate a customer that is not churned.
     */
    class InvalidReactivation(
        val customerId: String,
        val currentStage: String,
    ) : CiamDomainException(
        "Customer $customerId is in stage $currentStage. " +
            "Only CHURNED customers can be reactivated."
    )
}
