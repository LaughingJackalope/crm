package com.crm.common.observability

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

/**
 * Utility to create OpenTelemetry spans for domain operations.
 * Used by infrastructure layers in each service module.
 */
object TracingFilter {

    fun <T> traceOperation(
        tracer: Tracer,
        operationName: String,
        attributes: Map<String, String> = emptyMap(),
        block: () -> T,
    ): T {
        val span = tracer.spanBuilder(operationName).startSpan()
        attributes.forEach { (k, v) -> span.setAttribute(k, v) }
        return try {
            span.makeCurrent().use { block() }
        } catch (e: Exception) {
            span.recordException(e)
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.message ?: "Unknown error")
            throw e
        } finally {
            span.end()
        }
    }

    suspend fun <T> traceOperationAsync(
        tracer: Tracer,
        operationName: String,
        attributes: Map<String, String> = emptyMap(),
        block: suspend () -> T,
    ): T {
        val span = tracer.spanBuilder(operationName).startSpan()
        attributes.forEach { (k, v) -> span.setAttribute(k, v) }
        return try {
            span.makeCurrent().use { kotlinx.coroutines.runBlocking { block() } }
        } catch (e: Exception) {
            span.recordException(e)
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.message ?: "Unknown error")
            throw e
        } finally {
            span.end()
        }
    }
}
