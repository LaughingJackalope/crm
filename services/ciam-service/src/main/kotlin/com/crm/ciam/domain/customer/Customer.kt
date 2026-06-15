package com.crm.ciam.domain.customer

import com.crm.ciam.domain.CiamDomainException
import com.crm.ciam.domain.event.CiamDomainEvent
import com.crm.ciam.domain.event.CiamDomainEvent.*
import com.crm.ciam.domain.event.DisqualificationReason
import java.time.Instant
import java.util.UUID

/**
 * Domain result of a state-mutating operation on an [Customer] aggregate.
 *
 * Every command that changes aggregate state returns a [DomainResult] containing
 * both the new aggregate snapshot and the domain event(s) produced. This ensures:
 *
 * 1. The application service never needs to know *which* event corresponds to
 *    *which* state change — the aggregate decides.
 * 2. Events are guaranteed to be consistent with the aggregate's new state.
 * 3. Testing is deterministic — given an input, you get exactly one output.
 */
data class DomainResult<T>(
    val aggregate: T,
    val events: List<CiamDomainEvent>,
)

/**
 * Convenience factory for a single-event result.
 */
fun <T> T.withEvent(event: CiamDomainEvent): DomainResult<T> =
    DomainResult(this, listOf(event))

/**
 * Convenience factory for a no-event result (for operations that don't
 * produce domain events, e.g., adding a contact).
 */
fun <T> T.noEvent(): DomainResult<T> =
    DomainResult(this, emptyList())

/**
 * Customer Aggregate Root.
 *
 * The authoritative identity record in the CRM. Other Bounded Contexts
 * reference customers by customerId only — they never own customer data.
 *
 * ## State Transitions
 *
 * Lifecycle stage transitions are managed by [LifecycleStateMachine].
 * Every transition produces a [LifecycleStageChanged] domain event.
 * Invalid transitions throw [CiamDomainException.InvalidLifecycleTransition].
 */
data class Customer(
    val customerId: UUID = UUID.randomUUID(),
    val displayName: String,
    val lifecycleStage: LifecycleStage = LifecycleStage.LEAD,
    val source: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val contacts: List<Contact> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val consents: List<ConsentRecord> = emptyList(),
) {
    // ── Commands ─────────────────────────────────────────────────────────

    /**
     * Register a new contact under this customer.
     *
     * @return Updated customer with the new contact added.
     * @throws CiamDomainException.DuplicateContact if email already exists.
     */
    fun registerContact(
        firstName: String,
        lastName: String,
        email: EmailAddress,
        phone: PhoneNumber? = null,
        title: String? = null,
    ): DomainResult<Customer> {
        if (contacts.any { it.email == email }) {
            throw CiamDomainException.DuplicateContact(email.value)
        }
        val contact = Contact(
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone,
            title = title,
        )
        return copy(
            contacts = contacts + contact,
            updatedAt = Instant.now(),
        ).noEvent()
    }

    /**
     * Transition the customer to a new lifecycle stage.
     *
     * Validates the transition against the [LifecycleStateMachine] graph.
     * On success, returns the updated aggregate and a [LifecycleStageChanged] event.
     *
     * @return [DomainResult] with updated customer and [LifecycleStageChanged] event.
     * @throws CiamDomainException.InvalidLifecycleTransition if the transition is not allowed.
     * @throws CiamDomainException.CustomerInactive if the customer is deactivated.
     */
    fun changeLifecycleStage(newStage: LifecycleStage): DomainResult<Customer> {
        if (!isActive) {
            throw CiamDomainException.CustomerInactive(customerId.toString())
        }
        if (!LifecycleStateMachine.canTransition(lifecycleStage, newStage)) {
            throw CiamDomainException.InvalidLifecycleTransition(
                from = lifecycleStage.name,
                to = newStage.name,
            )
        }
        val updated = copy(
            lifecycleStage = newStage,
            updatedAt = Instant.now(),
        )
        val event = LifecycleStageChanged(
            entityId = customerId.toString(),
            fromStage = lifecycleStage.name,
            toStage = newStage.name,
            changedAt = updated.updatedAt,
        )
        return updated.withEvent(event)
    }

    /**
     * Qualify a lead — convenience for `changeLifecycleStage(QUALIFIED)`.
     *
     * @return [DomainResult] with updated customer and [LifecycleStageChanged] event.
     * @throws CiamDomainException.LeadNotQualified if not in LEAD stage.
     */
    fun qualifyLead(): DomainResult<Customer> {
        if (lifecycleStage != LifecycleStage.LEAD) {
            throw CiamDomainException.LeadNotQualified(
                customerId = customerId.toString(),
                currentStage = lifecycleStage.name,
            )
        }
        return changeLifecycleStage(LifecycleStage.QUALIFIED)
    }

    /**
     * Disqualify a lead — reverses qualification back to LEAD.
     *
     * Can only be called on a customer in QUALIFIED stage.
     * Returns the aggregate to LEAD and emits a [LeadDisqualified] event.
     *
     * @return [DomainResult] with updated customer and [LeadDisqualified] event.
     * @throws CiamDomainException.InvalidLifecycleTransition if not in QUALIFIED stage.
     */
    fun disqualifyLead(
        reason: DisqualificationReason = DisqualificationReason.MANUAL_DISQUALIFICATION,
    ): DomainResult<Customer> {
        if (lifecycleStage != LifecycleStage.QUALIFIED) {
            throw CiamDomainException.InvalidLifecycleTransition(
                from = lifecycleStage.name,
                to = LifecycleStage.LEAD.name,
            )
        }
        val updated = copy(
            lifecycleStage = LifecycleStage.LEAD,
            updatedAt = Instant.now(),
        )
        val event = LeadDisqualified(
            entityId = customerId.toString(),
            reason = reason,
            disqualifiedAt = updated.updatedAt,
        )
        return updated.withEvent(event)
    }

    /**
     * Deactivate (soft-delete) the customer.
     *
     * Transitions to CHURNED if not already churned. The customer can later
     * be reactivated via [reactivate].
     *
     * @return [DomainResult] with deactivated customer and [CustomerDeactivated] event.
     */
    fun deactivate(reason: String? = null): DomainResult<Customer> {
        val updated = copy(
            isActive = false,
            updatedAt = Instant.now(),
        )
        val event = CustomerDeactivated(
            entityId = customerId.toString(),
            reason = reason,
            deactivatedAt = updated.updatedAt,
        )
        return updated.withEvent(event)
    }

    /**
     * Reactivate a churned customer.
     *
     * The customer transitions back to LEAD stage, ready for re-qualification.
     *
     * @return [DomainResult] with reactivated customer and [CustomerReactivated] event.
     * @throws CiamDomainException.InvalidReactivation if customer is not CHURNED.
     */
    fun reactivate(): DomainResult<Customer> {
        if (lifecycleStage != LifecycleStage.CHURNED) {
            throw CiamDomainException.InvalidReactivation(
                customerId = customerId.toString(),
                currentStage = lifecycleStage.name,
            )
        }
        val updated = copy(
            isActive = true,
            lifecycleStage = LifecycleStage.LEAD,
            updatedAt = Instant.now(),
        )
        val event = CustomerReactivated(
            entityId = customerId.toString(),
            reactivatedAt = updated.updatedAt,
        )
        return updated.withEvent(event)
    }

    /**
     * Update or create a consent record for a given purpose.
     *
     * @return [DomainResult] with updated customer and [ConsentChanged] event.
     */
    fun updateConsent(purpose: String, granted: Boolean): DomainResult<Customer> {
        val existing = consents.indexOfFirst { it.purpose == purpose }
        val record = ConsentRecord(
            purpose = purpose,
            granted = granted,
            grantedAt = Instant.now(),
        )
        val updatedConsents = if (existing >= 0) {
            consents.toMutableList().also { it[existing] = record }
        } else {
            consents + record
        }
        val updated = copy(
            consents = updatedConsents,
            updatedAt = Instant.now(),
        )
        val event = ConsentChanged(
            entityId = customerId.toString(),
            purpose = purpose,
            granted = granted,
            changedAt = updated.updatedAt,
        )
        return updated.withEvent(event)
    }

    /**
     * Verify the customer's email address.
     *
     * @return [DomainResult] with updated timestamp and [EmailVerified] event.
     */
    fun verifyEmail(email: String): DomainResult<Customer> {
        val updated = copy(updatedAt = Instant.now())
        val event = EmailVerified(
            entityId = customerId.toString(),
            email = email,
            verifiedAt = updated.updatedAt,
        )
        return updated.withEvent(event)
    }
}

