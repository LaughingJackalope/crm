package com.crm.ciam.application

import com.crm.ciam.domain.customer.Customer
import com.crm.ciam.domain.customer.CustomerRepository
import com.crm.ciam.domain.customer.EmailAddress
import com.crm.ciam.domain.customer.LifecycleStage
import com.crm.ciam.domain.customer.PhoneNumber
import com.crm.ciam.domain.event.CiamDomainEvent
import com.crm.ciam.domain.event.DisqualificationReason
import com.crm.ciam.infrastructure.persistence.OutboxEventEntity
import com.crm.ciam.infrastructure.persistence.OutboxEventRepository
import com.crm.common.error.NotFoundException
import com.crm.common.iam.JwtContext
import com.crm.common.messaging.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Application service — orchestrates use cases for the CIAM context.
 *
 * ## Transaction boundaries
 *
 * Every command method is `@Transactional`. Within a single transaction we:
 * 1. Load the aggregate from the database.
 * 2. Execute the domain command (aggregate validates + produces events).
 * 3. Save the updated aggregate.
 * 4. Write domain events to the **transactional outbox** (`outbox_event` table).
 *
 * The transaction commits atomically — either both the aggregate update AND
 * the outbox rows are persisted, or neither is. A background relay polls
 * the outbox and publishes to Kafka.
 */
@ApplicationScoped
class CustomerCommandService(
    private val customerRepository: CustomerRepository,
    private val outboxRepository: OutboxEventRepository,
) {

    @Transactional
    fun registerContact(
        displayName: String,
        firstName: String,
        lastName: String,
        email: String,
        phone: String? = null,
        title: String? = null,
        registrationSource: String? = null,
        actor: JwtContext? = null,
    ): Customer {
        val emailVo = EmailAddress(email)
        require(!customerRepository.existsByEmail(emailVo)) {
            "Customer with email $email already exists"
        }

        val customer = Customer(
            displayName = displayName,
            source = registrationSource,
        )
        val contactResult = customer.registerContact(
            firstName = firstName,
            lastName = lastName,
            email = emailVo,
            phone = phone?.let { PhoneNumber(it) },
            title = title,
        )

        val saved = customerRepository.save(contactResult.aggregate)

        val registeredEvent = CiamDomainEvent.CustomerRegistered(
            entityId = saved.customerId.toString(),
            displayName = saved.displayName,
            source = registrationSource,
            registeredAt = saved.createdAt,
        )

        outboxRepository.save(
            OutboxEventEntity().apply {
                eventId = UUID.randomUUID()
                entityId = saved.customerId.toString()
                entityType = "customer"
                eventType = "CustomerRegistered"
                this@apply.source = "ciam"
                this@apply.payload = EventEnvelope(
                    eventType = "CustomerRegistered",
                    source = "ciam",
                    correlationId = null,
                    actorId = actor?.subject,
                    payload = registeredEvent,
                ).toJson()
                this@apply.correlationId = null
                this@apply.actorId = actor?.subject
                this@apply.createdAt = Instant.now()
            }
        )

        return saved
    }

    @Transactional
    fun qualifyLead(customerId: UUID, actor: JwtContext? = null): Customer {
        val customer = customerRepository.findById(customerId)
            ?: throw NotFoundException("Customer", customerId.toString())

        val result = customer.qualifyLead()
        val saved = customerRepository.save(result.aggregate)

        result.events.forEach { event ->
            outboxRepository.save(event.toOutboxEntity("ciam", actor?.subject))
        }

        return saved
    }

    @Transactional
    fun disqualifyLead(
        customerId: UUID,
        reason: DisqualificationReason = DisqualificationReason.MANUAL_DISQUALIFICATION,
        actor: JwtContext? = null,
    ): Customer {
        val customer = customerRepository.findById(customerId)
            ?: throw NotFoundException("Customer", customerId.toString())

        val result = customer.disqualifyLead(reason)
        val saved = customerRepository.save(result.aggregate)

        result.events.forEach { event ->
            outboxRepository.save(event.toOutboxEntity("ciam", actor?.subject))
        }

        return saved
    }

    @Transactional
    fun changeLifecycleStage(
        customerId: UUID,
        newStage: LifecycleStage,
        actor: JwtContext? = null,
    ): Customer {
        val customer = customerRepository.findById(customerId)
            ?: throw NotFoundException("Customer", customerId.toString())

        val result = customer.changeLifecycleStage(newStage)
        val saved = customerRepository.save(result.aggregate)

        result.events.forEach { event ->
            outboxRepository.save(event.toOutboxEntity("ciam", actor?.subject))
        }

        return saved
    }

    @Transactional
    fun updateConsent(
        customerId: UUID,
        purpose: String,
        granted: Boolean,
        actor: JwtContext? = null,
    ): Customer {
        val customer = customerRepository.findById(customerId)
            ?: throw NotFoundException("Customer", customerId.toString())

        val result = customer.updateConsent(purpose, granted)
        val saved = customerRepository.save(result.aggregate)

        result.events.forEach { event ->
            outboxRepository.save(event.toOutboxEntity("ciam", actor?.subject))
        }

        return saved
    }

    @Transactional
    fun deactivateCustomer(
        customerId: UUID,
        reason: String? = null,
        actor: JwtContext? = null,
    ): Customer {
        val customer = customerRepository.findById(customerId)
            ?: throw NotFoundException("Customer", customerId.toString())

        val result = customer.deactivate(reason)
        val saved = customerRepository.save(result.aggregate)

        result.events.forEach { event ->
            outboxRepository.save(event.toOutboxEntity("ciam", actor?.subject))
        }

        return saved
    }

    @Transactional
    fun reactivateCustomer(
        customerId: UUID,
        actor: JwtContext? = null,
    ): Customer {
        val customer = customerRepository.findById(customerId)
            ?: throw NotFoundException("Customer", customerId.toString())

        val result = customer.reactivate()
        val saved = customerRepository.save(result.aggregate)

        result.events.forEach { event ->
            outboxRepository.save(event.toOutboxEntity("ciam", actor?.subject))
        }

        return saved
    }

    @Transactional
    fun verifyEmail(
        customerId: UUID,
        email: String,
        actor: JwtContext? = null,
    ): Customer {
        val customer = customerRepository.findById(customerId)
            ?: throw NotFoundException("Customer", customerId.toString())

        val result = customer.verifyEmail(email)
        val saved = customerRepository.save(result.aggregate)

        result.events.forEach { event ->
            outboxRepository.save(event.toOutboxEntity("ciam", actor?.subject))
        }

        return saved
    }
}

