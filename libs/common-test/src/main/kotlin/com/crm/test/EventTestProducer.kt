package com.crm.test

import jakarta.enterprise.context.ApplicationScoped
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

/**
 * Shared Kafka producer for injecting test events into topics during integration tests.
 *
 * Reads `kafka.bootstrap.servers` from system properties (set by
 * [CrmIntegrationTestResourceLifecycleManager]) with a fallback to localhost.
 *
 * Usage:
 * ```
 * @Inject
 * lateinit var testProducer: EventTestProducer
 *
 * testProducer.send("my.topic", key, jsonEnvelope)
 * ```
 */
@ApplicationScoped
class EventTestProducer {

    private val producer: KafkaProducer<String, String> by lazy {
        val props = Properties().apply {
            put("bootstrap.servers", System.getProperty("kafka.bootstrap.servers", "localhost:9092"))
            put("key.serializer", StringSerializer::class.java.name)
            put("value.serializer", StringSerializer::class.java.name)
            put("acks", "all")
            put("retries", "3")
        }
        KafkaProducer(props)
    }

    fun send(topic: String, key: String, value: String) {
        producer.send(ProducerRecord(topic, key, value)).get()
    }

    fun close() {
        producer.close()
    }
}
