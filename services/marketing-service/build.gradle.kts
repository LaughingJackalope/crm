plugins {
    id("io.quarkus") version "3.36.3"
    id("crm.quarkus-convention")
}

dependencies {
    implementation(project(":libs:contracts:open-api"))
    implementation(project(":libs:contracts:async-api"))
    implementation(project(":libs:common"))
    implementation(project(":libs:common-ui"))

    // ── Qute templating ────────────────────────────────────────────────────
    implementation("io.quarkus:quarkus-rest-qute")
    implementation("io.quarkus:quarkus-qute")

    implementation(libs.quarkus.hibernate.orm.panache.kotlin)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.messaging.kafka)

    implementation("io.quarkus:quarkus-scheduler")

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(kotlin("test"))

    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.junit5.mockito)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.rest.assured)

    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.awaitility:awaitility-kotlin:4.3.0")

    // ── Shared test infrastructure ─────────────────────────────────────────
    testImplementation(project(":libs:common-test"))
}
