package com.crm.support.infrastructure.messaging

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts Postgres Testcontainer for support-service integration tests.
 * Uses Quarkus Dev Services for Kafka.
 */
class KafkaTestResourceLifecycleManager : QuarkusTestResourceLifecycleManager {

    private val postgres = PostgreSQLContainer(
        DockerImageName.parse("postgres:17-alpine")
    ).withDatabaseName("crm_support")
        .withUsername("crm")
        .withPassword("crm")

    override fun start(): Map<String, String> {
        postgres.start()

        postgres.createConnection("").use { conn ->
            conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS support")
        }

        return mapOf(
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "quarkus.datasource.db-kind" to "postgresql",
            "quarkus.hibernate-orm.database.generation" to "create",
            "quarkus.kafka.devservices.enabled" to "true",
        )
    }

    override fun stop() {
        postgres.stop()
    }
}