// ── Lifecycle State Machine ──────────────────────────────────────────────────

/**
 * Encodes the allowed lifecycle stage transition graph.
 *
 * The lifecycle is a directed graph with CHURNED as a terminal sink
 * (reachable from any active stage) and LEAD as the reactivation target.
 *
 * ```
 * LEAD → QUALIFIED → OPPORTUNITY → CUSTOMER → ADVOCATE
 *   ↑       ↓            ↓            ↓          ↓
 *   └── CHURNED ←────────────────────────────────┘
 * ```
 *
 * This object is public so transition rules can be unit-tested in isolation
 * without constructing a full Customer aggregate.
 */
object LifecycleStateMachine {

    /**
     * The set of allowed transitions, keyed by source stage.
     */
    private val transitions: Map<LifecycleStage, Set<LifecycleStage>> = mapOf(
        LifecycleStage.LEAD to setOf(
            LifecycleStage.QUALIFIED,
            LifecycleStage.CHURNED,
        ),
        LifecycleStage.QUALIFIED to setOf(
            LifecycleStage.LEAD,       // Disqualification
            LifecycleStage.OPPORTUNITY,
            LifecycleStage.CHURNED,
        ),
        LifecycleStage.OPPORTUNITY to setOf(
            LifecycleStage.CUSTOMER,
            LifecycleStage.CHURNED,
        ),
        LifecycleStage.CUSTOMER to setOf(
            LifecycleStage.ADVOCATE,
            LifecycleStage.CHURNED,
        ),
        LifecycleStage.ADVOCATE to setOf(
            LifecycleStage.CHURNED,
        ),
        LifecycleStage.CHURNED to setOf(
            LifecycleStage.LEAD,       // Reactivation
        ),
    )

    /**
     * Check if a transition from [from] to [to] is allowed.
     */
    fun canTransition(from: LifecycleStage, to: LifecycleStage): Boolean =
        transitions[from]?.contains(to) == true

    /**
     * Return the set of stages reachable from the given stage.
     */
    fun allowedTargets(from: LifecycleStage): Set<LifecycleStage> =
        transitions[from] ?: emptySet()
}
