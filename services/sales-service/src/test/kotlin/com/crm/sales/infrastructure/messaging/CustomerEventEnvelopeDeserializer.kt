package com.crm.sales.infrastructure.messaging

import io.quarkus.kafka.client.serialization.JsonbDeserializer

/**
 * Deserializes JSON bytes into {@link CustomerEventEnvelope} using JSON-B.
 */
class CustomerEventEnvelopeDeserializer : JsonbDeserializer<CustomerEventEnvelope>(CustomerEventEnvelope::class.java)
