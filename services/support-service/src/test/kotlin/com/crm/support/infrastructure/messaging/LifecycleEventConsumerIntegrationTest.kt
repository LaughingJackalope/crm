package com.crm.support.infrastructure.messaging

import com.crm.openapi.support.model.CreateTicketRequest
import com.crm.support.domain.ticket.CustomerTier
import com.crm.support.domain.ticket.CustomerTierProjection
import com.crm.support.infrastructure.persistence.IncomingEventLogEntity
import com.crm.support.infrastructure.persistence.IncomingEventLogRepository
import com.crm.support.infrastructure.persistence.OutboxEventEntity
import com.crm.support.infrastructure.persistence.OutboxEventRepository
import com.crm.support.infrastructure.persistence.TicketEntity
import com.crm.support.infrastructure.persistence.TicketRepository

import com.crm.test.CrmIntegrationTestResourceLifecycleManager
import com.crm.test.EventTestProducer
import com.crm.test.TestTags

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.http.ContentType
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
@QuarkusTestResource(SupportIntegrationTestResource::class)
@Tag(TestTags.INTEGRATION)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LifecycleEventConsumerIntegrationTest {

    @Inject
    lateinit var ticketRepository: TicketRepository

    @Inject
    lateinit var eventLogRepository: IncomingEventLogRepository

    @Inject
    lateinit var outboxEventRepository: OutboxEventRepository

    @Inject
    lateinit var testProducer: EventTestProducer

    companion object {
        const val CIAM_LIFECYCLE_TOPIC = "crm.ciam.lifecycle-stage.changed"
        val TEST_CONTACT_ID: UUID = UUID.fromString("d1e2f3a4-b5c6-7890-def1-234567890123")
        val TEST_CUSTOMER_ID: UUID = UUID.fromString("e2f3a4b5-c6d7-8901-ef12-345678901234")
    }

    @BeforeEach
    @Transactional
    fun cleanUp() {
        IncomingEventLogEntity.delete("entityId", TEST_CONTACT_ID.toString())
        CustomerTierProjection.delete("contactId", TEST_CONTACT_ID.toString())
        TicketEntity.delete("requesterId", TEST_CONTACT_ID.toString())
        OutboxEventEntity.list("entityId", TEST_CONTACT_ID.toString()).forEach {
            OutboxEventEntity.delete("eventId", (it as OutboxEventEntity).eventId)
        }
    }

    @AfterAll
    fun tearDown() { testProducer.close() }

    @Nested
    @DisplayName("Local Projection — Customer Tier")
    inner class ProjectionTests {

        @Test
        @Order(1)
        fun `LifecycleStageChanged updates customer tier projection`() {
            val envelope = buildLifecycleStageChangedEnvelope(
                TEST_CONTACT_ID, TEST_CUSTOMER_ID, "lead", "customer"
            )
            testProducer.send(CIAM_LIFECYCLE_TOPIC, TEST_CONTACT_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val projection = CustomerTierProjection.findByContactId(TEST_CONTACT_ID.toString())
                assertThat(projection).isNotNull
                assertThat(projection!!.tier).isEqualTo(CustomerTier.ENTERPRISE)
                assertThat(projection.lifecycleStage).isEqualTo("customer")
            }
        }

        @Test
        @Order(2)
        fun `projection maps opportunity stage to PREMIUM tier`() {
            val contactId = UUID.randomUUID()
            val envelope = buildLifecycleStageChangedEnvelope(contactId, UUID.randomUUID(), "lead", "opportunity")
            testProducer.send(CIAM_LIFECYCLE_TOPIC, contactId.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val projection = CustomerTierProjection.findByContactId(contactId.toString())
                assertThat(projection).isNotNull
                assertThat(projection!!.tier).isEqualTo(CustomerTier.PREMIUM)
            }
        }

        @Test
        @Order(3)
        fun `projection maps churned stage to STANDARD tier`() {
            val contactId = UUID.randomUUID()
            val envelope = buildLifecycleStageChangedEnvelope(contactId, UUID.randomUUID(), "customer", "churned")
            testProducer.send(CIAM_LIFECYCLE_TOPIC, contactId.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val projection = CustomerTierProjection.findByContactId(contactId.toString())
                assertThat(projection).isNotNull
                assertThat(projection!!.tier).isEqualTo(CustomerTier.STANDARD)
            }
        }

        @Test
        @Order(4)
        fun `duplicate lifecycle event is safely ignored`() {
            val envelope = buildLifecycleStageChangedEnvelope(TEST_CONTACT_ID, TEST_CUSTOMER_ID, "lead", "opportunity")
            testProducer.send(CIAM_LIFECYCLE_TOPIC, TEST_CONTACT_ID.toString(), envelope)
            testProducer.send(CIAM_LIFECYCLE_TOPIC, TEST_CONTACT_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val projection = CustomerTierProjection.findByContactId(TEST_CONTACT_ID.toString())
                assertThat(projection).isNotNull
                assertThat(projection!!.tier).isEqualTo(CustomerTier.PREMIUM)
            }

            val logCount = IncomingEventLogEntity.count("entityId", TEST_CONTACT_ID.toString())
            assertThat(logCount).isEqualTo(1L)
        }

        @Test
        @Order(5)
        fun `ticket created after projection uses correct tier for SLA`() {
            val contactId = UUID.randomUUID()
            val lifecycleEnvelope = buildLifecycleStageChangedEnvelope(contactId, UUID.randomUUID(), "lead", "customer")
            testProducer.send(CIAM_LIFECYCLE_TOPIC, contactId.toString(), lifecycleEnvelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                assertThat(CustomerTierProjection.findByContactId(contactId.toString())).isNotNull
            }

            val request = CreateTicketRequest(
                requesterId = contactId,
                subject = "Enterprise SLA Test",
                description = "Testing enterprise SLA",
                priority = com.crm.openapi.support.model.TicketPriority.CRITICAL,
                queueId = null,
            )

            val response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/api/v1/tickets")
                .then()
                .statusCode(201)
                .extract()
                .response()

            val ticketId = UUID.fromString(response.jsonPath().getString("ticketId"))

            await().atMost(Duration.ofSeconds(5)).untilAsserted {
                val ticket = ticketRepository.findById(ticketId)
                assertThat(ticket).isNotNull
                assertThat(ticket!!.slaDeadline).isNotNull
                val expectedDeadline = ticket.createdAt.plus(Duration.ofHours(1))
                assertThat(ticket.slaDeadline).isEqualTo(expectedDeadline)
            }
        }
    }

    @Nested
    @DisplayName("Transactional Outbox — Ticket Creation")
    inner class OutboxTests {

        @Test
        @Order(10)
        fun `ticket creation persists ticket and writes to outbox atomically`() {
            val contactId = UUID.randomUUID()
            val lifecycleEnvelope = buildLifecycleStageChangedEnvelope(contactId, UUID.randomUUID(), "lead", "customer")
            testProducer.send(CIAM_LIFECYCLE_TOPIC, contactId.toString(), lifecycleEnvelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                assertThat(CustomerTierProjection.findByContactId(contactId.toString())).isNotNull
            }

            val request = CreateTicketRequest(
                requesterId = contactId,
                subject = "Outbox Test",
                description = "Testing outbox",
                priority = com.crm.openapi.support.model.TicketPriority.HIGH,
                queueId = null,
            )

            val response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/api/v1/tickets")
                .then()
                .statusCode(201)
                .extract()
                .response()

            val ticketId = UUID.fromString(response.jsonPath().getString("ticketId"))

            await().atMost(Duration.ofSeconds(5)).untilAsserted {
                val ticket = ticketRepository.findById(ticketId)
                assertThat(ticket).isNotNull
                assertThat(ticket!!.slaDeadline).isNotNull
            }

            await().atMost(Duration.ofSeconds(5)).untilAsserted {
                val outboxEvents = outboxEventRepository.findPending(100)
                val ticketCreatedEvent = outboxEvents.find {
                    it.eventType == "TicketCreated" && it.source == "support"
                }
                assertThat(ticketCreatedEvent).isNotNull
            }
        }

        @Test
        @Order(11)
        fun `enterprise urgent ticket has 1h SLA deadline`() {
            val contactId = UUID.randomUUID()
            val lifecycleEnvelope = buildLifecycleStageChangedEnvelope(contactId, UUID.randomUUID(), "lead", "customer")
            testProducer.send(CIAM_LIFECYCLE_TOPIC, contactId.toString(), lifecycleEnvelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                assertThat(CustomerTierProjection.findByContactId(contactId.toString())).isNotNull
            }

            val request = CreateTicketRequest(
                requesterId = contactId,
                subject = "SLA Verification",
                description = "Verify SLA",
                priority = com.crm.openapi.support.model.TicketPriority.CRITICAL,
                queueId = null,
            )

            val response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/api/v1/tickets")
                .then()
                .statusCode(201)
                .extract()
                .response()

            val ticketId = UUID.fromString(response.jsonPath().getString("ticketId"))
            val ticket = ticketRepository.findById(ticketId)

            assertThat(ticket).isNotNull
            val expectedDeadline = ticket!!.createdAt.plus(Duration.ofHours(1))
            assertThat(ticket.slaDeadline).isEqualTo(expectedDeadline)
        }
    }

    @Nested
    @DisplayName("Error Handling & DLQ")
    inner class ErrorHandlingTests {

        @Test
        @Order(20)
        fun `malformed payload does not crash consumer`() {
            val poisonPill = """{"eventType": "LifecycleStageChanged", "source": "ciam", "payload": }"""
            testProducer.send(CIAM_LIFECYCLE_TOPIC, "bad-json-key", poisonPill)

            Thread.sleep(8000)

            val validContactId = UUID.randomUUID()
            val validEnvelope = buildLifecycleStageChangedEnvelope(validContactId, UUID.randomUUID(), "lead", "customer")
            testProducer.send(CIAM_LIFECYCLE_TOPIC, validContactId.toString(), validEnvelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val projection = CustomerTierProjection.findByContactId(validContactId.toString())
                assertThat(projection).isNotNull
            }
        }

        @Test
        @Order(21)
        fun `consumer survives poison pill and processes subsequent valid events`() {
            val poisonPill = """
                {"eventType": "LifecycleStageChanged", "source": "ciam",
                 "payload": {"contactId": null, "toStage": "customer"}}
            """.trimIndent()

            val validContactId = UUID.randomUUID()
            val validEnvelope = buildLifecycleStageChangedEnvelope(validContactId, UUID.randomUUID(), "lead", "opportunity")

            testProducer.send(CIAM_LIFECYCLE_TOPIC, "null-contact", poisonPill)
            Thread.sleep(8000)
            testProducer.send(CIAM_LIFECYCLE_TOPIC, validContactId.toString(), validEnvelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val projection = CustomerTierProjection.findByContactId(validContactId.toString())
                assertThat(projection).isNotNull
                assertThat(projection!!.tier).isEqualTo(CustomerTier.PREMIUM)
            }
        }

        @Test
        @Order(22)
        fun `missing contactId in payload does not create projection`() {
            val envelope = """
                {"eventType": "LifecycleStageChanged", "source": "ciam",
                 "payload": {"toStage": "customer", "changedAt": "${OffsetDateTime.now()}"}}
            """.trimIndent()

            testProducer.send(CIAM_LIFECYCLE_TOPIC, "missing-contact", envelope)
            Thread.sleep(8000)

            val projections = CustomerTierProjection.list("contactId", "missing-contact")
            assertThat(projections).isEmpty()
        }
    }

    private fun buildLifecycleStageChangedEnvelope(
        contactId: UUID, customerId: UUID, fromStage: String, toStage: String,
    ): String = """
        {"eventType": "LifecycleStageChanged", "source": "ciam", "actorId": "test-actor",
         "payload": {"contactId": "$contactId", "customerId": "$customerId",
                     "fromStage": "$fromStage", "toStage": "$toStage",
                     "changedAt": "${OffsetDateTime.now()}"}}
    """.trimIndent()
}
