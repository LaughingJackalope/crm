package com.crm.sales.infrastructure.messaging

import com.crm.sales.application.SalesEventPublisher
import com.crm.sales.domain.event.SalesDomainEvent
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

/**
 * Publishes Sales domain events.
 *
 * In production, this would write to the transactional outbox (same pattern
 * as [com.crm.ciam.infrastructure.messaging.OutboxRelay]).
 * For now, thisLogs the event. The outbox pattern will be added in
 * the next phase.
 */
@ApplicationScoped
class KafkaSalesEventPublisher : SalesEventPublisher {

    private val log = Logger.getLogger(KafkaSalesEventPublisher::class.java)

    override fun publish(event: SalesDomainEvent) {
        log.infof("Publishing sales event: type=%s, entity=%s",
            event::class.simpleName, event.entityId)
        // TODO: Write to outbox table (same pattern as CIAM outbox)
    }
}
