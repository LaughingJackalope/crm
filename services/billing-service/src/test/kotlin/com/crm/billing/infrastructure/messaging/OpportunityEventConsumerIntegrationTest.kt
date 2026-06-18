
package com.crm.billing.infrastructure.messaging

import com.crm.test.TestTags

import com.crm.billing.domain.invoice.InvoiceStatus
import com.crm.billing.infrastructure.persistence.IncomingEventLogEntity
import com.crm.billing.infrastructure.persistence.IncomingEventLogRepository
import com.crm.billing.infrastructure.persistence.InvoiceEntity
import com.crm.billing.infrastructure.persistence.InvoiceRepository
import com.crm.billing.infrastructure.persistence.OutboxEventEntity
import com.crm.billing.infrastructure.persistence.OutboxEventRepository
import com.crm.billing.infrastructure.persistence.OutboxStatus

import com.crm.test.CrmIntegrationTestResourceLifecycleManager
import com.crm.test.EventTestProducer
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Integration tests for [OpportunityEventConsumer].
 *
 * Uses @QuarkusTest with Testcontainers for PostgreSQL and Kafka.
 * Tests verify the full pipeline: Kafka message → @Incoming consumer →
 * idempotency check → invoice creation → outbox write,
 * all within a transactional boundary.
 */
