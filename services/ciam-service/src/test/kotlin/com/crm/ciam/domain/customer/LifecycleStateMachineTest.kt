package com.crm.ciam.domain.customer

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [LifecycleStateMachine] — the pure transition graph.
 *
 * These tests verify the state machine in isolation without constructing
 * a full Customer aggregate. This is the fastest, most focused way to
 * validate the transition rules.
 */
@DisplayName("LifecycleStateMachine")
class LifecycleStateMachineTest {

    // ── Forward Progression (Happy Path) ──────────────────────────────────

    @Nested
    @DisplayName("allows forward progression")
    inner class ForwardProgression {

        @Test
        fun `LEAD → QUALIFIED is allowed`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.LEAD, LifecycleStage.QUALIFIED))
        }

        @Test
        fun `QUALIFIED → OPPORTUNITY is allowed`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.QUALIFIED, LifecycleStage.OPPORTUNITY))
        }

        @Test
        fun `OPPORTUNITY → CUSTOMER is allowed`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.OPPORTUNITY, LifecycleStage.CUSTOMER))
        }

        @Test
        fun `CUSTOMER → ADVOCATE is allowed`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.CUSTOMER, LifecycleStage.ADVOCATE))
        }
    }

    // ── Churn (Terminal Sink) ─────────────────────────────────────────────

    @Nested
    @DisplayName("allows churn from any active stage")
    inner class ChurnTransitions {

        @Test
        fun `LEAD → CHURNED is allowed`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.LEAD, LifecycleStage.CHURNED))
        }

        @Test
        fun `QUALIFIED → CHURNED is allowed`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.QUALIFIED, LifecycleStage.CHURNED))
        }

        @Test
        fun `OPPORTUNITY → CHURNED is allowed`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.OPPORTUNITY, LifecycleStage.CHURNED))
        }

        @Test
        fun `CUSTOMER → CHURNED is allowed`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.CUSTOMER, LifecycleStage.CHURNED))
        }

        @Test
        fun `ADVOCATE → CHURNED is allowed`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.ADVOCATE, LifecycleStage.CHURNED))
        }
    }

    // ── Disqualification (Backward) ───────────────────────────────────────

    @Nested
    @DisplayName("allows disqualification")
    inner class Disqualification {

        @Test
        fun `QUALIFIED → LEAD is allowed (disqualification)`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.QUALIFIED, LifecycleStage.LEAD))
        }
    }

    // ── Reactivation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("allows reactivation")
    inner class Reactivation {

        @Test
        fun `CHURNED → LEAD is allowed (reactivation)`() {
            assertTrue(LifecycleStateMachine.canTransition(LifecycleStage.CHURNED, LifecycleStage.LEAD))
        }
    }

    // ── Invalid Transitions ───────────────────────────────────────────────

    @Nested
    @DisplayName("rejects invalid transitions")
    inner class InvalidTransitions {

        @Test
        fun `LEAD → OPPORTUNITY is rejected (must pass through QUALIFIED)`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.LEAD, LifecycleStage.OPPORTUNITY))
        }

        @Test
        fun `LEAD → CUSTOMER is rejected (must pass through QUALIFIED, OPPORTUNITY)`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.LEAD, LifecycleStage.CUSTOMER))
        }

        @Test
        fun `LEAD → ADVOCATE is rejected`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.LEAD, LifecycleStage.ADVOCATE))
        }

        @Test
        fun `QUALIFIED → CUSTOMER is rejected (must pass through OPPORTUNITY)`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.QUALIFIED, LifecycleStage.CUSTOMER))
        }

        @Test
        fun `QUALIFIED → ADVOCATE is rejected`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.QUALIFIED, LifecycleStage.ADVOCATE))
        }

        @Test
        fun `OPPORTUNITY → ADVOCATE is rejected (must pass through CUSTOMER)`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.OPPORTUNITY, LifecycleStage.ADVOCATE))
        }

        @Test
        fun `OPPORTUNITY → LEAD is rejected`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.OPPORTUNITY, LifecycleStage.LEAD))
        }

        @Test
        fun `CUSTOMER → LEAD is rejected`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.CUSTOMER, LifecycleStage.LEAD))
        }

        @Test
        fun `CUSTOMER → OPPORTUNITY is rejected`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.CUSTOMER, LifecycleStage.OPPORTUNITY))
        }

        @Test
        fun `ADVOCATE → LEAD is rejected`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.ADVOCATE, LifecycleStage.LEAD))
        }

        @Test
        fun `ADVOCATE → QUALIFIED is rejected`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.ADVOCATE, LifecycleStage.QUALIFIED))
        }

        @Test
        fun `CHURNED → QUALIFIED is rejected (must go through LEAD)`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.CHURNED, LifecycleStage.QUALIFIED))
        }

        @Test
        fun `CHURNED → OPPORTUNITY is rejected`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.CHURNED, LifecycleStage.OPPORTUNITY))
        }

        @Test
        fun `CHURNED → CUSTOMER is rejected`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.CHURNED, LifecycleStage.CUSTOMER))
        }

        @Test
        fun `CHURNED → ADVOCATE is rejected`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.CHURNED, LifecycleStage.ADVOCATE))
        }

        @Test
        fun `CHURNED → CHURNED is rejected (self-loop)`() {
            assertFalse(LifecycleStateMachine.canTransition(LifecycleStage.CHURNED, LifecycleStage.CHURNED))
        }
    }

    // ── Self-Loops (All Rejected) ─────────────────────────────────────────

    @Nested
    @DisplayName("rejects all self-loops")
    inner class SelfLoops {

        @ParameterizedTest
        @EnumSource(LifecycleStage::class)
        fun `self-transition is never allowed`(stage: LifecycleStage) {
            assertFalse(
                LifecycleStateMachine.canTransition(stage, stage),
                "Self-loop $stage → $stage should not be allowed"
            )
        }
    }

    // ── Allowed Targets ───────────────────────────────────────────────────

    @Nested
    @DisplayName("allowedTargets returns correct sets")
    inner class AllowedTargets {

        @Test
        fun `LEAD can reach QUALIFIED and CHURNED`() {
            val targets = LifecycleStateMachine.allowedTargets(LifecycleStage.LEAD)
            assertTrue(targets.contains(LifecycleStage.QUALIFIED))
            assertTrue(targets.contains(LifecycleStage.CHURNED))
            assertTrue(targets.size == 2)
        }

        @Test
        fun `ADVOCATE can only reach CHURNED`() {
            val targets = LifecycleStateMachine.allowedTargets(LifecycleStage.ADVOCATE)
            assertTrue(targets == setOf(LifecycleStage.CHURNED))
        }

        @Test
        fun `CHURNED can only reach LEAD`() {
            val targets = LifecycleStateMachine.allowedTargets(LifecycleStage.CHURNED)
            assertTrue(targets == setOf(LifecycleStage.LEAD))
        }
    }
}
