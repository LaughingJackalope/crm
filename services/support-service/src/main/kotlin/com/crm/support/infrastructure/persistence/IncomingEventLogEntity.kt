package com.crm.support.infrastructure.persistence

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "incoming_event_log", schema = "support")
class IncomingEventLogEntity : PanacheEntityBase {

    @Id
    @Column(name = "event_id", nullable = false)
    lateinit var eventId: UUID

    @Column(name = "event_type", nullable = false, length = 128)
    lateinit var eventType: String

    @Column(name = "source", nullable = false, length = 32)
    lateinit var source: String

    @Column(name = "entity_id", nullable = false, length = 36)
    lateinit var entityId: String

    @Column(name = "processed_at", nullable = false)
    lateinit var processedAt: Instant

    companion object : PanacheCompanion<IncomingEventLogEntity>
}
