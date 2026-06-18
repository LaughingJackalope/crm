package com.crm.marketing.infrastructure.messaging

import com.crm.marketing.domain.campaign.AdNetworkSource
import com.crm.marketing.domain.campaign.Campaign
import com.crm.marketing.domain.campaign.CampaignStatus
import com.crm.marketing.infrastructure.persistence.CampaignEntity
import com.crm.marketing.infrastructure.persistence.CampaignRepository
import com.crm.marketing.infrastructure.persistence.IncomingEventLogEntity
import com.crm.marketing.infrastructure.persistence.IncomingEventLogRepository
import com.crm.marketing.infrastructure.persistence.OutboxEventEntity
import com.crm.marketing.infrastructure.persistence.OutboxEventRepository
import com.crm.marketing.infrastructure.persistence.OutboxStatus

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

@QuarkusTest
@QuarkusTestResource(MarketingIntegrationTestResource::class)
@Tag(TestTags.INTEGRATION)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalesEventConsumerIntegrationTest {

    @Inject
    lateinit var campaignRepository: CampaignRepository

    @Inject
    lateinit var eventLogRepository: IncomingEventLogRepository

    @Inject
    lateinit var outboxEventRepository: OutboxEventRepository

    @Inject
    lateinit var testProducer: EventTestProducer

    companion object {
        const val SALES_OPPORTUNITY_TOPIC = "crm.sales.opportunity.closed"
        val TEST_CAMPAIGN_ID: UUID = UUID.fromString("f1a2b3c4-d5e6-7890-fabc-de1234567890")
        val TEST_OPPORTUNITY_ID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    }

    @BeforeEach
    @Transactional
    fun cleanUp() {
        IncomingEventLogEntity.delete("entityId", TEST_OPPORTUNITY_ID.toString())
        CampaignEntity.delete("campaignId", TEST_CAMPAIGN_ID)
        OutboxEventEntity.list("entityId", TEST_OPPORTUNITY_ID.toString()).forEach {
            OutboxEventEntity.delete("eventId", (it as OutboxEventEntity).eventId)
        }
    }

    @AfterAll
    fun tearDown() { testProducer.close() }

    private fun createActiveCampaign(): Campaign {
        val campaign = Campaign(
            campaignId = TEST_CAMPAIGN_ID, name = "E2E Test Campaign",
            source = AdNetworkSource.GOOGLE, status = CampaignStatus.ACTIVE,
            targetSegment = "enterprise",
        )
        return campaignRepository.save(campaign)
    }

    private fun buildOpportunityWonEnvelope(
        opportunityId: UUID, campaignId: UUID, amount: String,
    ): String = """
        {"eventType": "OpportunityClosed", "source": "sales", "actorId": "test-actor",
         "payload": {"opportunityId": "$opportunityId", "outcome": "won",
                     "utmCampaignId": "$campaignId", "totalAmount": "$amount",
                     "closedAt": "${OffsetDateTime.now()}"}}
    """.trimIndent()

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-Context E2E Attribution
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cross-Context E2E Attribution")
    inner class E2eAttributionTests {

        @Test
        @Order(1)
        fun `OpportunityWon event attributes revenue to campaign`() {
            createActiveCampaign()
            val envelope = buildOpportunityWonEnvelope(TEST_OPPORTUNITY_ID, TEST_CAMPAIGN_ID, "15000.00 USD")

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val campaign = campaignRepository.findById(TEST_CAMPAIGN_ID)
                assertThat(campaign).isNotNull
                assertThat(campaign!!.metrics.attributedRevenue).isEqualTo(BigDecimal("15000.00"))
                assertTrue(campaign.attributedOpportunityIds.contains(TEST_OPPORTUNITY_ID.toString()))
            }
        }

        @Test
        @Order(2)
        fun `idempotency guard prevents duplicate attribution`() {
            createActiveCampaign()
            val envelope = buildOpportunityWonEnvelope(TEST_OPPORTUNITY_ID, TEST_CAMPAIGN_ID, "5000.00")

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)
            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val campaign = campaignRepository.findById(TEST_CAMPAIGN_ID)
                assertThat(campaign!!.metrics.attributedRevenue).isEqualTo(BigDecimal("5000.00"))
            }

            val logCount = IncomingEventLogEntity.count("entityId", TEST_OPPORTUNITY_ID.toString())
            assertThat(logCount).isEqualTo(1L)
        }

        @Test
        @Order(3)
        fun `outbox events written after attribution`() {
            createActiveCampaign()
            val envelope = buildOpportunityWonEnvelope(TEST_OPPORTUNITY_ID, TEST_CAMPAIGN_ID, "25000.00")

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val outboxEvents = outboxEventRepository.findPending(100)
                val revenueEvent = outboxEvents.find {
                    it.eventType == "RevenueAttributedToCampaign" && it.source == "marketing"
                }
                assertThat(revenueEvent).isNotNull
            }
        }

        @Test
        @Order(4)
        fun `multiple opportunities attributed to same campaign sum correctly`() {
            createActiveCampaign()
            val opp1 = UUID.randomUUID()
            val opp2 = UUID.randomUUID()

            testProducer.send(SALES_OPPORTUNITY_TOPIC, opp1.toString(),
                buildOpportunityWonEnvelope(opp1, TEST_CAMPAIGN_ID, "10000.00"))
            testProducer.send(SALES_OPPORTUNITY_TOPIC, opp2.toString(),
                buildOpportunityWonEnvelope(opp2, TEST_CAMPAIGN_ID, "25000.00"))

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val campaign = campaignRepository.findById(TEST_CAMPAIGN_ID)
                assertThat(campaign!!.metrics.attributedRevenue).isEqualTo(BigDecimal("35000.00"))
                assertThat(campaign.attributedOpportunityIds).hasSize(2)
            }
        }

        @Test
        @Order(5)
        fun `lost opportunity does not attribute revenue`() {
            createActiveCampaign()
            val envelope = """
                {"eventType": "OpportunityClosed", "source": "sales",
                 "payload": {"opportunityId": "$TEST_OPPORTUNITY_ID", "outcome": "lost",
                             "utmCampaignId": "$TEST_CAMPAIGN_ID",
                             "closedAt": "${OffsetDateTime.now()}"}}
            """.trimIndent()

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            Thread.sleep(5000)

            val campaign = campaignRepository.findById(TEST_CAMPAIGN_ID)
            assertThat(campaign!!.metrics.attributedRevenue).isEqualTo(BigDecimal("0.00"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Resiliency & DLQ
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Resiliency & DLQ")
    inner class ResiliencyTests {

        @Test
        @Order(10)
        fun `corrupted campaign ID does not crash consumer`() {
            val envelope = """
                {"eventType": "OpportunityClosed", "source": "sales",
                 "payload": {"opportunityId": "$TEST_OPPORTUNITY_ID", "outcome": "won",
                             "utmCampaignId": "not-a-valid-uuid",
                             "closedAt": "${OffsetDateTime.now()}"}}
            """.trimIndent()

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            Thread.sleep(8000)

            // Consumer should survive — verify by sending a valid event
            createActiveCampaign()
            val validOpp = UUID.randomUUID()
            testProducer.send(SALES_OPPORTUNITY_TOPIC, validOpp.toString(),
                buildOpportunityWonEnvelope(validOpp, TEST_CAMPAIGN_ID, "5000.00"))

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val campaign = campaignRepository.findById(TEST_CAMPAIGN_ID)
                assertThat(campaign!!.metrics.attributedRevenue).isEqualTo(BigDecimal("5000.00"))
            }
        }

        @Test
        @Order(11)
        fun `missing campaign ID in attribution tag is handled gracefully`() {
            val envelope = """
                {"eventType": "OpportunityClosed", "source": "sales",
                 "payload": {"opportunityId": "$TEST_OPPORTUNITY_ID", "outcome": "won",
                             "closedAt": "${OffsetDateTime.now()}"}}
            """.trimIndent()

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            Thread.sleep(5000)

            // No crash, no attribution — just logged and skipped
            val logCount = IncomingEventLogEntity.count("entityId", TEST_OPPORTUNITY_ID.toString())
            assertThat(logCount).isEqualTo(1L)
        }

        @Test
        @Order(12)
        fun `non-existent campaign ID does not crash consumer`() {
            val nonExistentCampaignId = UUID.randomUUID()
            val envelope = buildOpportunityWonEnvelope(TEST_OPPORTUNITY_ID, nonExistentCampaignId, "10000.00")

            testProducer.send(SALES_OPPORTUNITY_TOPIC, TEST_OPPORTUNITY_ID.toString(), envelope)

            Thread.sleep(8000)

            // Consumer survives — idempotency record written
            val logCount = IncomingEventLogEntity.count("entityId", TEST_OPPORTUNITY_ID.toString())
            assertThat(logCount).isEqualTo(1L)
        }

        @Test
        @Order(13)
        fun `malformed JSON payload does not crash consumer`() {
            val poisonPill = """{"eventType": "OpportunityClosed", "source": "sales", "payload": }"""

            testProducer.send(SALES_OPPORTUNITY_TOPIC, "bad-json-key", poisonPill)

            Thread.sleep(8000)

            // Consumer survives — verify with valid event
            createActiveCampaign()
            val validOpp = UUID.randomUUID()
            testProducer.send(SALES_OPPORTUNITY_TOPIC, validOpp.toString(),
                buildOpportunityWonEnvelope(validOpp, TEST_CAMPAIGN_ID, "7500.00"))

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val campaign = campaignRepository.findById(TEST_CAMPAIGN_ID)
                assertThat(campaign!!.metrics.attributedRevenue).isEqualTo(BigDecimal("7500.00"))
            }
        }

        @Test
        @Order(14)
        fun `consumer processes valid events after handling poison pills`() {
            // Send poison pill first
            testProducer.send(SALES_OPPORTUNITY_TOPIC, "poison-1",
                """{"eventType": "OpportunityClosed", "source": "sales", "payload": }""")
            Thread.sleep(8000)

            // Then send valid events
            createActiveCampaign()
            val opp1 = UUID.randomUUID()
            val opp2 = UUID.randomUUID()

            testProducer.send(SALES_OPPORTUNITY_TOPIC, opp1.toString(),
                buildOpportunityWonEnvelope(opp1, TEST_CAMPAIGN_ID, "3000.00"))
            testProducer.send(SALES_OPPORTUNITY_TOPIC, opp2.toString(),
                buildOpportunityWonEnvelope(opp2, TEST_CAMPAIGN_ID, "7000.00"))

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val campaign = campaignRepository.findById(TEST_CAMPAIGN_ID)
                assertThat(campaign!!.metrics.attributedRevenue).isEqualTo(BigDecimal("10000.00"))
                assertThat(campaign.attributedOpportunityIds).hasSize(2)
            }
        }
    }
}