@QuarkusTestResource(BillingIntegrationTestResource::class)
@Tag(TestTags.INTEGRATION)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpportunityEventConsumerIntegrationTest {

    @Inject
    lateinit var invoiceRepository: InvoiceRepository

    @Inject
    lateinit var eventLogRepository: IncomingEventLogRepository

    @Inject
    lateinit var outboxEventRepository: OutboxEventRepository

    @Inject
    lateinit var testProducer: EventTestProducer

    companion object {
        const val SALES_OPPORTUNITY_TOPIC = "crm.sales.opportunity.closed"
        val TEST_OPPORTUNITY_ID: UUID = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-123456789012")
    }

    @BeforeEach
    @Transactional
    fun cleanUp() {
        // Clean up test data between tests
        IncomingEventLogEntity.delete("entityId", TEST_OPPORTUNITY_ID.toString())
        // Clean up invoices
        InvoiceEntity.list("opportunityId", TEST_OPPORTUNITY_ID.toString()).forEach {
            InvoiceEntity.delete("invoiceId", (it as InvoiceEntity).invoiceId)
        }
        // Clean up outbox events
        OutboxEventEntity.list("entityId", TEST_OPPORTUNITY_ID.toString()).forEach {
            OutboxEventEntity.delete("eventId", (it as OutboxEventEntity).eventId)
        }
    }

    @AfterAll
    fun tearDown() {
        testProducer.close()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Happy Path — Invoice Creation & Tracking
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Happy Path")
    inner class HappyPath {

        @Test
        @Order(1)
        fun `OpportunityClosed won event creates invoice with correct totals`() {
            val envelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "won"
            )

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val invoices = invoiceRepository.findByOpportunityId(TEST_OPPORTUNITY_ID.toString())
                assertThat(invoices).isNotNull

                val invoice = invoices!!
                assertThat(invoice.status).isEqualTo(InvoiceStatus.ISSUED)
                assertThat(invoice.customerId).isEqualTo("pending:$TEST_OPPORTUNITY_ID")
                assertThat(invoice.currency).isEqualTo("USD")

                // Verify financial invariants: subtotal + tax = total
                assertThat(invoice.total).isEqualTo(invoice.subtotal.add(invoice.totalTax))

                // The default line item: 1 × $999.99 at 8.25% tax
                assertThat(invoice.subtotal).isEqualTo(BigDecimal("999.99"))
                // Tax: 999.99 × 8.25 / 100 = 82.499175 → HALF_UP at scale 2 = 82.50
                assertThat(invoice.totalTax).isEqualTo(BigDecimal("82.50"))
                assertThat(invoice.total).isEqualTo(BigDecimal("1082.49"))
            }
        }

        @Test
        @Order(2)
        fun `OpportunityClosed won event writes idempotency record`() {
            val envelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "won"
            )

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val idempotencyKey = buildIdempotencyKey(TEST_OPPORTUNITY_ID.toString())
                assertThat(eventLogRepository.isProcessed(idempotencyKey)).isTrue()
            }
        }

        @Test
        @Order(3)
        fun `OpportunityClosed won event writes InvoiceGenerated to outbox`() {
            val envelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "won"
            )

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val pendingEvents = outboxEventRepository.findPending(100)
                val invoiceGeneratedEvent = pendingEvents.find {
                    it.eventType == "InvoiceGenerated" && it.source == "billing"
                }
                assertThat(invoiceGeneratedEvent).isNotNull
                assertThat(invoiceGeneratedEvent!!.entityType).isEqualTo("invoice")
                assertThat(invoiceGeneratedEvent.status).isEqualTo(OutboxStatus.PENDING)
            }
        }

        @Test
        @Order(4)
        fun `OpportunityClosed lost event does not create invoice`() {
            val envelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "lost"
            )

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            Thread.sleep(5000)

            val invoice = invoiceRepository.findByOpportunityId(TEST_OPPORTUNITY_ID.toString())
            assertThat(invoice).isNull()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Idempotency & Duplicate Protection
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Idempotency Enforcement")
    inner class IdempotencyEnforcement {

        @Test
        @Order(10)
        fun `duplicate OpportunityClosed event creates only one invoice`() {
            val envelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "won"
            )

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)
            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val invoice = invoiceRepository.findByOpportunityId(TEST_OPPORTUNITY_ID.toString())
                assertThat(invoice).isNotNull
                assertThat(invoice!!.status).isEqualTo(InvoiceStatus.ISSUED)
            }

            // Verify only one invoice exists in the database
            val entityCount = InvoiceEntity.find("opportunityId", TEST_OPPORTUNITY_ID.toString()).list()
            assertThat(entityCount).hasSize(1)
        }

        @Test
        @Order(11)
        fun `duplicate event creates only one outbox entry`() {
            val envelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "won"
            )

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)
            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val idempotencyKey = buildIdempotencyKey(TEST_OPPORTUNITY_ID.toString())
                assertThat(eventLogRepository.isProcessed(idempotencyKey)).isTrue()
            }

            // Only one outbox event
            val outboxEvents = OutboxEventEntity.list("eventType", "InvoiceGenerated")
            assertThat(outboxEvents).hasSize(1)
        }

        @Test
        @Order(12)
        fun `rapid duplicate events do not cause constraint violations`() {
            val envelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "won"
            )

            repeat(5) {
                testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)
                Thread.sleep(100)
            }

            await().atMost(Duration.ofSeconds(20)).untilAsserted {
                val invoice = invoiceRepository.findByOpportunityId(TEST_OPPORTUNITY_ID.toString())
                assertThat(invoice).isNotNull
            }

            // Exactly one invoice despite 5 deliveries
            val entityCount = InvoiceEntity.find("opportunityId", TEST_OPPORTUNITY_ID.toString()).list()
            assertThat(entityCount).hasSize(1)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Transactional Integrity & DLQ
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transactional Integrity & DLQ")
    inner class TransactionalIntegrityAndDlq {

        @Test
        @Order(20)
        fun `poison pill with missing opportunityId rolls back cleanly`() {
            val poisonPill = """
                {
                    "eventType": "OpportunityClosed",
                    "source": "sales",
                    "payload": {
                        "outcome": "won",
                        "closedAt": "${OffsetDateTime.now()}"
                    }
                }
            """.trimIndent()

            testProducer.send(SALES_OPPORTUNITY_TOPIC, "missing-id-key", poisonPill)

            Thread.sleep(8000)

            // No invoice created
            val invoices = InvoiceEntity.list("opportunityId", "missing-id-key")
            assertThat(invoices).isEmpty()
        }

        @Test
        @Order(21)
        fun `poison pill with invalid JSON structure does not crash consumer`() {
            val poisonPill = """{"eventType": "OpportunityClosed", "source": "sales", "payload": }"""

            testProducer.send(SALES_OPPORTUNITY_TOPIC, "bad-json-key", poisonPill)

            Thread.sleep(8000)

            // Consumer should survive — verify by sending a valid event afterward
            val validOpportunityId = UUID.randomUUID()
            val validEnvelope = buildOpportunityClosedEnvelope(
                opportunityId = validOpportunityId,
                outcome = "won"
            )

            testProducer.send(SALES_OPPORTUNITY_TOPIC, validOpportunityId.toString(), validEnvelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val invoice = invoiceRepository.findByOpportunityId(validOpportunityId.toString())
                assertThat(invoice).isNotNull
            }
        }

        @Test
        @Order(22)
        fun `consumer survives poison pill and processes subsequent valid events`() {
            val poisonPill = """
                {
                    "eventType": "OpportunityClosed",
                    "source": "sales",
                    "payload": {
                        "opportunityId": null,
                        "outcome": "won",
                        "closedAt": "${OffsetDateTime.now()}"
                    }
                }
            """.trimIndent()

            val validOpportunityId = UUID.randomUUID()
            val validEnvelope = buildOpportunityClosedEnvelope(
                opportunityId = validOpportunityId,
                outcome = "won"
            )

            // Send poison pill first, then valid event
            testProducer.send(SALES_OPPORTUNITY_TOPIC, "null-id", poisonPill)
            Thread.sleep(8000)
            testProducer.send(SALES_OPPORTUNITY_TOPIC, validOpportunityId.toString(), validEnvelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val invoice = invoiceRepository.findByOpportunityId(validOpportunityId.toString())
                assertThat(invoice).isNotNull
                assertThat(invoice!!.status).isEqualTo(InvoiceStatus.ISSUED)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Financial Precision Verification (Integration)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Financial Precision — persisted values")
    inner class FinancialPrecisionTests {

        @Test
        @Order(30)
        fun `persisted invoice has exact BigDecimal scale matching domain calculations`() {
            val envelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "won"
            )

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val entity = InvoiceEntity.findByOpportunityId(TEST_OPPORTUNITY_ID.toString())
                assertThat(entity).isNotNull

                // Verify exact scale on persisted values
                assertThat(entity!!.subtotal.scale()).isEqualTo(2)
                assertThat(entity.totalTax.scale()).isEqualTo(2)
                assertThat(entity.total.scale()).isEqualTo(2)

                // Verify exact values
                assertThat(entity.subtotal).isEqualTo(BigDecimal("999.99"))
                assertThat(entity.totalTax).isEqualTo(BigDecimal("82.50"))
                assertThat(entity.total).isEqualTo(BigDecimal("1082.49"))
            }
        }

        @Test
        @Order(31)
        fun `persisted line item has correct tax calculation with HALF_UP rounding`() {
            val envelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "won"
            )

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val entity = InvoiceEntity.findByOpportunityId(TEST_OPPORTUNITY_ID.toString())
                assertThat(entity).isNotNull
                assertThat(entity!!.lineItems).hasSize(1)

                val lineItem = entity.lineItems.first()
                // 1 × 999.99 = 999.99
                assertThat(lineItem.lineTotal).isEqualTo(BigDecimal("999.99"))
                // Tax: 999.99 × 8.25 / 100 = 82.499175 → HALF_UP → 82.50
                assertThat(lineItem.taxAmount).isEqualTo(BigDecimal("82.50"))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-Event Interaction
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cross-Event Interaction")
    inner class CrossEventInteraction {

        @Test
        @Order(40)
        fun `won then lost event for same opportunity creates only one invoice`() {
            val wonEnvelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "won"
            )
            val lostEnvelope = buildOpportunityClosedEnvelope(
                opportunityId = TEST_OPPORTUNITY_ID,
                outcome = "lost"
            )

            // Send won first, then lost
            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), wonEnvelope)
            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), lostEnvelope)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val invoice = invoiceRepository.findByOpportunityId(TEST_OPPORTUNITY_ID.toString())
                assertThat(invoice).isNotNull
                assertThat(invoice!!.status).isEqualTo(InvoiceStatus.ISSUED)
            }

            // Only one invoice
            val entityCount = InvoiceEntity.find("opportunityId", TEST_OPPORTUNITY_ID.toString()).list()
            assertThat(entityCount).hasSize(1)
        }

        @Test
        @Order(41)
        fun `two different opportunities create two separate invoices`() {
            val opp1Id = UUID.randomUUID()
            val opp2Id = UUID.randomUUID()

            val envelope1 = buildOpportunityClosedEnvelope(opp1Id, "won")
            val envelope2 = buildOpportunityClosedEnvelope(opp2Id, "won")

            testProducer.send(SALES_OPPORTUNITY_TOPIC, opp1Id.toString(), envelope1)
            testProducer.send(SALES_OPPORTUNITY_TOPIC, opp2Id.toString(), envelope2)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val invoice1 = invoiceRepository.findByOpportunityId(opp1Id.toString())
                val invoice2 = invoiceRepository.findByOpportunityId(opp2Id.toString())
                assertThat(invoice1).isNotNull
                assertThat(invoice2).isNotNull
                assertThat(invoice1!!.invoiceId).isNotEqualTo(invoice2!!.invoiceId)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun buildOpportunityClosedEnvelope(
        opportunityId: UUID,
        outcome: String,
    ): String = """
        {
            "eventType": "OpportunityClosed",
            "source": "sales",
            "actorId": "test-actor",
            "payload": {
                "opportunityId": "$opportunityId",
                "outcome": "$outcome",
                "closedAt": "${OffsetDateTime.now()}"
            }
        }
    """.trimIndent()

    private fun buildIdempotencyKey(opportunityId: String): UUID {
        val raw = "invoice:$opportunityId"
        return UUID.nameUUIDFromBytes(raw.toByteArray())
    }
}
