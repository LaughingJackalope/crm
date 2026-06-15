package com.crm.ciam.domain.customer

import com.crm.ciam.domain.CiamDomainException
import com.crm.ciam.domain.event.CiamDomainEvent
import com.crm.ciam.domain.event.DisqualificationReason
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Comprehensive tests for the [Customer] aggregate.
 *
 * Pure Kotlin — no Quarkus, no DI, no database. Tests run in milliseconds.
 * Each test constructs a Customer, invokes a command, and verifies:
 * 1. The returned aggregate has the expected state.
 * 2. The returned domain events match expectations.
 * 3. Invalid operations throw the correct domain exceptions.
 */
@DisplayName("Customer Aggregate")
class CustomerAggregateTest {

    private lateinit var customer: Customer

    @BeforeEach
    fun setUp() {
        customer = Customer(
            customerId = UUID.randomUUID(),
            displayName = "Acme Corp",
            lifecycleStage = LifecycleStage.LEAD,
            source = "website",
        )
    }

    // ── Factory / Construction ─────────────────────────────────────────────

    @Nested
    @DisplayName("construction")
    inner class Construction {

        @Test
        fun `defaults to LEAD stage and active`() {
            val c = Customer(displayName = "Test")
            assertEquals(LifecycleStage.LEAD, c.lifecycleStage)
            assertTrue(c.isActive)
            assertTrue(c.contacts.isEmpty())
            assertTrue(c.consents.isEmpty())
        }

        @Test
        fun `can be constructed with explicit stage`() {
            val c = Customer(
                displayName = "Test",
                lifecycleStage = LifecycleStage.CUSTOMER,
            )
            assertEquals(LifecycleStage.CUSTOMER, c.lifecycleStage)
        }
    }

    // ── Lifecycle Stage Transitions (Happy Path) ──────────────────────────

    @Nested
    @DisplayName("changeLifecycleStage — happy paths")
    inner class ChangeLifecycleStageHappy {

        @Test
        fun `LEAD → QUALIFIED returns updated aggregate and LifecycleStageChanged event`() {
            val result = customer.changeLifecycleStage(LifecycleStage.QUALIFIED)

            assertEquals(LifecycleStage.QUALIFIED, result.aggregate.lifecycleStage)
            assertEquals(1, result.events.size)
            val event = result.events.first() as CiamDomainEvent.LifecycleStageChanged
            assertEquals(customer.customerId.toString(), event.entityId)
            assertEquals("LEAD", event.fromStage)
            assertEquals("QUALIFIED", event.toStage)
        }

        @Test
        fun `QUALIFIED → OPPORTUNITY returns updated aggregate and event`() {
            val qualified = customer.changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
            val result = qualified.changeLifecycleStage(LifecycleStage.OPPORTUNITY)

            assertEquals(LifecycleStage.OPPORTUNITY, result.aggregate.lifecycleStage)
            val event = result.events.first() as CiamDomainEvent.LifecycleStageChanged
            assertEquals("QUALIFIED", event.fromStage)
            assertEquals("OPPORTUNITY", event.toStage)
        }

        @Test
        fun `OPPORTUNITY → CUSTOMER returns updated aggregate and event`() {
            val opp = customer
                .changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
                .changeLifecycleStage(LifecycleStage.OPPORTUNITY).aggregate
            val result = opp.changeLifecycleStage(LifecycleStage.CUSTOMER)

            assertEquals(LifecycleStage.CUSTOMER, result.aggregate.lifecycleStage)
        }

        @Test
        fun `CUSTOMER → ADVOCATE returns updated aggregate and event`() {
            val cust = customer
                .changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
                .changeLifecycleStage(LifecycleStage.OPPORTUNITY).aggregate
                .changeLifecycleStage(LifecycleStage.CUSTOMER).aggregate
            val result = cust.changeLifecycleStage(LifecycleStage.ADVOCATE)

            assertEquals(LifecycleStage.ADVOCATE, result.aggregate.lifecycleStage)
        }

        @Test
        fun `any active stage → CHURNED returns updated aggregate and event`() {
            val stages = listOf(
                LifecycleStage.LEAD,
                LifecycleStage.QUALIFIED,
                LifecycleStage.OPPORTUNITY,
                LifecycleStage.CUSTOMER,
                LifecycleStage.ADVOCATE,
            )
            for (stage in stages) {
                val c = customer.copy(lifecycleStage = stage)
                val result = c.changeLifecycleStage(LifecycleStage.CHURNED)
                assertEquals(LifecycleStage.CHURNED, result.aggregate.lifecycleStage)
                val event = result.events.first() as CiamDomainEvent.LifecycleStageChanged
                assertEquals(stage.name, event.fromStage)
                assertEquals("CHURNED", event.toStage)
            }
        }

        @Test
        fun `CHURNED → LEAD (reactivation) returns updated aggregate and event`() {
            val churned = customer.changeLifecycleStage(LifecycleStage.CHURNED).aggregate
            val result = churned.changeLifecycleStage(LifecycleStage.LEAD)

            assertEquals(LifecycleStage.LEAD, result.aggregate.lifecycleStage)
            val event = result.events.first() as CiamDomainEvent.LifecycleStageChanged
            assertEquals("CHURNED", event.fromStage)
            assertEquals("LEAD", event.toStage)
        }

        @Test
        fun `updatedAt timestamp is refreshed on transition`() {
            val before = Instant.now()
            Thread.sleep(1) // ensure clock advances
            val result = customer.changeLifecycleStage(LifecycleStage.QUALIFIED)
            assertTrue(result.aggregate.updatedAt.isAfter(customer.updatedAt))
        }

        @Test
        fun `customerId is preserved across transitions`() {
            val id = customer.customerId
            val result = customer.changeLifecycleStage(LifecycleStage.QUALIFIED)
            assertEquals(id, result.aggregate.customerId)
        }
    }

