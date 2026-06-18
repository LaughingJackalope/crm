package com.crm.communication.infrastructure.messaging

import com.crm.communication.domain.notification.NotificationStatus
import com.crm.communication.infrastructure.persistence.IncomingEventLogEntity
import com.crm.communication.infrastructure.persistence.IncomingEventLogRepository
import com.crm.communication.infrastructure.persistence.NotificationEntity
import com.crm.communication.infrastructure.persistence.NotificationRepository
import com.crm.communication.infrastructure.persistence.OutboxEventEntity
import com.crm.communication.infrastructure.persistence.OutboxEventRepository

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

@QuarkusTest
@QuarkusTestResource(CommunicationIntegrationTestResource::class)
@Tag(TestTags.INTEGRATION)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnifiedEventConsumerIntegrationTest {

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var eventLogRepository: IncomingEventLogRepository

    @Inject
    lateinit var outboxEventRepository: OutboxEventRepository

    @Inject
    lateinit var testProducer: EventTestProducer

    companion object {
        const val CIAM_TOPIC = "crm.ciam.lifecycle-stage.changed"
        const val BILLING_TOPIC = "crm.billing.events"
        const val SUPPORT_TOPIC = "crm.support.ticket.events"
        const val MARKETING_TOPIC = "crm.marketing.events"
        const val SALES_TOPIC = "crm.sales.opportunity.closed"
        val TEST_CONTACT_ID: UUID = UUID.fromString("c1d2e3f4-a5b6-7890-bcde-f12345678901")
        val TEST_INVOICE_ID: UUID = UUID.fromString("d2e3f4a5-b6c7-8901-cdef-123456789012")
        val TEST_TICKET_ID: UUID = UUID.fromString("e3f4a5b6-c7d8-9012-def1-234567890123")
    }

    @BeforeEach
    @Transactional
    fun cleanUp() {
        IncomingEventLogEntity.delete("entityId", TEST_CONTACT_ID.toString())
        IncomingEventLogEntity.delete("entityId", TEST_INVOICE_ID.toString())
        IncomingEventLogEntity.delete("entityId", TEST_TICKET_ID.toString())
        NotificationEntity.delete("recipientId", TEST_CONTACT_ID.toString())
        NotificationEntity.list("recipientId", "support-team").forEach {
            NotificationEntity.delete("notificationId", (it as NotificationEntity).notificationId)
        }
        OutboxEventEntity.list("entityId", TEST_CONTACT_ID.toString()).forEach {
            OutboxEventEntity.delete("eventId", (it as OutboxEventEntity).eventId)
        }
    }

    @AfterAll
    fun tearDown() { testProducer.close() }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-Context Router Integration
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cross-Context Router — multi-topic ingestion")
    inner class CrossContextTests {

        @Test
        @Order(1)
        fun `InvoiceGenerated event from Billing creates notification`() {
            val envelope = """
                {"eventType": "InvoiceGenerated", "source": "billing", "actorId": "system",
                 "payload": {"entityId": "$TEST_CONTACT_ID", "invoiceId": "$TEST_INVOICE_ID",
                             "totalAmount": "1500.00 USD", "currency": "USD",
                             "dueDate": "2026-07-14", "generatedAt": "${OffsetDateTime.now()}"}}
            """.trimIndent()

            testProducer.send(BILLING_TOPIC, TEST_INVOICE_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val notifications = notificationRepository.findByRecipientId(TEST_CONTACT_ID.toString())
                assertThat(notifications).isNotEmpty
                assertThat(notifications.first().templateId).isEqualTo("invoice_generated")
                assertThat(notifications.first().status).isEqualTo(NotificationStatus.PENDING)
            }
        }

        @Test
        @Order(2)
        fun `TicketAssigned event from Support creates notification`() {
            val envelope = """
                {"eventType": "TicketAssigned", "source": "support", "actorId": "agent-001",
                 "payload": {"ticketId": "$TEST_TICKET_ID", "assigneeId": "support-team",
                             "queueId": "enterprise-queue"}}
            """.trimIndent()

            testProducer.send(SUPPORT_TOPIC, TEST_TICKET_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val notifications = notificationRepository.findByRecipientId("support-team")
                assertThat(notifications).isNotEmpty
                assertThat(notifications.first().templateId).isEqualTo("ticket_assigned")
            }
        }

        @Test
        @Order(3)
        fun `CustomerRegistered event from CIAM creates welcome notification`() {
            val envelope = """
                {"eventType": "CustomerRegistered", "source": "ciam", "actorId": "system",
                 "payload": {"contactId": "$TEST_CONTACT_ID", "displayName": "Jane Doe",
                             "email": "jane@example.com", "source": "website",
                             "registeredAt": "${OffsetDateTime.now()}"}}
            """.trimIndent()

            testProducer.send(CIAM_TOPIC, TEST_CONTACT_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val notifications = notificationRepository.findByRecipientId(TEST_CONTACT_ID.toString())
                assertThat(notifications).isNotEmpty
                assertThat(notifications.first().templateId).isEqualTo("welcome")
            }
        }

        @Test
        @Order(4)
        fun `outbox events written for cross-context notifications`() {
            // Clean slate for this test
            val contactId = UUID.randomUUID()
            val invoiceId = UUID.randomUUID()

            val envelope = """
                {"eventType": "InvoiceGenerated", "source": "billing",
                 "payload": {"entityId": "$contactId", "invoiceId": "$invoiceId",
                             "totalAmount": "5000.00 USD", "currency": "USD",
                             "dueDate": "2026-07-14", "generatedAt": "${OffsetDateTime.now()}"}}
            """.trimIndent()

            testProducer.send(BILLING_TOPIC, invoiceId.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val outboxEvents = outboxEventRepository.findPending(100)
                val queuedEvent = outboxEvents.find {
                    it.eventType == "NotificationQueued" && it.source == "communication"
                }
                assertThat(queuedEvent).isNotNull
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Strict Anti-Spam / Idempotency
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Anti-Spam Idempotency — duplicate prevention")
    inner class IdempotencyTests {

        @Test
        @Order(10)
        fun `duplicate InvoiceGenerated event creates only one notification`() {
            val contactId = UUID.randomUUID()
            val invoiceId = UUID.randomUUID()
            val envelope = """
                {"eventType": "InvoiceGenerated", "source": "billing",
                 "payload": {"entityId": "$contactId", "invoiceId": "$invoiceId",
                             "totalAmount": "2500.00 USD", "currency": "USD",
                             "dueDate": "2026-07-14", "generatedAt": "${OffsetDateTime.now()}"}}
            """.trimIndent()

            testProducer.send(BILLING_TOPIC, invoiceId.toString(), envelope)
            testProducer.send(BILLING_TOPIC, invoiceId.toString(), envelope)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val notifications = notificationRepository.findByRecipientId(contactId.toString())
                assertThat(notifications).hasSize(1)
            }

            // Only one idempotency record
            val logCount = IncomingEventLogEntity.count("entityId", invoiceId.toString())
            assertThat(logCount).isEqualTo(1L)
        }

        @Test
        @Order(11)
        fun `duplicate CustomerRegistered event is safely ignored`() {
            val contactId = UUID.randomUUID()
            val envelope = """
                {"eventType": "CustomerRegistered", "source": "ciam",
                 "payload": {"contactId": "$contactId", "displayName": "John Smith",
                             "email": "john@example.com", "source": "api",
                             "registeredAt": "${OffsetDateTime.now()}"}}
            """.trimIndent()

            testProducer.send(CIAM_TOPIC, contactId.toString(), envelope)
            testProducer.send(CIAM_TOPIC, contactId.toString(), envelope)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val notifications = notificationRepository.findByRecipientId(contactId.toString())
                assertThat(notifications).hasSize(1)
                assertThat(notifications.first().templateId).isEqualTo("welcome")
            }
        }

        @Test
        @Order(12)
        fun `different events for same contact create separate notifications`() {
            val contactId = UUID.randomUUID()

            // First: CustomerRegistered
            testProducer.send(CIAM_TOPIC, contactId.toString(), """
                {"eventType": "CustomerRegistered", "source": "ciam",
                 "payload": {"contactId": "$contactId", "displayName": "Alice",
                             "email": "alice@example.com", "source": "website",
                             "registeredAt": "${OffsetDateTime.now()}"}}
            """.trimIndent())

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                assertThat(notificationRepository.findByRecipientId(contactId.toString())).hasSize(1)
            }

            // Second: LifecycleStageChanged (different event type = different idempotency key)
            testProducer.send(CIAM_TOPIC, contactId.toString(), """
                {"eventType": "LifecycleStageChanged", "source": "ciam",
                 "payload": {"contactId": "$contactId", "fromStage": "lead",
                             "toStage": "customer", "changedAt": "${OffsetDateTime.now()}"}}
            """.trimIndent())

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val notifications = notificationRepository.findByRecipientId(contactId.toString())
                assertThat(notifications).hasSize(2)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Resiliency & DLQ
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Resiliency & DLQ — error handling")
    inner class ResiliencyTests {

        @Test
        @Order(20)
        fun `malformed JSON payload does not crash consumer`() {
            val poisonPill = """{"eventType": "CustomerRegistered", "source": "ciam", "payload": }"""

            testProducer.send(CIAM_TOPIC, "bad-json-key", poisonPill)

            Thread.sleep(8000)

            // Consumer survives — verify with valid event
            val contactId = UUID.randomUUID()
            testProducer.send(CIAM_TOPIC, contactId.toString(), """
                {"eventType": "CustomerRegistered", "source": "ciam",
                 "payload": {"contactId": "$contactId", "displayName": "Valid User",
                             "email": "valid@example.com", "source": "website",
                             "registeredAt": "${OffsetDateTime.now()}"}}
            """.trimIndent())

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val notifications = notificationRepository.findByRecipientId(contactId.toString())
                assertThat(notifications).isNotEmpty
            }
        }

        @Test
        @Order(21)
        fun `missing contactId in payload does not crash consumer`() {
            val envelope = """
                {"eventType": "CustomerRegistered", "source": "ciam",
                 "payload": {"displayName": "No ID User", "source": "website"}}
            """.trimIndent()

            testProducer.send(CIAM_TOPIC, "missing-id", envelope)

            Thread.sleep(5000)

            // Consumer survives — idempotency record written
            val logCount = IncomingEventLogEntity.count("entityId", "missing-id")
            assertThat(logCount).isEqualTo(1L)
        }

        @Test
        @Order(22)
        fun `consumer processes valid events after handling poison pills`() {
            // Send poison pills on different topics
            testProducer.send(CIAM_TOPIC, "poison-1", """{"eventType": "CustomerRegistered", "source": "ciam", "payload": }""")
            testProducer.send(BILLING_TOPIC, "poison-2", """{"eventType": "InvoiceGenerated", "source": "billing", "payload": }""")
            Thread.sleep(8000)

            // Then send valid events
            val contactId = UUID.randomUUID()
            testProducer.send(CIAM_TOPIC, contactId.toString(), """
                {"eventType": "CustomerRegistered", "source": "ciam",
                 "payload": {"contactId": "$contactId", "displayName": "Recovery Test",
                             "email": "recovery@example.com", "source": "website",
                             "registeredAt": "${OffsetDateTime.now()}"}}
            """.trimIndent())

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val notifications = notificationRepository.findByRecipientId(contactId.toString())
                assertThat(notifications).isNotEmpty
            }
        }

        @Test
        @Order(23)
        fun `unhandled event type is logged and acknowledged without side effects`() {
            val envelope = """
                {"eventType": "SomeUnknownEvent", "source": "ciam",
                 "payload": {"contactId": "$TEST_CONTACT_ID"}}
            """.trimIndent()

            testProducer.send(CIAM_TOPIC, TEST_CONTACT_ID.toString(), envelope)

            Thread.sleep(5000)

            // No crash, idempotency record written
            val logCount = IncomingEventLogEntity.count("entityId", TEST_CONTACT_ID.toString())
            assertThat(logCount).isEqualTo(1L)
        }
    }
}
