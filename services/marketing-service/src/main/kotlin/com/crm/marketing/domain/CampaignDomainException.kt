package com.crm.marketing.domain
import java.util.UUID

/**
 * Sealed exception hierarchy for Marketing domain errors.
 */
sealed class CampaignDomainException(
    override val message: String,
) : RuntimeException(message) {

    class InvalidCampaignState(
        val campaignId: UUID,
        val currentStatus: String,
        val requiredStatus: String,
    ) : CampaignDomainException(
        "Campaign $campaignId is in status $currentStatus, required: $requiredStatus"
    )

    class CampaignNotFound(
        val campaignId: UUID,
    ) : CampaignDomainException("Campaign $campaignId not found")

    class DuplicateAttribution(
        val opportunityId: String,
        val campaignId: UUID,
    ) : CampaignDomainException(
        "Opportunity $opportunityId is already attributed to campaign $campaignId"
    )

    class InvalidAttributionAmount(
        val amount: String,
    ) : CampaignDomainException("Invalid attribution amount: $amount")

    class ConsumerProcessingException(
        override val message: String,
        override val cause: Throwable? = null,
    ) : CampaignDomainException(message)

    class AdapterSyncException(
        val source: String,
        val detail: String,
    ) : CampaignDomainException("Adapter sync failed for $source: $detail")
}
