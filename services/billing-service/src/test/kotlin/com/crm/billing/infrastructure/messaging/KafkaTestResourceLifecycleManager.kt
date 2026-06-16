package com.crm.billing.infrastructure.messaging

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts Postgres Testcontainer for billing-service integration tests.
 *
 * Uses Quarkus Dev Services for Kafka (auto-configured) instead of manual
 * Testcontainers Kafka. This avoids the bootstrap.servers config resolution
 * issues with SmallRye Reactive Messaging channels.
 */
class KafkaTestResourceLifecycleManager : QuarkusTestResourceLifecycleManager {

    private val postgres = PostgreSQLContainer(
        DockerImageName.parse("postgres:17-alpine")
    ).withDatabaseName("crm_billing")
        .withUsername("crm")
        .withPassword("crm")

    override fun start(): Map<String, String> {
        postgres.start()

        // ── Create service-specific schema ────────────────────────────────────
        postgres.createConnection("").use { conn ->
            conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS billing")
        }

        return mapOf(
            // ── Datasource ───────────────────────────────────────────────────
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "quarkus.datasource.db-kind" to "postgresql",
            "quarkus.hibernate-orm.database.generation" to "create",

            // ── Dev Services for Kafka ───────────────────────────────────────
            // Disables the default devservices.kafka.enabled=true so we can
            // use our own Testcontainers Kafka via the KafkaDevServicesBuildTimeConfig.
            // Actually, we WANT dev services — it auto-configures all channels.
            "quarkus.kafka.devservices.enabled" to "true",
        )
    }

    override fun stop() {
        postgres.stop()
    }
}
