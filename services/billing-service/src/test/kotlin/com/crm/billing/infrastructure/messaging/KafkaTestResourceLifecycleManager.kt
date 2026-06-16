package com.crm.billing.infrastructure.messaging

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts Kafka and Postgres Testcontainers for billing-service integration tests.
 *
 * Creates the "billing" schema in the blank Postgres container before Quarkus
 * starts. Configures all incoming and outgoing channels to use the Testcontainers
 * Kafka broker instead of the default localhost:9092.
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
        postgres.createConnection("").use { conn ->
            conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS billing")
        }

        val kafkaBrokers = kafka.bootstrapServers

        return mapOf(
            // ── Datasource ───────────────────────────────────────────────────
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "quarkus.datasource.db-kind" to "postgresql",
            "quarkus.hibernate-orm.database.generation" to "create",

            // ── Kafka — global default ──────────────────────────────────────
            "kafka.bootstrap.servers" to kafkaBrokers,

            // ── Kafka — incoming ─────────────────────────────────────────────
            "mp.messaging.incoming.sales-opportunity-events.bootstrap.servers" to kafkaBrokers,

            // ── Kafka — outgoing ─────────────────────────────────────────────
            "mp.messaging.outgoing.billing-events.bootstrap.servers" to kafkaBrokers,
            "mp.messaging.outgoing.domain-events.bootstrap.servers" to kafkaBrokers,
        )
    }

    override fun stop() {
        kafka.stop()
        postgres.stop()
    }
}
