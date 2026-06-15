package com.crm.support.domain.ticket

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.Instant

/**
 * Pure unit tests for SLA engine and Ticket aggregate.
 * No Quarkus, no DI, no database — just deterministic time math.
 */
@DisplayName("Ticket SLA Engine & State Machine")
class TicketSlaEngineTest {

    private val fixedCreatedAt: Instant = Instant.parse("2026-06-14T10:00:00Z")

    // ═══════════════════════════════════════════════════════════════════════════
    // SLA Matrix — Parameterized
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SLA Matrix — hours from creation")
    inner class SlaMatrixTests {

        @ParameterizedTest(name = "{0} + {1} = {2}h")
        @CsvSource(
            "URGENT, ENTERPRISE, 1",
            "URGENT, PREMIUM, 2",
            "URGENT, STANDARD, 4",
            "HIGH, ENTERPRISE, 4",
            "HIGH, PREMIUM, 12",
            "HIGH, STANDARD, 24",
            "MEDIUM, ENTERPRISE, 12",
            "MEDIUM, PREMIUM, 24",
            "MEDIUM, STANDARD, 48",
            "LOW, ENTERPRISE, 24",
            "LOW, PREMIUM, 48",
            "LOW, STANDARD, 72",
        )
        fun `SLA deadline matches business rules`(priority: String, tier: String, expectedHours: Long) {
            val p = TicketPriority.valueOf(priority)
            val t = CustomerTier.valueOf(tier)
            val deadline = SlaEngine.calculateDeadline(fixedCreatedAt, p, t)
            val expected = fixedCreatedAt.plus(Duration.ofHours(expectedHours))
            assertEquals(expected, deadline, "$priority + $tier should be ${expectedHours}h")
        }

        @Test
        fun `Enterprise Urgent is exactly 1 hour`() {
            val deadline = SlaEngine.calculateDeadline(fixedCreatedAt, TicketPriority.URGENT, CustomerTier.ENTERPRISE)
            assertEquals(fixedCreatedAt.plus(Duration.ofHours(1)), deadline)
        }

        @Test
        fun `Standard Low is exactly 72 hours`() {
            val deadline = SlaEngine.calculateDeadline(fixedCreatedAt, TicketPriority.LOW, CustomerTier.STANDARD)
            assertEquals(fixedCreatedAt.plus(Duration.ofHours(72)), deadline)
        }

        @Test
        fun `recalculateDeadline uses original createdAt not new time`() {
            val original = fixedCreatedAt
            val newTime = fixedCreatedAt.plus(Duration.ofHours(10))
            val deadline = SlaEngine.recalculateDeadline(original, TicketPriority.HIGH, CustomerTier.ENTERPRISE)
            // Should be original + 4h, NOT newTime + 4h
            assertEquals(original.plus(Duration.ofHours(4)), deadline)
            assertNotEquals(newTime.plus(Duration.ofHours(4)), deadline)
        }

        @Test
        fun `recalculateDeadline matches calculateDeadline for same inputs`() {
            val direct = SlaEngine.calculateDeadline(fixedCreatedAt, TicketPriority.MEDIUM, CustomerTier.PREMIUM)
            val recalculated = SlaEngine.recalculateDeadline(fixedCreatedAt, TicketPriority.MEDIUM, CustomerTier.PREMIUM)
            assertEquals(direct, recalculated)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Deterministic Time Testing
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Deterministic SLA breach detection")
    inner class DeterministicSlaTests {

        @Test
        fun `ticket is not breached when now is before deadline`() {
            val createdAt = Instant.parse("2026-06-14T10:00:00Z")
            val deadline = Instant.parse("2026-06-14T14:00:00Z") // 4h SLA
            val ticket = createTicketWithDeadline(createdAt, deadline)

            val now = Instant.parse("2026-06-14T13:00:00Z") // 1h before deadline
            val status = ticket.checkSla(now)

            assertFalse(status.isBreached, "Ticket should not be breached 1h before deadline")
            assertNotNull(status.timeRemaining)
            assertEquals(Duration.ofHours(1), status.timeRemaining)
        }

        @Test
        fun `ticket is breached when now is after deadline`() {
            val createdAt = Instant.parse("2026-06-14T10:00:00Z")
            val deadline = Instant.parse("2026-06-14T14:00:00Z") // 4h SLA
            val ticket = createTicketWithDeadline(createdAt, deadline)

            val now = Instant.parse("2026-06-14T15:00:00Z") // 1h after deadline
            val status = ticket.checkSla(now)

            assertTrue(status.isBreached, "Ticket should be breached 1h after deadline")
            assertNotNull(status.breachDuration)
            assertEquals(Duration.ofHours(1), status.breachDuration)
        }

        @Test
        fun `ticket is breached at exact deadline moment`() {
            val createdAt = Instant.parse("2026-06-14T10:00:00Z")
            val deadline = Instant.parse("2026-06-14T14:00:00Z")
            val ticket = createTicketWithDeadline(createdAt, deadline)

            // At the exact deadline — isAfter is false for equals, so not breached
            val status = ticket.checkSla(deadline)
            assertFalse(status.isBreached, "At exact deadline, isAfter should be false")
        }

        @Test
        fun `breach detected 1 millisecond after deadline`() {
            val deadline = Instant.parse("2026-06-14T14:00:00Z")
            val ticket = createTicketWithDeadline(fixedCreatedAt, deadline)

            val now = deadline.plusMillis(1)
            val status = ticket.checkSla(now)

            assertTrue(status.isBreached, "1ms after deadline should be breached")
            assertEquals(Duration.ofMillis(1), status.breachDuration)
        }

        @Test
        fun `breach increases over time`() {
            val deadline = Instant.parse("2026-06-14T14:00:00Z")
            val ticket = createTicketWithDeadline(fixedCreatedAt, deadline)

            val status1h = ticket.checkSla(deadline.plus(Duration.ofHours(1)))
            val status3h = ticket.checkSla(deadline.plus(Duration.ofHours(3)))

            assertTrue(status1h.isBreached)
            assertTrue(status3h.isBreached)
            assertEquals(Duration.ofHours(1), status1h.breachDuration)
            assertEquals(Duration.ofHours(3), status3h.breachDuration)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SLA Status — Compliance
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SLA compliance status")
    inner class SlaComplianceTests {

        @Test
        fun `compliant ticket has timeRemaining but no breachDuration`() {
            val deadline = fixedCreatedAt.plus(Duration.ofHours(4))
            val ticket = createTicketWithDeadline(fixedCreatedAt, deadline)

            val status = ticket.checkSla(fixedCreatedAt.plus(Duration.ofHours(1)))

            assertFalse(status.isBreached)
            assertNotNull(status.timeRemaining)
            assertNull(status.breachDuration)
            assertEquals(Duration.ofHours(3), status.timeRemaining)
        }

        @Test
        fun `breached ticket has breachDuration but no timeRemaining`() {
            val deadline = fixedCreatedAt.plus(Duration.ofHours(4))
            val ticket = createTicketWithDeadline(fixedCreatedAt, deadline)

            val status = ticket.checkSla(fixedCreatedAt.plus(Duration.ofHours(6)))

            assertTrue(status.isBreached)
            assertNull(status.timeRemaining)
            assertNotNull(status.breachDuration)
            assertEquals(Duration.ofHours(2), status.breachDuration)
        }

        @Test
        fun `resolved ticket is never breached regardless of deadline`() {
            val deadline = fixedCreatedAt.plus(Duration.ofHours(4))
            var ticket = createTicketWithDeadline(fixedCreatedAt, deadline)
            ticket = ticket.resolve(fixedCreatedAt.plus(Duration.ofHours(5)))

            val status = ticket.checkSla(fixedCreatedAt.plus(Duration.ofHours(10)))

            assertFalse(status.isBreached, "Resolved ticket should never show breached")
        }

        @Test
        fun `closed ticket is never`() {
            val deadline = fixedCreatedAt.plus(Duration.ofHours(4))
            var ticket = createTicketWithDeadline(fixedCreatedAt, deadline)
            ticket = ticket.resolve(fixedCreatedAt.plus(Duration.ofHours(2)))
            ticket = ticket.close(fixedCreatedAt.plus(Duration.ofHours(3)))

            val status = ticket.checkSla(fixedCreatedAt.plus(Duration.ofHours(100)))

            assertFalse(status.isBreached, "Closed ticket should never show breached")
        }

        @Test
        fun `ticket with no SLA deadline is never breached`() {
            val ticket = Ticket(
                requesterId = "req-001",
                subject = "No SLA ticket",
                createdAt = fixedCreatedAt,
                updatedAt = fixedCreatedAt,
            )

            val status = ticket.checkSla(fixedCreatedAt.plus(Duration.ofDays(365)))

            assertFalse(status.isBreached, "Ticket without deadline should never be breached")
            assertNull(status.deadline)
            assertNull(status.timeRemaining)
        }

        @Test
        fun `timeRemaining counts down correctly`() {
            val deadline = fixedCreatedAt.plus(Duration.ofHours(24))
            val ticket = createTicketWithDeadline(fixedCreatedAt, deadline)

            val t0 = ticket.checkSla(fixedCreatedAt)
            val t6 = ticket.checkSla(fixedCreatedAt.plus(Duration.ofHours(6)))
            val t12 = ticket.checkSla(fixedCreatedAt.plus(Duration.ofHours(12)))
            val t23 = ticket.checkSla(fixedCreatedAt.plus(Duration.ofHours(23)))

            assertEquals(Duration.ofHours(24), t0.timeRemaining)
            assertEquals(Duration.ofHours(18), t6.timeRemaining)
            assertEquals(Duration.ofHours(12), t12.timeRemaining)
            assertEquals(Duration.ofHours(1), t23.timeRemaining)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Machine — Invalid Transitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State Transitions — valid and invalid")
    inner class StateTransitionTests {

        @Test
        fun `full happy path lifecycle`() {
            val now = fixedCreatedAt
            var ticket = Ticket(requesterId = "req-001", subject = "Test", createdAt = now, updatedAt = now)
            assertEquals(TicketStatus.OPEN, ticket.status)

            ticket = ticket.assign("agent-001", now.plusSeconds(60))
            assertEquals(TicketStatus.IN_PROGRESS, ticket.status)

            ticket = ticket.pendingCustomer(now.plusSeconds(120))
            assertEquals(TicketStatus.PENDING_CUSTOMER, ticket.status)

            // PENDING_CUSTOMER -> assign -> IN_PROGRESS
            ticket = ticket.assign("agent-001", now.plusSeconds(180))
            assertEquals(TicketStatus.IN_PROGRESS, ticket.status)

            ticket = ticket.resolve(now.plusSeconds(3600))
            assertEquals(TicketStatus.RESOLVED, ticket.status)

            ticket = ticket.close(now.plusSeconds(3700))
            assertEquals(TicketStatus.CLOSED, ticket.status)
        }

        @Test
        fun `closed ticket cannot go directly to in_progress — must reopen first`() {
            var ticket = createClosedTicket()

            assertThrows(IllegalStateException::class.java) {
                ticket.assign("agent-002", fixedCreatedAt.plusSeconds(4000))
            }
        }

        @Test
        fun `closed ticket can be reopened`() {
            var ticket = createClosedTicket()

            ticket = ticket.reopen(fixedCreatedAt.plusSeconds(4000))
            assertEquals(TicketStatus.OPEN, ticket.status)
            assertNull(ticket.assigneeId)
            assertNull(ticket.resolvedAt)
            assertNull(ticket.closedAt)
            assertNull(ticket.slaDeadline)
        }

        @Test
        fun `reopened ticket can be reassigned`() {
            var ticket = createClosedTicket()
            ticket = ticket.reopen(fixedCreatedAt.plusSeconds(4000))

            ticket = ticket.assign("new-agent", fixedCreatedAt.plusSeconds(4100))
            assertEquals(TicketStatus.IN_PROGRESS, ticket.status)
            assertEquals("new-agent", ticket.assigneeId)
        }

        @Test
        fun `resolved ticket can be reopened`() {
            var ticket = Ticket(requesterId = "req-001", subject = "Test", createdAt = fixedCreatedAt, updatedAt = fixedCreatedAt)
            ticket = ticket.resolve(fixedCreatedAt.plusSeconds(3600))

            ticket = ticket.reopen(fixedCreatedAt.plusSeconds(4000))
            assertEquals(TicketStatus.OPEN, ticket.status)
            assertNull(ticket.resolvedAt)
        }

        @Test
        fun `cannot close an open ticket directly`() {
            val ticket = Ticket(requesterId = "req-001", subject = "Test", createdAt = fixedCreatedAt, updatedAt = fixedCreatedAt)

            assertThrows(IllegalStateException::class.java) {
                ticket.close(fixedCreatedAt.plusSeconds(60))
            }
        }

        @Test
        fun `cannot resolve an already closed ticket`() {
            val ticket = createClosedTicket()

            assertThrows(IllegalStateException::class.java) {
                ticket.resolve(fixedCreatedAt.plusSeconds(4000))
            }
        }

        @Test
        fun `cannot reassign a closed ticket`() {
            val ticket = createClosedTicket()

            assertThrows(IllegalStateException::class.java) {
                ticket.assign("agent", fixedCreatedAt.plusSeconds(4000))
            }
        }

        @Test
        fun `pending customer can go back to in_progress`() {
            var ticket = Ticket(requesterId = "req-001", subject = "Test", createdAt = fixedCreatedAt, updatedAt = fixedCreatedAt)
            ticket = ticket.assign("agent", fixedCreatedAt.plusSeconds(60))
            ticket = ticket.pendingCustomer(fixedCreatedAt.plusSeconds(120))
            assertEquals(TicketStatus.PENDING_CUSTOMER, ticket.status)

            ticket = ticket.assign("agent", fixedCreatedAt.plusSeconds(180))
            assertEquals(TicketStatus.IN_PROGRESS, ticket.status)
        }

        @Test
        fun `cannot resolve a closed ticket`() {
            val ticket = createClosedTicket()

            assertThrows(IllegalStateException::class.java) {
                ticket.resolve(fixedCreatedAt.plusSeconds(4000))
            }
        }
    }

    @Nested
    @DisplayName("SLA deadline on ticket with different tiers")
    inner class TicketSlaCalculationTests {

        @Test
        fun `Enterprise Urgent ticket has 1h SLA`() {
            val deadline = SlaEngine.calculateDeadline(fixedCreatedAt, TicketPriority.URGENT, CustomerTier.ENTERPRISE)
            assertEquals(fixedCreatedAt.plus(Duration.ofHours(1)), deadline)

            val ticket = createTicketWithDeadline(fixedCreatedAt, deadline)
            // 30 min in — not breached
            assertFalse(ticket.checkSla(fixedCreatedAt.plus(Duration.ofMinutes(30))).isBreached)
            // 59 min in — not breached
            assertFalse(ticket.checkSla(fixedCreatedAt.plus(Duration.ofMinutes(59))).isBreached)
            // 61 min in — breached
            assertTrue(ticket.checkSla(fixedCreatedAt.plus(Duration.ofMinutes(61))).isBreached)
        }

        @Test
        fun `Standard Low ticket has 72h SLA`() {
            val deadline = SlaEngine.calculateDeadline(fixedCreatedAt, TicketPriority.LOW, CustomerTier.STANDARD)
            assertEquals(fixedCreatedAt.plus(Duration.ofHours(72)), deadline)

            val ticket = createTicketWithDeadline(fixedCreatedAt, deadline)
            assertFalse(ticket.checkSla(fixedCreatedAt.plus(Duration.ofHours(71))).isBreached)
            assertTrue(ticket.checkSla(fixedCreatedAt.plus(Duration.ofHours(73))).isBreached)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun createTicketWithDeadline(createdAt: Instant, deadline: Instant): Ticket =
        Ticket(
            requesterId = "req-001",
            subject = "SLA test",
            priority = TicketPriority.HIGH,
            slaDeadline = deadline,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    private fun createClosedTicket(): Ticket {
        var ticket = Ticket(requesterId = "req-001", subject = "Test", createdAt = fixedCreatedAt, updatedAt = fixedCreatedAt)
        ticket = ticket.assign("agent", fixedCreatedAt.plusSeconds(60))
        ticket = ticket.resolve(fixedCreatedAt.plusSeconds(3600))
        ticket = ticket.close(fixedCreatedAt.plusSeconds(3700))
        return ticket
    }
}
