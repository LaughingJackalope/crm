package com.crm.marketing.infrastructure.persistence

import com.crm.marketing.domain.event.CampaignDomainEvent
import com.crm.common.messaging.EventEnvelope

internal fun EventEnvelope<*>.toJson(): String {
    val payloadJson = when (val p = payload) {
        is CampaignDomainEvent -> p.toJson()
        else -> p.toString()
    }
    return """{"eventType":"$eventType","source":"$source","payload":$payloadJson}"""
}

internal fun CampaignDomainEvent.toJson(): String = when (this) {
    is CampaignDomainEvent.CampaignCreated -> """{"campaignId":"$campaignId","name":"$name","source":"$source","createdAt":"$createdAt"}"""
    is CampaignDomainEvent.CampaignLaunched -> """{"campaignId":"$campaignId","launchedAt":"$launchedAt"}"""
    is CampaignDomainEvent.CampaignPaused -> """{"campaignId":"$campaignId","pausedAt":"$pausedAt"}"""
    is CampaignDomainEvent.CampaignCompleted -> """{"campaignId":"$campaignId","completedAt":"$completedAt"}"""
    is CampaignDomainEvent.CampaignMetricsSynced -> """{"campaignId":"$campaignId","source":"$source","impressions":$impressions,"clicks":$clicks,"spend":$spend,"syncedAt":"$syncedAt"}"""
    is CampaignDomainEvent.RevenueAttributedToCampaign -> """{"campaignId":"$campaignId","opportunityId":"$opportunityId","amount":$amount,"totalAttributedRevenue":$totalAttributedRevenue,"attributedAt":"$attributedAt"}"""
    is CampaignDomainEvent.ConversionDeclared -> """{"campaignId":"$campaignId","customerId":"$customerId","revenue":$revenue,"occurredAt":"$occurredAt"}"""
}
