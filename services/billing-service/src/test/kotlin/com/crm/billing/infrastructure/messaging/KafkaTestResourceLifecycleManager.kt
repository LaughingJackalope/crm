package com.crm.billing.infrastructure.messaging

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts Kafka and Postgres Testcontainers for billing-service integration tests.
 *
 * Uses System.setProperty() to ensure the Testcontainers broker address is visible
 * to the Quarkus Kafka connector, which reads from system properties (not from the
 * QuarkusTestResourceLifecycleManager config source).
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

        // ── Set system properties for Kafka bootstrap ─────────────────────────
        // System properties have highest priority in Quarkus config and are
        // reliably picked up by the SmallRye Kafka connector for ALL channels.
        val kafkaBrokers = kafka.bootstrapServers
        System.setProperty("kafka.bootstrap.servers", kafkaBrokers)
        System.setProperty("mp.messaging.incoming.sales-opportunity-events.bootstrap.servers", kafkaBrokers)
        System.setProperty("mp.messaging.outgoing.billing-events.bootstrap.servers", kafkaBrokers)
        System.setProperty("mp.messaging.outgoing.domain-events.bootstrap.servers", kafkaBrokers)

        return mapOf(
            // ── Datasource ───────────────────────────────────────────────────
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "quarkus.datasource.db-kind" to "postgresql",
            "quarkus.hibernate-orm.database.generation" to "create",
        )
    }

    override fun stop() {
        kafka.stop()
        postgres.stop()
    }
}