    // ── Lifecycle Stage Transitions (Invalid — Exceptions) ────────────────

    @Nested
    @DisplayName("changeLifecycleStage — invalid transitions throw")
    inner class ChangeLifecycleStageInvalid {

        @Test
        fun `LEAD → OPPORTUNITY throws InvalidLifecycleTransition`() {
            val ex = assertThrows(CiamDomainException.InvalidLifecycleTransition::class.java) {
                customer.changeLifecycleStage(LifecycleStage.OPPORTUNITY)
            }
            assertEquals("LEAD", ex.from)
            assertEquals("OPPORTUNITY", ex.to)
        }

        @Test
        fun `LEAD → CUSTOMER throws InvalidLifecycleTransition`() {
            assertThrows(CiamDomainException.InvalidLifecycleTransition::class.java) {
                customer.changeLifecycleStage(LifecycleStage.CUSTOMER)
            }
        }

        @Test
        fun `LEAD → ADVOCATE throws InvalidLifecycleTransition`() {
            assertThrows(CiamDomainException.InvalidLifecycleTransition::class.java) {
                customer.changeLifecycleStage(LifecycleStage.ADVOCATE)
            }
        }

        @Test
        fun `QUALIFIED → CUSTOMER throws InvalidLifecycleTransition`() {
            val qualified = customer.changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
            assertThrows(CiamDomainException.InvalidLifecycleTransition::class.java) {
                qualified.changeLifecycleStage(LifecycleStage.CUSTOMER)
            }
        }

        @Test
        fun `OPPORTUNITY → LEAD throws InvalidLifecycleTransition`() {
            val opp = customer
                .changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
                .changeLifecycleStage(LifecycleStage.OPPORTUNITY).aggregate
            assertThrows(CiamDomainException.InvalidLifecycleTransition::class.java) {
                opp.changeLifecycleStage(LifecycleStage.LEAD)
            }
        }

        @Test
        fun `CHURNED → QUALIFIED throws InvalidLifecycleTransition`() {
            val churned = customer.changeLifecycleStage(LifecycleStage.CHURNED).aggregate
            assertThrows(CiamDomainException.InvalidLifecycleTransition::class.java) {
                churned.changeLifecycleStage(LifecycleStage.QUALIFIED)
            }
        }

        @Test
        fun `self-transition throws InvalidLifecycleTransition`() {
            assertThrows(CiamDomainException.InvalidLifecycleTransition::class.java) {
                customer.changeLifecycleStage(LifecycleStage.LEAD)
            }
        }

        @Test
        fun `inactive customer throws CustomerInactive`() {
            val inactive = customer.copy(isActive = false)
            assertThrows(CiamDomainException.CustomerInactive::class.java) {
                inactive.changeLifecycleStage(LifecycleStage.QUALIFIED)
            }
        }
    }

