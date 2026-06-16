/*
 * Quarkus convention plugin — shared config for all :services:* modules.
 *
 * Apply alongside the io.quarkus plugin in your module's build.gradle.kts:
 *
 *   plugins {
 *       id("io.quarkus") version "3.28.1"
 *       id("crm.quarkus-convention")
 *   }
 *
 * Version resolution:
 *   Accepts a `-Pversion=...` project property (injected by CI). Falls back to
 *   "0.0.1-SNAPSHOT" when no property is supplied (local dev default).
 */

plugins {
    id("crm.kotlin-convention")
}

// ── Version ────────────────────────────────────────────────────────────────────
// CI injects `-Pversion=1.0.0` or `-Pversion=0.1.0-SNAPSHOT-42` via Gradle CLI.
// `findProperty` returns null when the property is unset, falling through to the
// local-development default.
version = findProperty("version")?.toString() ?: "0.0.1-SNAPSHOT"
val quarkusPlatformVersion: String by project

dependencies {
    // Enforce Quarkus BOM for version alignment
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))

    // Core Quarkus
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-kotlin")

    // Observability
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    // OpenTelemetry distributed tracing
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.quarkus:quarkus-smallrye-health")

    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.rest-assured:rest-assured")
}
