/*
 * :libs:common
 *
 * Shared cross-cutting concerns for all CRM services:
 *   - OpenTelemetry tracing filters & interceptors
 *   - Unified error handling (Problem+JSON responses)
 *   - IAM / JWT parsing utilities
 *   - Common domain primitives (Result type, validation helpers)
 *   - Kafka message envelope wrapper
 *
 * This module is pure Kotlin — no Quarkus dependency to keep it lightweight
 * and testable without the container.
 */

plugins {
    id("crm.kotlin-convention")
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)

    // Jackson (for @JsonInclude etc.)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.1")

    // Observability
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.extension.kotlin)
    implementation(libs.micrometer.tracing.bridge.otel)

    // JWT parsing (small, no framework dependency)
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.nimbusds:nimbus-jose-jwt:9.41")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}
