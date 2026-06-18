/*
 * :libs:common-test
 *
 * Shared test infrastructure for all CRM service integration tests.
 * Provides:
 *   - CrmIntegrationTestResourceLifecycleManager — starts Postgres + Kafka Testcontainers
 *   - EventTestProducer — injects JSON envelopes into Kafka topics
 *   - TestTags — constants for @Tag annotations
 *
 * This module is test-only; it is never published or included in production builds.
 * It applies the Quarkus platform BOM for version alignment but not the Quarkus plugin.
 */

plugins {
    id("crm.kotlin-convention")
}

val quarkusPlatformVersion: String by project

dependencies {
    // ── Quarkus BOM for version alignment (no plugin — just the BOM) ─────────
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))
    testImplementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))

    // Quarkus test resource lifecycle
    api(libs.quarkus.junit5)
    api(libs.testcontainers.junit)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.kafka)
    api(libs.rest.assured)

    // Kafka producer for test event injection
    implementation(libs.kafka.clients)

    // Awaitility for async assertions
    api("org.awaitility:awaitility-kotlin:4.3.0")

    // AssertJ
    api("org.assertj:assertj-core:3.27.3")
}
