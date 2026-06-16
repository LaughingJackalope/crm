package com.crm.common.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.context.propagation.TextMapSetter

/**
 * W3C Trace Context carrier for bridging OTel trace context across the
 * transactional outbox boundary.
 *
 * Extracts the active W3C trace context (traceparent, tracestate) into a flat
 * map before writing to the outbox, and reconstructs the OTel Context from
 * that map in the OutboxRelay before publishing to Kafka.
 */
object TraceContextCarrier {

    private val propagator: TextMapPropagator =
        GlobalOpenTelemetry.getPropagators().textMapPropagator

    /**
     * Extract the current W3C trace context into a flat Map<String, String>.
     * Call inside the application service BEFORE writing the outbox event.
     */
    fun extractCurrentTraceHeaders(): Map<String, String> {
        val carrier = mutableMapOf<String, String>()
        val currentContext = Context.current()
        propagator.inject(currentContext, carrier, TextMapSetter { map, key, value ->
            map?.set(key, value)
        })
        return carrier
    }

    /**
     * Reconstruct an OTel [Context] from a map of W3C trace headers.
     * Call in the OutboxRelay AFTER reading the outbox event but BEFORE
     * publishing to Kafka. Activate via `.makeCurrent().use { ... }`.
     */
    fun createContextFromHeaders(headers: Map<String, String>?): Context {
        if (headers.isNullOrEmpty()) return Context.current()
        return propagator.extract(Context.current(), headers, MapTextMapGetter)
    }

    /** Convenience overload for deserialized metadata stored as a JSON string. */
    fun createContextFromHeaders(metadata: String?): Context {
        if (metadata.isNullOrBlank()) return Context.current()
        return try {
            createContextFromHeaders(parseMetadataToMap(metadata))
        } catch (ex: Exception) {
            Context.current()
        }
    }

    /** Get the current trace ID as a hex string for logging correlation. */
    fun currentTraceId(): String =
        Span.fromContextOrNull(Context.current())?.spanContext?.traceId ?: "unknown"

    /** Get the current span ID as a hex string for logging correlation. */
    fun currentSpanId(): String =
        Span.fromContextOrNull(Context.current())?.spanContext?.spanId ?: "unknown"

    /** Check if the current context has a valid span. */
    fun hasActiveSpan(): Boolean =
        Span.fromContextOrNull(Context.current())?.spanContext?.isValid ?: false

    /** Serialize a trace headers map to a JSON string for storage. */
    fun headersToJson(headers: Map<String, String>): String {
        val entries = headers.entries.joinToString(",") { (key, value) ->
            "\"$key\":\"$value\""
        }
        return "{$entries}"
    }

    /** Parse a JSON metadata string into a Map. */
    internal fun parseMetadataToMap(metadata: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val trimmed = metadata.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val content = trimmed.substring(1, trimmed.length - 1)
            for (pair in content.split(",")) {
                val colonIndex = pair.indexOf(":")
                if (colonIndex > 0) {
                    val key = pair.substring(0, colonIndex).trim().removeSurrounding("\"")
                    val value = pair.substring(colonIndex + 1).trim().removeSurrounding("\"")
                    result[key] = value
                }
            }
        }
        return result
    }

    /**
     * [TextMapGetter] for extracting trace context from a Map.
     * OTel API: get carrier parameter is @Nullable, keys carrier is non-null.
     */
    private object MapTextMapGetter : TextMapGetter<Map<String, String>> {
        override fun get(carrier: Map<String, String>?, key: String): String? {
            return carrier?.get(key)
        }

        override fun keys(carrier: Map<String, String>): Iterable<String> {
            return carrier.keys
        }
    }
}
