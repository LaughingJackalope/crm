package com.crm.marketing.infrastructure.messaging

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts Kafka and Postgres Testcontainers for marketing-service integration tests.
 */
class KafkaTestResourceLifecycleManager : QuarkusTestResourceLifecycleManager {

    private val postgres = PostgreSQLContainer(
        DockerImageName.parse("postgres:17-alpine")
    ).withDatabaseName("crm_marketing")
        .withUsername("crm")
        .withPassword("crm")

    private val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))

    override fun start(): Map<String, String> {
        postgres.start()
        kafka.start()

        postgres.createConnection("").use { conn ->
            conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS marketing")
        }

        val kafkaBrokers = kafka.bootstrapServers
        System.setProperty("kafka.bootstrap.servers", kafkaBrokers)
        System.setProperty("mp.messaging.incoming.sales-opportunity-events.bootstrap.servers", kafkaBrokers)
        System.setProperty("mp.messaging.outgoing.marketing-events.bootstrap.servers", kafkaBrokers)
        System.setProperty("mp.messaging.outgoing.domain-events.bootstrap.servers", kafkaBrokers)

        return mapOf(
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