// ── Outbox extension ─────────────────────────────────────────────────────────

/**
 * Convert a domain event into an outbox entity for durable storage.
 */
private fun CiamDomainEvent.toOutboxEntity(source: String, actorId: String?): OutboxEventEntity =
    OutboxEventEntity().apply {
        eventId = UUID.randomUUID()
        entityId = this@toOutboxEntity.entityId
        entityType = "customer"
        eventType = this@toOutboxEntity::class.simpleName!!
        this@apply.source = source
        this@apply.payload = EventEnvelope(
            eventType = this@toOutboxEntity::class.simpleName!!,
            source = source,
            correlationId = null,
            actorId = actorId,
            payload = this@toOutboxEntity,
        ).toJson()
        this@apply.correlationId = null
        this@apply.actorId = actorId
        this@apply.createdAt = Instant.now()
    }

/**
 * Serialize an [EventEnvelope] to JSON for outbox storage.
 */
private fun EventEnvelope<*>.toJson(): String {
    val payloadJson = when (val p = payload) {
        is CiamDomainEvent -> p.toJson()
        else -> p.toString()
    }
    return """{"eventType":"$eventType","source":"$source","actorId":"${actorId ?: ""}","payload":$payloadJson}"""
}

/**
 * Serialize a CIAM domain event to JSON.
 */
private fun CiamDomainEvent.toJson(): String = when (this) {
    is CiamDomainEvent.CustomerRegistered ->
        """{"entityId":"$entityId","displayName":"$displayName","source":"${source ?: ""}","registeredAt":"$registeredAt"}"""
    is CiamDomainEvent.LeadQualified ->
        """{"entityId":"$entityId","previousStage":"$previousStage","qualifiedAt":"$qualifiedAt"}"""
    is CiamDomainEvent.LeadDisqualified ->
        """{"entityId":"$entityId","reason":"$reason","disqualifiedAt":"$disqualifiedAt"}"""
    is CiamDomainEvent.CustomersMerged ->
        """{"survivingCustomerId":"$survivingCustomerId","mergedCustomerId":"$mergedCustomerId","mergedAt":"$mergedAt"}"""
    is CiamDomainEvent.ConsentChanged ->
        """{"entityId":"$entityId","purpose":"$purpose","granted":$granted,"changedAt":"$changedAt"}"""
    is CiamDomainEvent.EmailVerified ->
        """{"entityId":"$entityId","email":"$email","verifiedAt":"$verifiedAt"}"""
    is CiamDomainEvent.CustomerDeactivated ->
        """{"entityId":"$entityId","reason":"${reason ?: ""}","deactivatedAt":"$deactivatedAt"}"""
    is CiamDomainEvent.CustomerReactivated ->
        """{"entityId":"$entityId","reactivatedAt":"$reactivatedAt"}"""
    is CiamDomainEvent.LifecycleStageChanged ->
        """{"entityId":"$entityId","fromStage":"$fromStage","toStage":"$toStage","changedAt":"$changedAt"}"""
}
