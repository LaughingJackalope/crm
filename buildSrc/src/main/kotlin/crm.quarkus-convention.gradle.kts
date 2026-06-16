/*
 * Quarkus convention plugin — adds common Quarkus dependencies.
 *
 * Apply alongside the io.quarkus plugin in your module's build.gradle.kts:
 *
 *   plugins {
 *       id("io.quarkus") version "3.28.1"
 *       id("crm.quarkus-convention")
 *   }
 */

plugins {
    id("crm.kotlin-convention")
}

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
