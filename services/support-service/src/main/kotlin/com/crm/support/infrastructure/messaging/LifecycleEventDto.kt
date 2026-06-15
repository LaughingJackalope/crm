package com.crm.support.infrastructure.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime
import java.util.UUID

/**
 * DTO for deserializing CIAM lifecycle events.
 */
data class LifecycleEventEnvelope(
    @JsonProperty("eventType")
    val eventType: String = "",

    @JsonProperty("source")
    val source: String = "",

    @JsonProperty("actorId")
    val actorId: String? = null,

    @JsonProperty("payload")
    val payload: LifecycleEventPayload = LifecycleEventPayload(),
)

data class LifecycleEventPayload(
    @JsonProperty("entityId")
    val entityId: String? = null,

    @JsonProperty("customerId")
    val customerId: String? = null,

    @JsonProperty("contactId")
    val contactId: String? = null,

    @JsonProperty("fromStage")
    val fromStage: String? = null,

    @JsonProperty("toStage")
    val toStage: String? = null,

    @JsonProperty("previousStage")
    val previousStage: String? = null,

    @JsonProperty("newStage")
    val newStage: String? = null,

    @JsonProperty("lifecycleStage")
    val lifecycleStage: String? = null,

    @JsonProperty("reason")
    val reason: String? = null,

    @JsonProperty("changedAt")
    val changedAt: OffsetDateTime? = null,

    @JsonProperty("deactivatedAt")
    val deactivatedAt: OffsetDateTime? = null,
)
