package com.crm.billing.infrastructure.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime
import java.util.UUID

/**
 * DTO for deserializing incoming opportunity events from the Sales context.
 *
 * Wire format (from Sales outbox):
 * {
 *   "eventType": "OpportunityClosed",
 *   "source": "sales",
 *   "actorId": "...",
 *   "payload": { "opportunityId": "...", "outcome": "won", ... }
 * }
 */
data class OpportunityEventEnvelope(
    @JsonProperty("eventType")
    val eventType: String = "",

    @JsonProperty("source")
    val source: String = "",

    @JsonProperty("actorId")
    val actorId: String? = null,

    @JsonProperty("payload")
    val payload: OpportunityEventPayload = OpportunityEventPayload(),
)

data class OpportunityEventPayload(
    @JsonProperty("opportunityId")
    val opportunityId: String? = null,

    @JsonProperty("isWon")
    val isWon: Boolean? = null,

    @JsonProperty("outcome")
    val outcome: String? = null,

    @JsonProperty("reason")
    val reason: String? = null,

    @JsonProperty("closedAt")
    val closedAt: OffsetDateTime? = null,
)