    // ── qualifyLead ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("qualifyLead")
    inner class QualifyLead {

        @Test
        fun `qualifies a LEAD and returns LifecycleStageChanged event`() {
            val result = customer.qualifyLead()

            assertEquals(LifecycleStage.QUALIFIED, result.aggregate.lifecycleStage)
            assertEquals(1, result.events.size)
            val event = result.events.first() as CiamDomainEvent.LifecycleStageChanged
            assertEquals("LEAD", event.fromStage)
            assertEquals("QUALIFIED", event.toStage)
        }

        @Test
        fun `throws LeadNotQualified when not in LEAD stage`() {
            val qualified = customer.changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
            val ex = assertThrows(CiamDomainException.LeadNotQualified::class.java) {
                qualified.qualifyLead()
            }
            assertEquals(qualified.customerId.toString(), ex.customerId)
            assertEquals("QUALIFIED", ex.currentStage)
        }

        @Test
        fun `throws LeadNotQualified for CHURNED customer`() {
            val churned = customer.changeLifecycleStage(LifecycleStage.CHURNED).aggregate
            assertThrows(CiamDomainException.LeadNotQualified::class.java) {
                churned.qualifyLead()
            }
        }
    }

    // ── disqualifyLead ────────────────────────────────────────────────────

    @Nested
    @DisplayName("disqualifyLead")
    inner class DisqualifyLead {

        @Test
        fun `disqualifies a QUALIFIED customer back to LEAD`() {
            val qualified = customer.changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
            val result = qualified.disqualifyLead()

            assertEquals(LifecycleStage.LEAD, result.aggregate.lifecycleStage)
        }

        @Test
        fun `emits LeadDisqualified event with correct reason`() {
            val qualified = customer.changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
            val result = qualified.disqualifyLead(DisqualificationReason.CONSENT_REVOKED)

            val event = result.events.first() as CiamDomainEvent.LeadDisqualified
            assertEquals(customer.customerId.toString(), event.entityId)
            assertEquals(DisqualificationReason.CONSENT_REVOKED, event.reason)
        }

        @Test
        fun `defaults to MANUAL_DISQUALIFICATION reason`() {
            val qualified = customer.changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
            val result = qualified.disqualifyLead()

            val event = result.events.first() as CiamDomainEvent.LeadDisqualified
            assertEquals(DisqualificationReason.MANUAL_DISQUALIFICATION, event.reason)
        }

        @Test
        fun `throws when customer is not QUALIFIED`() {
            assertThrows(CiamDomainException.InvalidLifecycleTransition::class.java) {
                customer.disqualifyLead()
            }
        }

        @Test
        fun `throws when customer is already LEAD`() {
            assertThrows(CiamDomainException.InvalidLifecycleTransition::class.java) {
                customer.disqualifyLead()
            }
        }
    }

    // ── deactivate ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deactivate")
    inner class Deactivate {

        @Test
        fun `deactivates an active customer`() {
            val result = customer.deactivate("requested_by_customer")

            assertFalse(result.aggregate.isActive)
        }

        @Test
        fun `emits CustomerDeactivated event with reason`() {
            val result = customer.deactivate("fraud_detected")

            val event = result.events.first() as CiamDomainEvent.CustomerDeactivated
            assertEquals(customer.customerId.toString(), event.entityId)
            assertEquals("fraud_detected", event.reason)
        }

        @Test
        fun `deactivate with null reason`() {
            val result = customer.deactivate()

            val event = result.events.first() as CiamDomainEvent.CustomerDeactivated
            assertNull(event.reason)
        }

        @Test
        fun `can deactivate an already-inactive customer (idempotent-ish)`() {
            val inactive = customer.copy(isActive = false)
            val result = inactive.deactivate()
            assertFalse(result.aggregate.isActive)
        }
    }

