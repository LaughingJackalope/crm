package com.crm.billing.infrastructure.messaging

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts a Kafka Testcontainer and configures the Quarkus reactive messaging
 * connector to point at it for the billing-service integration tests.
 */
class KafkaTestResourceLifecycleManager : QuarkusTestResourceLifecycleManager {

    private val kafka = KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.8.0")
    )

    override fun start(): Map<String, String> {
        kafka.start()

        return mapOf(
            "kafka.bootstrap.servers" to kafka.bootstrapServers,
            "mp.messaging.incoming.sales-opportunity-events.bootstrap.servers" to kafka.bootstrapServers,
        )
    }

    override fun stop() {
        kafka.stop()
    }
}
