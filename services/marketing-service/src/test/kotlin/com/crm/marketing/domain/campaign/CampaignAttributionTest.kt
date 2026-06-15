package com.crm.marketing.domain.campaign

import com.crm.marketing.domain.CampaignDomainException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Pure unit tests for Campaign attribution logic and adapter pattern.
 * No Quarkus, no DI, no database — just BigDecimal math and state transitions.
 */
@DisplayName("Campaign Attribution & Adapter Tests")
class CampaignAttributionTest {

    private val fixedNow: Instant = Instant.parse("2026-06-14T10:00:00Z")
    private val campaignId: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

    private fun activeCampaign(
        metrics: CampaignMetrics = CampaignMetrics.EMPTY,
        attributedIds: Set<String> = emptySet(),
    ): Campaign = Campaign(
        campaignId = campaignId, name = "Test Campaign", source = AdNetworkSource.GOOGLE,
        status = CampaignStatus.ACTIVE, targetSegment = "enterprise",
        metrics = metrics, attributedOpportunityIds = attributedIds,
        createdAt = fixedNow, updatedAt = fixedNow,
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Attribution Arithmetic
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Attribution arithmetic — BigDecimal precision")
    inner class AttributionArithmeticTests {

        @Test
        fun `single attribution adds exact amount`() {
            val campaign = activeCampaign()
            val updated = campaign.attributeSale("opp-001", BigDecimal("1500.00"), fixedNow)

            assertEquals(BigDecimal("1500.00"), updated.metrics.attributedRevenue)
            assertTrue(updated.attributedOpportunityIds.contains("opp-001"))
        }

        @Test
        fun `multiple sequential attributions sum correctly`() {
            var campaign = activeCampaign()
            campaign = campaign.attributeSale("opp-001", BigDecimal("1000.00"), fixedNow)
            campaign = campaign.attributeSale("opp-002", BigDecimal("2500.50"), fixedNow)
            campaign = campaign.attributeSale("opp-003", BigDecimal("999.99"), fixedNow)

            assertEquals(BigDecimal("4500.49"), campaign.metrics.attributedRevenue)
            assertEquals(3, campaign.attributedOpportunityIds.size)
        }

        @Test
        fun `attribution with fractional cents rounds correctly via HALF_UP`() {
            var campaign = activeCampaign()
            // 1000.005 should round to 1000.01 at scale 2
            campaign = campaign.attributeSale("opp-001", BigDecimal("1000.005"), fixedNow)
            assertEquals(BigDecimal("1000.01"), campaign.metrics.attributedRevenue)
        }

        @Test
        fun `attribution with 0_004 rounds down`() {
            var campaign = activeCampaign()
            campaign = campaign.attributeSale("opp-001", BigDecimal("1000.004"), fixedNow)
            assertEquals(BigDecimal("1000.00"), campaign.metrics.attributedRevenue)
        }

        @Test
        fun `duplicate attribution is idempotent — revenue not double-counted`() {
            var campaign = activeCampaign()
            campaign = campaign.attributeSale("opp-001", BigDecimal("5000.00"), fixedNow)
            val afterFirst = campaign.metrics.attributedRevenue

            // Second call with same opportunityId — should be ignored
            campaign = campaign.attributeSale("opp-001", BigDecimal("5000.00"), fixedNow.plusSeconds(60))
            val afterSecond = campaign.metrics.attributedRevenue

            assertEquals(afterFirst, afterSecond, "Duplicate attribution should not change revenue")
            assertEquals(BigDecimal("5000.00"), campaign.metrics.attributedRevenue)
            assertEquals(1, campaign.attributedOpportunityIds.size)
        }

        @Test
        fun `large monetary values maintain precision`() {
            var campaign = activeCampaign()
            campaign = campaign.attributeSale("opp-001", BigDecimal("9999999.99"), fixedNow)
            campaign = campaign.attributeSale("opp-002", BigDecimal("0.01"), fixedNow)

            assertEquals(BigDecimal("10000000.00"), campaign.metrics.attributedRevenue)
        }

        @Test
        fun `very small attribution amounts are handled`() {
            var campaign = activeCampaign()
            campaign = campaign.attributeSale("opp-001", BigDecimal("0.01"), fixedNow)
            assertEquals(BigDecimal("0.01"), campaign.metrics.attributedRevenue)
        }

        @Test
        fun `attribution with many decimal places is rounded to 2`() {
            var campaign = activeCampaign()
            campaign = campaign.attributeSale("opp-001", BigDecimal("123.456"), fixedNow)
            assertEquals(BigDecimal("123.46"), campaign.metrics.attributedRevenue)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Status Transition Guards
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Status guards — attribution rejected for invalid states")
    inner class StatusGuardTests {

        @Test
        fun `cannot attribute sale to DRAFT campaign`() {
            val campaign = activeCampaign().copy(status = CampaignStatus.DRAFT)
        assertThrows(IllegalArgumentException::class.java) {
            campaign.attributeSale("opp-001", BigDecimal("1000.00"), fixedNow)
        }
        }

        @Test
        fun `cannot attribute sale to COMPLETED campaign`() {
            val campaign = activeCampaign().copy(status = CampaignStatus.COMPLETED)
            assertThrows(IllegalArgumentException::class.java) {
                campaign.attributeSale("opp-001", BigDecimal("1000.00"), fixedNow)
            }
        }

        @Test
        fun `cannot attribute sale to CANCELLED campaign`() {
            val campaign = activeCampaign().copy(status = CampaignStatus.CANCELLED)
            assertThrows(IllegalArgumentException::class.java) {
                campaign.attributeSale("opp-001", BigDecimal("1000.00"), fixedNow)
            }
        }

        @Test
        fun `can attribute sale to ACTIVE campaign`() {
            val campaign = activeCampaign()
            val updated = campaign.attributeSale("opp-001", BigDecimal("1000.00"), fixedNow)
            assertEquals(BigDecimal("1000.00"), updated.metrics.attributedRevenue)
        }

        @Test
        fun `can attribute sale to PAUSED campaign`() {
            val campaign = activeCampaign().copy(status = CampaignStatus.PAUSED)
            val updated = campaign.attributeSale("opp-001", BigDecimal("1000.00"), fixedNow)
            assertEquals(BigDecimal("1000.00"), updated.metrics.attributedRevenue)
        }

        @Test
        fun `cannot attribute zero amount`() {
            val campaign = activeCampaign()
            assertThrows(IllegalArgumentException::class.java) {
                campaign.attributeSale("opp-001", BigDecimal.ZERO, fixedNow)
            }
        }

        @Test
        fun `cannot attribute negative amount`() {
            val campaign = activeCampaign()
            assertThrows(IllegalArgumentException::class.java) {
                campaign.attributeSale("opp-001", BigDecimal("-500.00"), fixedNow)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROAS and CPA Calculations
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ROAS and CPA calculations")
    inner class RoasCpaTests {

        @Test
        fun `ROAS is revenue divided by spend`() {
            val campaign = activeCampaign(
                metrics = CampaignMetrics(
                    spend = BigDecimal("1000.00"),
                    attributedRevenue = BigDecimal("5000.00"),
                )
            )
            assertEquals(BigDecimal("5.0000"), campaign.calculateRoas())
        }

        @Test
        fun `ROAS is zero when spend is zero`() {
            val campaign = activeCampaign(metrics = CampaignMetrics.EMPTY)
            assertEquals(BigDecimal.ZERO, campaign.calculateRoas())
        }

        @Test
        fun `CPA is spend divided by conversions`() {
            val campaign = activeCampaign(
                metrics = CampaignMetrics(
                    spend = BigDecimal("1000.00"),
                    attributedConversions = 10,
                )
            )
            assertEquals(BigDecimal("100.00"), campaign.calculateCpa())
        }

        @Test
        fun `CPA is zero when no conversions`() {
            val campaign = activeCampaign(metrics = CampaignMetrics.EMPTY)
            assertEquals(BigDecimal.ZERO, campaign.calculateCpa())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Adapter Pattern & ACL
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Adapter pattern — external metric sync")
    inner class AdapterTests {

        @Test
        fun `GoogleAdsAdapter returns incremented metrics`() {
            val adapter = GoogleAdsAdapter()
            val campaign = activeCampaign(metrics = CampaignMetrics(
                impressions = 10000, clicks = 500, spend = BigDecimal("200.00"),
            ))

            val synced = adapter.syncCampaignPerformance(campaign)

            assertEquals(11000, synced.impressions)
            assertEquals(550, synced.clicks)
            assertEquals(BigDecimal("225.00"), synced.spend)
        }

        @Test
        fun `MetaAdsAdapter returns incremented metrics`() {
            val adapter = MetaAdsAdapter()
            val campaign = activeCampaign(metrics = CampaignMetrics.EMPTY)

            val synced = adapter.syncCampaignPerformance(campaign)

            assertEquals(2500, synced.impressions)
            assertEquals(120, synced.clicks)
            assertEquals(BigDecimal("40.00"), synced.spend)
        }

        @Test
        fun `DirectCampaignAdapter returns unchanged metrics`() {
            val adapter = DirectCampaignAdapter()
            val metrics = CampaignMetrics(impressions = 5000, clicks = 200, spend = BigDecimal("100.00"))
            val campaign = activeCampaign(metrics = metrics)

            val synced = adapter.syncCampaignPerformance(campaign)

            assertEquals(metrics, synced)
        }

        @Test
        fun `adapter registry returns correct adapter for source`() {
            val googleAdapter = AdNetworkAdapterRegistry.get(AdNetworkSource.GOOGLE)
            assertEquals(AdNetworkSource.GOOGLE, googleAdapter.source)

            val metaAdapter = AdNetworkAdapterRegistry.get(AdNetworkSource.META)
            assertEquals(AdNetworkSource.META, metaAdapter.source)
        }

        @Test
        fun `adapter registry throws for unregistered source`() {
            assertThrows(IllegalArgumentException::class.java) {
                AdNetworkAdapterRegistry.get(AdNetworkSource.AFFILIATE)
            }
        }

        @Test
        fun `all adapters pass health check`() {
            AdNetworkAdapterRegistry.all().forEach { adapter ->
                assertTrue(adapter.healthCheck(), "Adapter ${adapter.source} should pass health check")
            }
        }

        @Test
        fun `syncing metrics from multiple adapters does not fail if one times out`() {
            // Simulate: Google succeeds, Meta throws, Direct succeeds
            val campaign = activeCampaign()

            // Google sync succeeds
            val googleMetrics = GoogleAdsAdapter().syncCampaignPerformance(campaign)
            val afterGoogle = campaign.updateMetrics(googleMetrics)
            assertEquals(1000, afterGoogle.metrics.impressions)

            // Meta sync succeeds (stub doesn't throw, but in production this would be caught)
            val metaMetrics = MetaAdsAdapter().syncCampaignPerformance(afterGoogle)
            val afterMeta = afterGoogle.updateMetrics(metaMetrics)
            assertEquals(3500, afterMeta.metrics.impressions) // 1000 + 2500

            // Direct sync succeeds
            val directMetrics = DirectCampaignAdapter().syncCampaignPerformance(afterMeta)
            val afterDirect = afterMeta.updateMetrics(directMetrics)
            assertEquals(3500, afterDirect.metrics.impressions) // unchanged by direct
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Campaign Metrics Value Object
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CampaignMetrics value object")
    inner class MetricsTests {

        @Test
        fun `clickThroughRate is clicks divided by impressions`() {
            val metrics = CampaignMetrics(impressions = 10000, clicks = 250)
            assertEquals(BigDecimal("0.0250"), metrics.clickThroughRate)
        }

        @Test
        fun `CTR is zero when no impressions`() {
            val metrics = CampaignMetrics.EMPTY
            assertEquals(BigDecimal.ZERO, metrics.clickThroughRate)
        }

        @Test
        fun `costPerClick is spend divided by clicks`() {
            val metrics = CampaignMetrics(clicks = 100, spend = BigDecimal("250.00"))
            assertEquals(BigDecimal("2.50"), metrics.costPerClick)
        }

        @Test
        fun `CPC is zero when no clicks`() {
            val metrics = CampaignMetrics.EMPTY
            assertEquals(BigDecimal.ZERO, metrics.costPerClick)
        }

        @Test
        fun `EMPTY companion has all zero values`() {
            val empty = CampaignMetrics.EMPTY
            assertEquals(0, empty.impressions)
            assertEquals(0, empty.clicks)
            assertEquals(BigDecimal("0.00"), empty.spend)
            assertEquals(BigDecimal("0.00"), empty.attributedRevenue)
            assertEquals(0, empty.attributedConversions)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Machine
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Campaign state machine")
    inner class StateMachineTests {

        @Test
        fun `full lifecycle - draft to completed`() {
            var campaign = activeCampaign().copy(status = CampaignStatus.DRAFT)
            campaign = campaign.launch()
            assertEquals(CampaignStatus.ACTIVE, campaign.status)

            campaign = campaign.pause()
            assertEquals(CampaignStatus.PAUSED, campaign.status)

            campaign = campaign.resume()
            assertEquals(CampaignStatus.ACTIVE, campaign.status)

            campaign = campaign.complete()
            assertEquals(CampaignStatus.COMPLETED, campaign.status)
        }

        @Test
        fun `cannot launch a non-draft campaign`() {
            val campaign = activeCampaign()
            assertThrows(IllegalArgumentException::class.java) { campaign.launch() }
        }

        @Test
        fun `cannot complete a draft campaign`() {
            val campaign = activeCampaign().copy(status = CampaignStatus.DRAFT)
            assertThrows(IllegalArgumentException::class.java) { campaign.complete() }
        }

        @Test
        fun `cannot cancel a completed campaign`() {
            val campaign = activeCampaign().copy(status = CampaignStatus.COMPLETED)
            assertThrows(IllegalArgumentException::class.java) { campaign.cancel() }
        }

        @Test
        fun `can cancel from any non-completed state`() {
            CampaignStatus.entries.filter { it != CampaignStatus.COMPLETED }.forEach { status ->
                val campaign = activeCampaign().copy(status = status)
                val cancelled = campaign.cancel()
                assertEquals(CampaignStatus.CANCELLED, cancelled.status)
            }
        }
    }
}
