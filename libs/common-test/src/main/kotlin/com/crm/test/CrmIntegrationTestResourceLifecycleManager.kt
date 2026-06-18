package com.crm.test

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Shared Testcontainers lifecycle manager for all CRM service integration tests.
 *
 * Starts:
 *   - PostgreSQL 17-alpine with the given schema
 *   - Confluent Kafka 7.7.1 in KRaft mode (no Zookeeper)
 *
 * Both the Quarkus reactive messaging channels and [EventTestProducer] connect
 * to the same Kafka instance via the `kafka.bootstrap.servers` system property
 * and Quarkus config.
 *
 * ## Usage
 *
 * Each service declares a thin subclass that specifies its schema:
 *
 * ```
 * // In the service's test source:
 * class MyServiceIntegrationTestResource :
 *     CrmIntegrationTestResourceLifecycleManager(schema = "sales")
 * ```
 *
 * And the test references it:
 * ```
 * @QuarkusTest
 * @QuarkusTestResource(MyServiceIntegrationTestResource::class)
 * class MyIntegrationTest { ... }
 * ```
 *
 * @param schema PostgreSQL schema name to create (e.g. "sales", "billing").
 * @param dbName PostgreSQL database name (defaults to "crm", shared across services).
 */
open class CrmIntegrationTestResourceLifecycleManager(
    private val schema: String,
    private val dbName: String = DEFAULT_DB_NAME,
) : QuarkusTestResourceLifecycleManager {

    private val postgres = PostgreSQLContainer(
        DockerImageName.parse("postgres:17-alpine")
    ).withDatabaseName(dbName)
        .withUsername(DB_USER)
        .withPassword(DB_PASSWORD)

    private val kafka = KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.7.1")
    ).withKraft()

    override fun start(): Map<String, String> {
        postgres.start()
        kafka.start()

        // Create the service-specific schema
        postgres.createConnection("").use { conn ->
            conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS $schema")
        }

        // Make bootstrap.servers available to EventTestProducer via system property
        System.setProperty("kafka.bootstrap.servers", kafka.bootstrapServers)

        return mapOf(
            // ── Datasource ───────────────────────────────────────────────────
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "quarkus.datasource.db-kind" to "postgresql",
            "quarkus.hibernate-orm.database.generation" to "create",

            // ── Kafka ────────────────────────────────────────────────────────
            // Disable Dev Services — we provide our own Kafka container.
            "quarkus.kafka.devservices.enabled" to "false",
            "kafka.bootstrap.servers" to kafka.bootstrapServers,
        )
    }

    override fun stop() {
        kafka.stop()
        postgres.stop()
    }

    companion object {
        const val DEFAULT_DB_NAME = "crm"
        const val DB_USER = "crm"
        const val DB_PASSWORD = "crm"
    }
}
