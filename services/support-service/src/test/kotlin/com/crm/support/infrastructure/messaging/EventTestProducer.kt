package com.crm.support.infrastructure.messaging

import jakarta.enterprise.context.ApplicationScoped
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

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

    fun close() { producer.close() }
}
