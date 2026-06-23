/*
 * :services:ciam-service
 *
 * Customer Identity & Access Management (CIAM) Bounded Context.
 *
 * DDD layer separation:
 *   domain/       — Aggregates, Value Objects, Domain Events, Repository interfaces
 *   application/  — Use cases / command handlers, DTO mappers
 *   infrastructure/
 *     rest/       — Quarkus RESTEasy Reactive endpoints
 *     persistence/— Panache/Hibernate ORM entities, repository implementations
 *     messaging/  — Kafka producers for domain events
 */

plugins {
    id("io.quarkus") version "3.36.3"
    id("crm.quarkus-convention")
}

dependencies {
    // ── Generated API contracts ───────────────────────────────────────────
    implementation(project(":libs:contracts:open-api"))
    implementation(project(":libs:contracts:async-api"))

    // ── Shared cross-cutting concerns ─────────────────────────────────────
    implementation(project(":libs:common"))
    implementation(project(":libs:common-ui"))

    // ── Quarkus extensions ────────────────────────────────────────────────
    implementation(libs.quarkus.hibernate.orm.panache.kotlin)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.messaging.kafka)

    // ── Scheduling (outbox relay) ──────────────────────────────────────────
    implementation("io.quarkus:quarkus-scheduler")

    // ── Qute templating ────────────────────────────────────────────────────
    implementation("io.quarkus:quarkus-rest-qute")
    implementation("io.quarkus:quarkus-qute")

    // ── OIDC / IAM ─────────────────────────────────────────────────────────

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(kotlin("test"))

    // ── Integration test (Quarkus) ─────────────────────────────────────────
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.rest.assured)

    // ── Shared test infrastructure ─────────────────────────────────────────
    testImplementation(project(":libs:common-test"))
}
