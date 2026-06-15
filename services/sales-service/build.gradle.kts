/*
 * :services:sales-service
 *
 * Sales / Opportunity Pipeline Bounded Context.
 *
 * DDD layers: domain/ | application/ | infrastructure/{rest,persistence,messaging}
 */

plugins {
    id("io.quarkus") version "3.28.1"
    id("crm.quarkus-convention")
}

dependencies {
    implementation(project(":libs:contracts:open-api"))
    implementation(project(":libs:contracts:async-api"))
    implementation(project(":libs:common"))

    implementation(libs.quarkus.hibernate.orm.panache.kotlin)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.messaging.kafka)

    // ── Unit testing ────────────────────────────────────────────────────────
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(kotlin("test"))

    // ── Integration testing ─────────────────────────────────────────────────
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.junit5.mockito)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)

    // ── Assertions ──────────────────────────────────────────────────────────
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
}
