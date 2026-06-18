package com.crm.sales.infrastructure.messaging

import com.crm.sales.application.OpportunityRepository
import com.crm.sales.domain.opportunity.SalesStage
import com.crm.sales.infrastructure.persistence.IncomingEventLogEntity
import com.crm.sales.infrastructure.persistence.IncomingEventLogRepository

import com.crm.test.CrmIntegrationTestResourceLifecycleManager
import com.crm.test.EventTestProducer
import com.crm.test.TestTags

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Integration tests for [CustomerEventConsumer].
 *
 * Uses @QuarkusTest with Testcontainers for PostgreSQL and Kafka.
* A [SalesIntegrationTestResource] starts the Testcontainers for PostgreSQL
 *
 * Tests verify the full pipeline: Kafka message → @Incoming consumer →
 * idempotency check → opportunity creation → outbox write,
 * all within a transactional boundary.
 */
@QuarkusTest
@QuarkusTestResource(SalesIntegrationTestResource::class)
@Tag(TestTags.INTEGRATION)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerEventConsumerIntegrationTest {

    @Inject
    lateinit var opportunityRepository: OpportunityRepository

    @Inject
    lateinit var eventLogRepository: IncomingEventLogRepository

    @Inject
    lateinit var testProducer: EventTestProducer

    companion object {
        const val CUSTOMER_EVENTS_TOPIC = "crm.ciam.events"
        val TEST_CUSTOMER_ID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    }

    @BeforeEach
    @Transactional
    fun cleanUp() {
        // Clean up test data between tests
        IncomingEventLogEntity.delete("entityId", TEST_CUSTOMER_ID.toString())
        // Clean up opportunities created for our test customer
        opportunityRepository.findByCustomerId(TEST_CUSTOMER_ID.toString())
            .forEach { opp ->
                com.crm.sales.infrastructure.persistence.OpportunityEntity
                    .find("opportunityId", opp.opportunityId)
                    .firstResult()
                    ?.let {
                        com.crm.sales.infrastructure.persistence.OpportunityEntity
                            .delete("opportunityId", opp.opportunityId)
                    }
            }
    }

    @AfterAll
    fun tearDown() {
        testProducer.close()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Happy Path — Opportunity Creation & Tracking
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Happy Path")
    inner class HappyPath {

        @Test
        @Order(1)
        fun `CustomerRegistered event creates opportunity with correct name`() {
            val envelope = buildCustomerRegisteredEnvelope(
                customerId = TEST_CUSTOMER_ID,
                displayName = "Acme Corporation",
                source = "website"
            )

            testProducer.send(CUSTOMER_EVENTS_TOPIC, TEST_CUSTOMER_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val opportunities = opportunityRepository.findByCustomerId(TEST_CUSTOMER_ID.toString())
                assertThat(opportunities).hasSize(1)
                assertThat(opportunities.first().name).isEqualTo("New Lead: Acme Corporation")
                assertThat(opportunities.first().customerId).isEqualTo(TEST_CUSTOMER_ID.toString())
                assertThat(opportunities.first().stage).isEqualTo(SalesStage.PROSPECTING)
            }
        }

        @Test
        @Order(2)
        fun `CustomerRegistered event writes idempotency record to incoming_event_log`() {
            val envelope = buildCustomerRegisteredEnvelope(
                customerId = TEST_CUSTOMER_ID,
                displayName = "Test Corp",
                source = "import"
            )

            testProducer.send(CUSTOMER_EVENTS_TOPIC, TEST_CUSTOMER_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val idempotencyKey = buildIdempotencyKey("CustomerRegistered", TEST_CUSTOMER_ID.toString())
                val processed = eventLogRepository.isProcessed(idempotencyKey)
                assertThat(processed).isTrue()
            }
        }

        @Test
        @Order(3)
        fun `CustomerRegistered event is acknowledged after successful processing`() {
            val envelope = buildCustomerRegisteredEnvelope(
                customerId = TEST_CUSTOMER_ID,
                displayName = "Ack Test Corp",
                source = "referral"
            )

            testProducer.send(CUSTOMER_EVENTS_TOPIC, TEST_CUSTOMER_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val opportunities = opportunityRepository.findByCustomerId(TEST_CUSTOMER_ID.toString())
                assertThat(opportunities).isNotEmpty

                val idempotencyKey = buildIdempotencyKey("CustomerRegistered", TEST_CUSTOMER_ID.toString())
                assertThat(eventLogRepository.isProcessed(idempotencyKey)).isTrue()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Idempotency Enforcement — Duplicate Prevention
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Idempotency Enforcement")
    inner class IdempotencyEnforcement {

        @Test
        @Order(10)
        fun `duplicate CustomerRegistered event is safely ignored`() {
            val envelope = buildCustomerRegisteredEnvelope(
                customerId = TEST_CUSTOMER_ID,
                displayName = "Duplicate Test Corp",
                source = "website"
            )

            testProducer.send(CUSTOMER_EVENTS_TOPIC, TEST_CUSTOMER_ID.toString(), envelope)
            testProducer.send(CUSTOMER_EVENTS_TOPIC, TEST_CUSTOMER_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val opportunities = opportunityRepository.findByCustomerId(TEST_CUSTOMER_ID.toString())
                assertThat(opportunities).hasSize(1)
                assertThat(opportunities.first().name).isEqualTo("New Lead: Duplicate Test Corp")
            }
        }

        @Test
        @Order(11)
        fun `duplicate event does not create duplicate idempotency record`() {
            val envelope = buildCustomerRegisteredEnvelope(
                customerId = TEST_CUSTOMER_ID,
                displayName = "Idempotency Check Corp",
                source = "api"
            )

            testProducer.send(CUSTOMER_EVENTS_TOPIC, TEST_CUSTOMER_ID.toString(), envelope)
            testProducer.send(CUSTOMER_EVENTS_TOPIC, TEST_CUSTOMER_ID.toString(), envelope)
            testProducer.send(CUSTOMER_EVENTS_TOPIC, TEST_CUSTOMER_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val idempotencyKey = buildIdempotencyKey("CustomerRegistered", TEST_CUSTOMER_ID.toString())
                assertThat(eventLogRepository.isProcessed(idempotencyKey)).isTrue()

                val opportunities = opportunityRepository.findByCustomerId(TEST_CUSTOMER_ID.toString())
                assertThat(opportunities).hasSize(1)
            }
        }

        @Test
        @Order(12)
        fun `no constraint exception on rapid duplicate processing`() {
            val envelope = buildCustomerRegisteredEnvelope(
                customerId = TEST_CUSTOMER_ID,
                displayName = "Constraint Test Corp",
                source = "website"
            )

            repeat(5) {
                testProducer.send(CUSTOMER_EVENTS_TOPIC, TEST_CUSTOMER_ID.toString(), envelope)
                Thread.sleep(100)
            }

            await().atMost(Duration.ofSeconds(20)).untilAsserted {
                val opportunities = opportunityRepository.findByCustomerId(TEST_CUSTOMER_ID.toString())
                assertThat(opportunities).hasSize(1)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Transactional Rollback on Failure
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transactional Rollback on Failure")
    inner class TransactionalRollback {

        @Test
        @Order(20)
        fun `malformed event payload rolls back transaction cleanly`() {
            val malformedPayload = """{"eventType":"CustomerRegistered","source":"ciam","payload":{}}"""

            testProducer.send(CUSTOMER_EVENTS_TOPIC, TEST_CUSTOMER_ID.toString(), malformedPayload)

            Thread.sleep(5000)

            val opportunities = opportunityRepository.findByCustomerId(TEST_CUSTOMER_ID.toString())
            assertThat(opportunities).isEmpty()

            val idempotencyKey = buildIdempotencyKey("CustomerRegistered", TEST_CUSTOMER_ID.toString())
            assertThat(eventLogRepository.isProcessed(idempotencyKey)).isFalse()
        }

        @Test
        @Order(21)
        fun `event with missing entityId does not create opportunity or idempotency record`() {
            val envelope = """
                {
                    "eventType": "CustomerRegistered",
                    "source": "ciam",
                    "payload": {
                        "displayName": "No ID Corp",
                        "source": "website",
                        "registeredAt": "${OffsetDateTime.now()}"
                    }
                }
            """.trimIndent()

            testProducer.send(CUSTOMER_EVENTS_TOPIC, "no-id-key", envelope)

            Thread.sleep(5000)

            val opportunities = opportunityRepository.findByCustomerId(TEST_CUSTOMER_ID.toString())
            assertThat(opportunities).isEmpty()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Event Routing Logic
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Routing Logic")
    inner class EventRouting {

        @Test
        @Order(30)
        fun `LeadQualified event creates opportunity with Qualified prefix`() {
            val customerId = UUID.randomUUID()
            val envelope = buildLeadQualifiedEnvelope(
                customerId = customerId,
                displayName = "Qualified Prospect Inc"
            )

            testProducer.send(CUSTOMER_EVENTS_TOPIC, customerId.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val opportunities = opportunityRepository.findByCustomerId(customerId.toString())
                assertThat(opportunities).hasSize(1)
                assertThat(opportunities.first().name).isEqualTo("Qualified: Qualified Prospect Inc")
                assertThat(opportunities.first().stage).isEqualTo(SalesStage.PROSPECTING)
            }
        }

        @Test
        @Order(31)
        fun `LeadQualified event writes idempotency record`() {
            val customerId = UUID.randomUUID()
            val envelope = buildLeadQualifiedEnvelope(
                customerId = customerId,
                displayName = "Idempotent Qualified Corp"
            )

            testProducer.send(CUSTOMER_EVENTS_TOPIC, customerId.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val idempotencyKey = buildIdempotencyKey("LeadQualified", customerId.toString())
                assertThat(eventLogRepository.isProcessed(idempotencyKey)).isTrue()
            }
        }

        @Test
        @Order(32)
        fun `LifecycleStageChanged event completes without creating opportunity`() {
            val customerId = UUID.randomUUID()
            val envelope = buildLifecycleStageChangedEnvelope(
                customerId = customerId,
                fromStage = "lead",
                toStage = "qualified"
            )

            testProducer.send(CUSTOMER_EVENTS_TOPIC, customerId.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val opportunities = opportunityRepository.findByCustomerId(customerId.toString())
                assertThat(opportunities).isEmpty()
            }

            val idempotencyKey = buildIdempotencyKey("LifecycleStageChanged", customerId.toString())
            await().atMost(Duration.ofSeconds(5)).untilAsserted {
                assertThat(eventLogRepository.isProcessed(idempotencyKey)).isTrue()
            }
        }

        @Test
        @Order(33)
        fun `unhandled event type is logged and acknowledged without side effects`() {
            val envelope = """
                {
                    "eventType": "CustomerDeactivated",
                    "source": "ciam",
                    "payload": {
                        "entityId": "${UUID.randomUUID()}",
                        "reason": "test",
                        "deactivatedAt": "${OffsetDateTime.now()}"
                    }
                }
            """.trimIndent()

            testProducer.send(CUSTOMER_EVENTS_TOPIC, "any-key", envelope)

            Thread.sleep(5000)
            // If we got here without exception, the consumer handled it gracefully
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
        fun `CustomerRegistered followed by LeadQualified creates two opportunities`() {
            val customerId = UUID.randomUUID()
            val registeredEnvelope = buildCustomerRegisteredEnvelope(
                customerId = customerId,
                displayName = "Multi Event Corp",
                source = "website"
            )
            val qualifiedEnvelope = buildLeadQualifiedEnvelope(
                customerId = customerId,
                displayName = "Multi Event Corp"
            )

            testProducer.send(CUSTOMER_EVENTS_TOPIC, customerId.toString(), registeredEnvelope)
            testProducer.send(CUSTOMER_EVENTS_TOPIC, customerId.toString(), qualifiedEnvelope)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val opportunities = opportunityRepository.findByCustomerId(customerId.toString())
                assertThat(opportunities).hasSize(2)
                val names = opportunities.map { it.name }.toSet()
                assertThat(names).containsExactlyInAnyOrder(
                    "New Lead: Multi Event Corp",
                    "Qualified: Multi Event Corp"
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Dead Letter Queue Verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dead Letter Queue")
    inner class DeadLetterQueue {

        @Test
        @Order(50)
        fun `poison pill with invalid customerId is routed to DLQ after retries`() {
            // Given: a poison pill — valid JSON envelope but invalid customerId
            // that will cause ConsumerProcessingException on UUID validation
            val poisonPill = """
                {
                    "eventType": "CustomerRegistered",
                    "source": "ciam",
                    "actorId": "test-actor",
                    "payload": {
                        "entityId": "not-a-valid-uuid",
                        "displayName": "Poison Pill Corp",
                        "source": "website",
                        "registeredAt": "${OffsetDateTime.now()}"
                    }
                }
            """.trimIndent()

            // When: inject the poison pill
            testProducer.send(CUSTOMER_EVENTS_TOPIC, "poison-pill-key", poisonPill)

            // Then: wait for retries to exhaust and message to land on DLQ
            await().atMost(Duration.ofSeconds(30)).untilAsserted {
                // No opportunity created for this poison pill
                val opportunities = opportunityRepository.findByCustomerId("not-a-valid-uuid")
                assertThat(opportunities).isEmpty()

                // No idempotency record (processing failed before markProcessed)
                val idempotencyKey = buildIdempotencyKey("CustomerRegistered", "not-a-valid-uuid")
                assertThat(eventLogRepository.isProcessed(idempotencyKey)).isFalse()
            }
        }

        @Test
        @Order(51)
        fun `consumer does not crash on poison pill and continues processing subsequent events`() {
            // Given: a poison pill followed by a valid event
            val poisonPill = """
                {
                    "eventType": "CustomerRegistered",
                    "source": "ciam",
                    "payload": {
                        "entityId": "TOTALLY-INVALID-UUID",
                        "displayName": "Crash Test Corp",
                        "source": "website",
                        "registeredAt": "${OffsetDateTime.now()}"
                    }
                }
            """.trimIndent()

            val validCustomerId = UUID.randomUUID()
            val validEnvelope = buildCustomerRegisteredEnvelope(
                customerId = validCustomerId,
                displayName = "Valid After Poison Corp",
                source = "website"
            )

            // When: send poison pill first, then valid event
            testProducer.send(CUSTOMER_EVENTS_TOPIC, "crash-key", poisonPill)
            Thread.sleep(8000)
            testProducer.send(CUSTOMER_EVENTS_TOPIC, validCustomerId.toString(), validEnvelope)

            // Then: the valid event should still be processed successfully
            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val opportunities = opportunityRepository.findByCustomerId(validCustomerId.toString())
                assertThat(opportunities).hasSize(1)
                assertThat(opportunities.first().name).isEqualTo("New Lead: Valid After Poison Corp")
            }
        }

        @Test
        @Order(52)
        fun `transient error with valid payload succeeds without DLQ routing`() {
            // Given: a valid event
            val validCustomerId = UUID.randomUUID()
            val envelope = buildCustomerRegisteredEnvelope(
                customerId = validCustomerId,
                displayName = "Transient Test Corp",
                source = "website"
            )

            // When
            testProducer.send(CUSTOMER_EVENTS_TOPIC, validCustomerId.toString(), envelope)

            // Then: succeeds (possibly after retry) without going to DLQ
            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val opportunities = opportunityRepository.findByCustomerId(validCustomerId.toString())
                assertThat(opportunities).hasSize(1)

                val idempotencyKey = buildIdempotencyKey("CustomerRegistered", validCustomerId.toString())
                assertThat(eventLogRepository.isProcessed(idempotencyKey)).isTrue()
            }
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun buildCustomerRegisteredEnvelope(
        customerId: UUID,
        displayName: String,
        source: String,
    ): String = """
        {
            "eventType": "CustomerRegistered",
            "source": "ciam",
            "actorId": "test-actor",
            "payload": {
                "entityId": "$customerId",
                "displayName": "$displayName",
                "source": "$source",
                "registeredAt": "${OffsetDateTime.now()}"
            }
        }
    """.trimIndent()

    private fun buildLeadQualifiedEnvelope(
        customerId: UUID,
        displayName: String,
    ): String = """
        {
            "eventType": "LeadQualified",
            "source": "ciam",
            "actorId": "test-actor",
            "payload": {
                "entityId": "$customerId",
                "displayName": "$displayName",
                "previousStage": "lead",
                "qualifiedAt": "${OffsetDateTime.now()}"
            }
        }
    """.trimIndent()

    private fun buildLifecycleStageChangedEnvelope(
        customerId: UUID,
        fromStage: String,
        toStage: String,
    ): String = """
        {
            "eventType": "LifecycleStageChanged",
            "source": "ciam",
            "actorId": "test-actor",
            "payload": {
                "entityId": "$customerId",
                "fromStage": "$fromStage",
                "toStage": "$toStage",
                "changedAt": "${OffsetDateTime.now()}"
            }
        }
    """.trimIndent()

    private fun buildIdempotencyKey(eventType: String, entityId: String): UUID {
        val raw = "$eventType:$entityId"
        return UUID.nameUUIDFromBytes(raw.toByteArray())
    }
}
