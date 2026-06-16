package com.crm.billing.infrastructure.messaging

import io.quarkus.kafka.client.serialization.JsonbDeserializer

/**
 * Deserializes JSON bytes into {@link OpportunityEventEnvelope} using JSON-B.
 *
 * Configured as the value deserializer for the sales-opportunity-events channel
 * in application-test.properties. This replaces StringDeserializer which returned
 * raw strings that could not be cast to the target type.
 */
class OpportunityEventEnvelopeDeserializer : JsonbDeserializer<OpportunityEventEnvelope>(OpportunityEventEnvelope::class.java)
