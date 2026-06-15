package com.crm.sales.infrastructure.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime
import java.util.UUID

/**
 * DTO for deserializing the envelope wrapping incoming customer events from Kafka.
 *
 * Wire format (from CIAM outbox):
 * {
 *   "eventType": "CustomerRegistered",
 *   "source": "ciam",
 *   "actorId": "...",
 *   "payload": { ... event-specific fields ... }
 * }
 *
 * The payload is polymorphic — we extract only the fields we care about
 * for Sales context (customerId/contactId for opportunity creation).
 */
data class CustomerEventEnvelope(
    @JsonProperty("eventType")
    val eventType: String = "",

    @JsonProperty("source")
    val source: String = "",

    @JsonProperty("actorId")
    val actorId: String? = null,

    @JsonProperty("payload")
    val payload: CustomerEventPayload = CustomerEventPayload(),
)

/**
 * Polymorphic payload — fields are nullable because different event types
 * populate different subsets.
 */
data class CustomerEventPayload(
    // Common across all events
    @JsonProperty("entityId")
    val entityId: String? = null,

    // CustomerRegistered
    @JsonProperty("displayName")
    val displayName: String? = null,
    @JsonProperty("source")
    val source: String? = null,
    @JsonProperty("registeredAt")
    val registeredAt: OffsetDateTime? = null,

    // LeadQualified
    @JsonProperty("previousStage")
    val previousStage: String? = null,
    @JsonProperty("qualifiedAt")
    val qualifiedAt: OffsetDateTime? = null,

    // LifecycleStageChanged
    @JsonProperty("fromStage")
    val fromStage: String? = null,
    @JsonProperty("toStage")
    val toStage: String? = null,
    @JsonProperty("changedAt")
    val changedAt: OffsetDateTime? = null,
)
