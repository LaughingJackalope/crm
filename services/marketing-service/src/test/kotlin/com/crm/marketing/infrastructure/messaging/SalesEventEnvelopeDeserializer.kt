package com.crm.marketing.infrastructure.messaging

import io.quarkus.kafka.client.serialization.JsonbDeserializer

/**
 * Deserializes JSON bytes into {@link SalesEventEnvelope} using JSON-B.
 */
class SalesEventEnvelopeDeserializer : JsonbDeserializer<SalesEventEnvelope>(SalesEventEnvelope::class.java)
