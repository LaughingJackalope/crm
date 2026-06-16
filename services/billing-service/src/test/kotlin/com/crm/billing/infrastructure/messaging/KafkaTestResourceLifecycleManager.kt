package com.crm.billing.infrastructure.messaging

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts Kafka and Postgres Testcontainers for billing-service integration tests.
 *
 * QuarkusTestResourceLifecycleManager is invoked by the Quarkus test framework
 * before the application starts. The returned map is injected as system
 * properties, overriding application.properties values at runtime.
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

        return mapOf(
            // ── Datasource ───────────────────────────────────────────────────
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "quarkus.datasource.db-kind" to "postgresql",
            "quarkus.hibernate-orm.database.generation" to "create",

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
