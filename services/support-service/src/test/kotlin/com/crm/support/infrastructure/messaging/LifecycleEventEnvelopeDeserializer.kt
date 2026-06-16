package com.crm.support.infrastructure.messaging

import io.quarkus.kafka.client.serialization.JsonbDeserializer

/**
 * Deserializes JSON bytes into {@link LifecycleEventEnvelope} using JSON-B.
 */
class LifecycleEventEnvelopeDeserializer : JsonbDeserializer<LifecycleEventEnvelope>(LifecycleEventEnvelope::class.java)
