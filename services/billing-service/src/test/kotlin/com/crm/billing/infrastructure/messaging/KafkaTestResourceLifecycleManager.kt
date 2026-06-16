package com.crm.billing.infrastructure.messaging

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts Kafka and Postgres Testcontainers for billing-service integration tests.
 *
 * Creates the "billing" schema in the blank Postgres container before Quarkus
 * starts. Without this, Hibernate's drop-and-create strategy fails because
 * entities declare schema = "billing" but the schema doesn't exist in a fresh
 * Testcontainers instance.
 */
class KafkaTestResourceLifecycleManager : QuarkusTestResourceLifecycleManager {

    private val postgres = PostgreSQLContainer(
        DockerImageName.parse("postgres:17-alpine")
    ).withDatabaseName("crm_billing")
        .withUsername("crm")
        .withPassword("crm")

    private val kafka = KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.8.0")
    )

    override fun start(): Map<String, String> {
        postgres.start()
        kafka.start()

        // ── Create service-specific schema ────────────────────────────────────
        // Entities use @Table(schema = "billing") — the schema must exist before
        // Hibernate's drop-and-create runs.
        postgres.createConnection("").use { conn ->
            conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS billing")
        }

        return mapOf(
            // ── Datasource ───────────────────────────────────────────────────
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "quarkus.datasource.db-kind" to "postgresql",

            // ── Kafka ────────────────────────────────────────────────────────
            "kafka.bootstrap.servers" to kafka.bootstrapServers,
            "mp.messaging.incoming.sales-opportunity-events.bootstrap.servers" to kafka.bootstrapServers,
        )
    }

    override fun stop() {
        kafka.stop()
        postgres.stop()
    }
}
