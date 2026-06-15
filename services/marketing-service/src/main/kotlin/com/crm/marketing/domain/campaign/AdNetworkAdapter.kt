package com.crm.marketing.domain.campaign

/**
 * Anti-Corruption Layer interface for external ad network integrations.
 *
 * This interface is the boundary between the Marketing domain and external
 * third-party APIs (Google Ads, Meta Ads, etc.). All external payloads are
 * translated into domain objects ([CampaignMetrics]) by the adapter
 * implementations, so the domain layer never depends on external API schemas.
 *
 * Implementations handle:
 * - API authentication and rate limiting
 * - External payload deserialization and validation
 * - Translation of external metric names to domain metric names
 * - Error handling and retry logic
 */
interface AdNetworkAdapter {

    /**
     * The ad network source this adapter handles.
     */
    val source: AdNetworkSource

    /**
     * Fetch the latest performance metrics for a campaign from the external
     * ad network and return them as a domain [CampaignMetrics] value object.
     *
     * @param campaign The local Campaign aggregate (contains campaignId and metadata).
     * @return Updated metrics from the external network.
     * @throws CampaignDomainException.AdapterSyncException if the sync fails.
     */
    fun syncCampaignPerformance(campaign: Campaign): CampaignMetrics

    /**
     * Validate that the adapter can connect to the external API.
     * Used for health checks.
     */
    fun healthCheck(): Boolean
}

/**
 * Stub implementation for Google Ads.
 * In production, this would use the Google Ads API client.
 */
class GoogleAdsAdapter : AdNetworkAdapter {
    override val source: AdNetworkSource = AdNetworkSource.GOOGLE

    override fun syncCampaignPerformance(campaign: Campaign): CampaignMetrics {
        // Stub: return incremented metrics simulating an API response
        val current = campaign.metrics
        return current.copy(
            impressions = current.impressions + 1000,
            clicks = current.clicks + 50,
            spend = current.spend.add(java.math.BigDecimal("25.00")),
        )
    }

    override fun healthCheck(): Boolean = true
}

/**
 * Stub implementation for Meta (Facebook/Instagram) Ads.
 * In production, this would use the Meta Marketing API client.
 */
class MetaAdsAdapter : AdNetworkAdapter {
    override val source: AdNetworkSource = AdNetworkSource.META

    override fun syncCampaignPerformance(campaign: Campaign): CampaignMetrics {
        val current = campaign.metrics
        return current.copy(
            impressions = current.impressions + 2500,
            clicks = current.clicks + 120,
            spend = current.spend.add(java.math.BigDecimal("40.00")),
        )
    }

    override fun healthCheck(): Boolean = true
}

/**
 * Stub implementation for direct/organic campaigns.
 * No external API — metrics are tracked internally.
 */
class DirectCampaignAdapter : AdNetworkAdapter {
    override val source: AdNetworkSource = AdNetworkSource.DIRECT

    override fun syncCampaignPerformance(campaign: Campaign): CampaignMetrics {
        return campaign.metrics // Direct campaigns don't have external sync
    }

    override fun healthCheck(): Boolean = true
}

/**
 * Registry of all available adapters.
 * New adapters are registered here — the domain layer remains unchanged.
 */
object AdNetworkAdapterRegistry {
    private val adapters: Map<AdNetworkSource, AdNetworkAdapter> = mapOf(
        AdNetworkSource.GOOGLE to GoogleAdsAdapter(),
        AdNetworkSource.META to MetaAdsAdapter(),
        AdNetworkSource.DIRECT to DirectCampaignAdapter(),
    )

    fun get(source: AdNetworkSource): AdNetworkAdapter =
        adapters[source] ?: throw IllegalArgumentException("No adapter registered for source: $source")

    fun all(): Collection<AdNetworkAdapter> = adapters.values
}