    // ── reactivate ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reactivate")
    inner class Reactivate {

        @Test
        fun `reactivates a CHURNED customer to LEAD`() {
            val churned = customer.changeLifecycleStage(LifecycleStage.CHURNED).aggregate
            val result = churned.reactivate()

            assertTrue(result.aggregate.isActive)
            assertEquals(LifecycleStage.LEAD, result.aggregate.lifecycleStage)
        }

        @Test
        fun `emits CustomerReactivated event`() {
            val churned = customer.changeLifecycleStage(LifecycleStage.CHURNED).aggregate
            val result = churned.reactivate()

            val event = result.events.first() as CiamDomainEvent.CustomerReactivated
            assertEquals(customer.customerId.toString(), event.entityId)
        }

        @Test
        fun `throws InvalidReactivation when not CHURNED`() {
            val ex = assertThrows(CiamDomainException.InvalidReactivation::class.java) {
                customer.reactivate()
            }
            assertEquals(customer.customerId.toString(), ex.customerId)
            assertEquals("LEAD", ex.currentStage)
        }

        @Test
        fun `throws InvalidReactivation for QUALIFIED customer`() {
            val qualified = customer.changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
            assertThrows(CiamDomainException.InvalidReactivation::class.java) {
                qualified.reactivate()
            }
        }
    }

    // ── updateConsent ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateConsent")
    inner class UpdateConsent {

        @Test
        fun `adds new consent record`() {
            val result = customer.updateConsent("email_marketing", true)

            assertEquals(1, result.aggregate.consents.size)
            assertEquals("email_marketing", result.aggregate.consents[0].purpose)
            assertTrue(result.aggregate.consents[0].granted)
        }

        @Test
        fun `updates existing consent record`() {
            val withConsent = customer.updateConsent("email_marketing", true).aggregate
            val result = withConsent.updateConsent("email_marketing", false)

            assertEquals(1, result.aggregate.consents.size)
            assertFalse(result.aggregate.consents[0].granted)
        }

        @Test
        fun `emits ConsentChanged event`() {
            val result = customer.updateConsent("sms_marketing", true)

            val event = result.events.first() as CiamDomainEvent.ConsentChanged
            assertEquals(customer.customerId.toString(), event.entityId)
            assertEquals("sms_marketing", event.purpose)
            assertTrue(event.granted)
        }

        @Test
        fun `revoking consent emits granted=false`() {
            val withConsent = customer.updateConsent("email_marketing", true).aggregate
            val result = withConsent.updateConsent("email_marketing", false)

            val event = result.events.first() as CiamDomainEvent.ConsentChanged
            assertFalse(event.granted)
        }

        @Test
        fun `multiple consents accumulate`() {
            val step1 = customer.updateConsent("email_marketing", true).aggregate
            val step2 = step1.updateConsent("sms_marketing", true).aggregate
            val step3 = step2.updateConsent("analytics", false).aggregate

            assertEquals(3, step3.consents.size)
        }
    }

    // ── registerContact ───────────────────────────────────────────────────

    @Nested
    @DisplayName("registerContact")
    inner class RegisterContact {

        @Test
        fun `adds contact to customer`() {
            val result = customer.registerContact(
                firstName = "Jane",
                lastName = "Doe",
                email = EmailAddress("jane@example.com"),
            )

            assertEquals(1, result.aggregate.contacts.size)
            assertEquals("Jane", result.aggregate.contacts[0].firstName)
            assertEquals("jane@example.com", result.aggregate.contacts[0].email.value)
        }

        @Test
        fun `returns no events`() {
            val result = customer.registerContact(
                firstName = "Jane",
                lastName = "Doe",
                email = EmailAddress("jane@example.com"),
            )
            assertTrue(result.events.isEmpty())
        }

        @Test
        fun `throws DuplicateContact for same email`() {
            val withContact = customer.registerContact(
                firstName = "Jane",
                lastName = "Doe",
                email = EmailAddress("jane@example.com"),
            ).aggregate

            assertThrows(CiamDomainException.DuplicateContact::class.java) {
                withContact.registerContact(
                    firstName = "Janet",
                    lastName = "Smith",
                    email = EmailAddress("jane@example.com"),
                )
            }
        }

        @Test
        fun `allows different emails`() {
            val step1 = customer.registerContact(
                firstName = "Jane",
                lastName = "Doe",
                email = EmailAddress("jane@example.com"),
            ).aggregate
            val step2 = step1.registerContact(
                firstName = "John",
                lastName = "Doe",
                email = EmailAddress("john@example.com"),
            ).aggregate

            assertEquals(2, step2.contacts.size)
        }
    }

