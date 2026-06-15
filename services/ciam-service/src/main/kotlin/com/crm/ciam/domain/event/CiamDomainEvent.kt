package com.crm.ciam.domain.event

import com.crm.common.messaging.Identifiable
import java.time.Instant

/**
 * Sealed hierarchy of all domain events emitted by the CIAM bounded context.
 *
 * Sealed interface gives us:
 * - Exhaustive `when` handling in application services and event handlers
 * - Type-safe pattern matching without `else` branches
 * - Compiler-enforced handling of new event types
 *
 * Each event carries the entityId required by [Identifiable] for Kafka
 * partition key routing.
 */
sealed interface CiamDomainEvent : Identifiable {

    /**
     * Emitted when a new contact is registered.
     */
    data class CustomerRegistered(
        override val entityId: String,
        val displayName: String,
        val source: String?,
        val registeredAt: Instant,
    ) : CiamDomainEvent

    /**
     * Emitted when a lead meets the qualification threshold.
     */
    data class LeadQualified(
        override val entityId: String,
        val previousStage: String,
        val qualifiedAt: Instant,
    ) : CiamDomainEvent

    /**
     * Emitted when a lead is disqualified (score drops or consent revoked).
     */
    data class LeadDisqualified(
        override val entityId: String,
        val reason: DisqualificationReason,
        val disqualifiedAt: Instant,
    ) : CiamDomainEvent

    /**
     * Emitted when two customer records are deduplicated.
     */
    data class CustomersMerged(
        val survivingCustomerId: String,
        val mergedCustomerId: String,
        val mergedAt: Instant,
    ) : CiamDomainEvent {
        override val entityId: String get() = survivingCustomerId
    }

    /**
     * Emitted when consent is granted or revoked for a purpose.
     */
    data class ConsentChanged(
        override val entityId: String,
        val purpose: String,
        val granted: Boolean,
        val changedAt: Instant,
    ) : CiamDomainEvent

    /**
     * Emitted when a contact's email passes verification.
     */
    data class EmailVerified(
        override val entityId: String,
        val email: String,
        val verifiedAt: Instant,
    ) : CiamDomainEvent

    /**
     * Emitted when a contact is soft-deactivated.
     */
    data class CustomerDeactivated(
        override val entityId: String,
        val reason: String?,
        val deactivatedAt: Instant,
    ) : CiamDomainEvent

    /**
     * Emitted when a deactivated customer is reactivated.
     */
    data class CustomerReactivated(
        override val entityId: String,
        val reactivatedAt: Instant,
    ) : CiamDomainEvent

    /**
     * Emitted when a customer transitions lifecycle stage.
     * This is the primary event for cross-context lifecycle synchronization.
     */
    data class LifecycleStageChanged(
        override val entityId: String,
        val fromStage: String,
        val toStage: String,
        val changedAt: Instant,
    ) : CiamDomainEvent
}

/**
     * Reasons a lead can be disqualified.
     */
enum class DisqualificationReason {
    SCORE_BELOW_THRESHOLD,
    CONSENT_REVOKED,
    MANUAL_DISQUALIFICATION,
}
