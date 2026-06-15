package com.crm.marketing.infrastructure.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime
import java.util.UUID

data class SalesEventEnvelope(
    @JsonProperty("eventType") val eventType: String = "",
    @JsonProperty("source") val source: String = "",
    @JsonProperty("actorId") val actorId: String? = null,
    @JsonProperty("payload") val payload: SalesEventPayload = SalesEventPayload(),
)

data class SalesEventPayload(
    @JsonProperty("opportunityId") val opportunityId: String? = null,
    @JsonProperty("isWon") val isWon: Boolean? = null,
    @JsonProperty("outcome") val outcome: String? = null,
    @JsonProperty("reason") val reason: String? = null,
    @JsonProperty("closedAt") val closedAt: OffsetDateTime? = null,
    @JsonProperty("customerId") val customerId: String? = null,
    @JsonProperty("accountId") val accountId: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("totalAmount") val totalAmount: String? = null,
    @JsonProperty("amount") val amount: String? = null,
    @JsonProperty("attributionTag") val attributionTag: String? = null,
    @JsonProperty("utmCampaignId") val utmCampaignId: String? = null,
    @JsonProperty("fromStage") val fromStage: String? = null,
    @JsonProperty("toStage") val toStage: String? = null,
)