    // ── verifyEmail ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyEmail")
    inner class VerifyEmail {

        @Test
        fun `emits EmailVerified event`() {
            val result = customer.verifyEmail("test@example.com")

            val event = result.events.first() as CiamDomainEvent.EmailVerified
            assertEquals(customer.customerId.toString(), event.entityId)
            assertEquals("test@example.com", event.email)
        }

        @Test
        fun `preserves all other fields`() {
            val result = customer.verifyEmail("test@example.com")

            assertEquals(customer.displayName, result.aggregate.displayName)
            assertEquals(customer.lifecycleStage, result.aggregate.lifecycleStage)
            assertEquals(customer.isActive, result.aggregate.isActive)
        }
    }

    // ── Full Lifecycle Journey ────────────────────────────────────────────

    @Nested
    @DisplayName("full lifecycle journey")
    inner class FullJourney {

        @Test
        fun `complete happy path - register, qualify, opportunity, customer, advocate`() {
            val events = mutableListOf<CiamDomainEvent>()

            fun apply(result: DomainResult<Customer>): Customer {
                events.addAll(result.events)
                return result.aggregate
            }

            var c = customer
            c = apply(c.changeLifecycleStage(LifecycleStage.QUALIFIED))
            c = apply(c.changeLifecycleStage(LifecycleStage.OPPORTUNITY))
            c = apply(c.changeLifecycleStage(LifecycleStage.CUSTOMER))
            c = apply(c.changeLifecycleStage(LifecycleStage.ADVOCATE))

            assertEquals(LifecycleStage.ADVOCATE, c.lifecycleStage)
            assertEquals(4, events.size)
            assertTrue(events.all { it is CiamDomainEvent.LifecycleStageChanged })
        }

        @Test
        fun `churn and reactivation cycle`() {
            // Full churn: transition to CHURNED stage, then deactivate.
            // Reactivation requires CHURNED stage + inactive status.
            val churned = customer
                .changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
                .changeLifecycleStage(LifecycleStage.OPPORTUNITY).aggregate
                .changeLifecycleStage(LifecycleStage.CUSTOMER).aggregate
                .changeLifecycleStage(LifecycleStage.CHURNED).aggregate
                .deactivate().aggregate

            assertFalse(churned.isActive)
            assertEquals(LifecycleStage.CHURNED, churned.lifecycleStage)

            val reactivated = churned.reactivate().aggregate
            assertTrue(reactivated.isActive)
            assertEquals(LifecycleStage.LEAD, reactivated.lifecycleStage)
        }

        @Test
        fun `disqualification resets to LEAD`() {
            val qualified = customer.changeLifecycleStage(LifecycleStage.QUALIFIED).aggregate
            val disqualified = qualified.disqualifyLead(DisqualificationReason.SCORE_BELOW_THRESHOLD).aggregate

            assertEquals(LifecycleStage.LEAD, disqualified.lifecycleStage)
            assertTrue(disqualified.isActive)
        }
    }

    // ── Immutability ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("immutability")
    inner class Immutability {

        @Test
        fun `original customer is not modified after transition`() {
            val originalStage = customer.lifecycleStage
            customer.changeLifecycleStage(LifecycleStage.QUALIFIED)
            assertEquals(originalStage, customer.lifecycleStage)
        }

        @Test
        fun `contacts list is not shared after registerContact`() {
            val result = customer.registerContact(
                firstName = "Jane",
                lastName = "Doe",
                email = EmailAddress("jane@example.com"),
            )
            // Original should still be empty
            assertTrue(customer.contacts.isEmpty())
            // Result should have the contact
            assertEquals(1, result.aggregate.contacts.size)
        }
    }
}
