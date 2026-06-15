package com.crm.communication.application

import com.crm.communication.domain.event.NotificationDomainEvent
import com.crm.communication.domain.event.NotificationDomainEvent.*
import com.crm.communication.domain.notification.Notification
import com.crm.communication.domain.notification.NotificationChannel
import com.crm.communication.infrastructure.persistence.NotificationRepository
import com.crm.communication.infrastructure.persistence.OutboxEventEntity
import com.crm.communication.infrastructure.persistence.OutboxEventRepository
import com.crm.communication.infrastructure.persistence.toJson
import com.crm.common.messaging.EventEnvelope
import io.quarkus.qute.Engine
import io.quarkus.qute.Template
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.time.Instant
import org.jboss.logging.Logger
import java.util.UUID

@ApplicationScoped
class NotificationOrchestrationService @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val outboxRepository: OutboxEventRepository,
    private val quteEngine: Engine,
) {
    private val log = Logger.getLogger(NotificationOrchestrationService::class.java)

    // ── CIAM Event Handlers ───────────────────────────────────────────────────

    @Transactional
    fun handleCustomerRegistered(recipientId: String, email: String?, displayName: String?, source: String?) {
        if (email.isNullOrBlank()) return
        val vars = mapOf("displayName" to (displayName ?: "Valued Customer"), "source" to (source ?: "unknown"))
        val body = renderTemplate("welcome", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "welcome", subject = "Welcome to CRM Platform!",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleCustomerDeactivated(recipientId: String, reason: String?) {
        val vars = mapOf("reason" to (reason ?: "No reason provided"))
        val body = renderTemplate("account_deactivated", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "account_deactivated", subject = "Account Deactivated",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleLifecycleStageChanged(recipientId: String, fromStage: String?, toStage: String?) {
        val vars = mapOf("fromStage" to (fromStage ?: "unknown"), "toStage" to (toStage ?: "unknown"))
        val body = renderTemplate("lifecycle_changed", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "lifecycle_changed", subject = "Your Account Status Has Changed",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleEmailVerified(recipientId: String, email: String?) {
        if (email.isNullOrBlank()) return
        val vars = mapOf("email" to email)
        val body = renderTemplate("email_verified", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "email_verified", subject = "Email Verified Successfully",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleCustomersMerged(survivingContactId: String, mergedContactId: String?) {
        val vars = mapOf("mergedContactId" to (mergedContactId ?: "unknown"))
        val body = renderTemplate("account_merged", vars)
        val notification = Notification(
            recipientId = survivingContactId, channelType = NotificationChannel.EMAIL,
            templateId = "account_merged", subject = "Accounts Merged",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleConsentChanged(recipientId: String, purpose: String?, granted: Boolean?) {
        val vars = mapOf("purpose" to (purpose ?: "unknown"), "granted" to (granted?.toString() ?: "unknown"))
        val body = renderTemplate("consent_changed", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "consent_changed", subject = "Consent Preferences Updated",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    // ── Billing Event Handlers ────────────────────────────────────────────────

    @Transactional
    fun handleInvoiceGenerated(recipientId: String, invoiceId: String?, totalAmount: String?, currency: String?, dueDate: String?) {
        val vars = mapOf("invoiceId" to (invoiceId ?: "N/A"), "totalAmount" to (totalAmount ?: "N/A"),
            "currency" to (currency ?: "USD"), "dueDate" to (dueDate ?: "N/A"))
        val body = renderTemplate("invoice_generated", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "invoice_generated", subject = "New Invoice Available",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handlePaymentSucceeded(recipientId: String, invoiceId: String?, amount: String?) {
        val vars = mapOf("invoiceId" to (invoiceId ?: "N/A"), "amount" to (amount ?: "N/A"))
        val body = renderTemplate("payment_receipt", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "payment_receipt", subject = "Payment Confirmation",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handlePaymentFailed(recipientId: String, invoiceId: String?, reason: String?) {
        val vars = mapOf("invoiceId" to (invoiceId ?: "N/A"), "reason" to (reason ?: "Unknown error"))
        val body = renderTemplate("payment_failed", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "payment_failed", subject = "Payment Failed — Action Required",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleSubscriptionCreated(recipientId: String, subscriptionId: String?, planId: String?) {
        val vars = mapOf("subscriptionId" to (subscriptionId ?: "N/A"), "planId" to (planId ?: "N/A"))
        val body = renderTemplate("subscription_created", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "subscription_created", subject = "Subscription Activated",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleSubscriptionCancelled(recipientId: String, subscriptionId: String?, reason: String?) {
        val vars = mapOf("subscriptionId" to (subscriptionId ?: "N/A"), "reason" to (reason ?: "No reason provided"))
        val body = renderTemplate("subscription_cancelled", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "subscription_cancelled", subject = "Subscription Cancelled",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleDunningEscalated(recipientId: String, invoiceId: String?) {
        val vars = mapOf("invoiceId" to (invoiceId ?: "N/A"))
        val body = renderTemplate("dunning_notice", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "dunning_notice", subject = "Payment Overdue — Immediate Action Required",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    // ── Support Event Handlers ────────────────────────────────────────────────

    @Transactional
    fun handleTicketCreated(recipientId: String, ticketId: String?, subject: String?, priority: String?) {
        val vars = mapOf("ticketId" to (ticketId ?: "N/A"), "ticketSubject" to (subject ?: "N/A"), "priority" to (priority ?: "N/A"))
        val body = renderTemplate("ticket_created", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "ticket_created", subject = "Ticket Created: ${subject ?: "N/A"}",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleTicketAssigned(assigneeId: String, ticketId: String?, queueId: String?) {
        val vars = mapOf("ticketId" to (ticketId ?: "N/A"), "queueId" to (queueId ?: "N/A"))
        val body = renderTemplate("ticket_assigned", vars)
        val notification = Notification(
            recipientId = assigneeId, channelType = NotificationChannel.EMAIL,
            templateId = "ticket_assigned", subject = "New Ticket Assigned",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleTicketStatusChanged(recipientId: String, ticketId: String?, fromStatus: String?, toStatus: String?) {
        val vars = mapOf("ticketId" to (ticketId ?: "N/A"), "fromStatus" to (fromStatus ?: "N/A"), "toStatus" to (toStatus ?: "N/A"))
        val body = renderTemplate("ticket_status_changed", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "ticket_status_changed", subject = "Ticket Status Updated",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleTicketEscalated(recipientId: String, ticketId: String?, escalatedTo: String?, newPriority: String?) {
        val vars = mapOf("ticketId" to (ticketId ?: "N/A"), "escalatedTo" to (escalatedTo ?: "N/A"), "newPriority" to (newPriority ?: "N/A"))
        val body = renderTemplate("ticket_escalated", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "ticket_escalated", subject = "Ticket Escalated",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleTicketResolved(recipientId: String, ticketId: String?, resolution: String?) {
        val vars = mapOf("ticketId" to (ticketId ?: "N/A"), "resolution" to (resolution ?: "N/A"))
        val body = renderTemplate("ticket_resolved", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "ticket_resolved", subject = "Ticket Resolved",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleTicketClosed(recipientId: String, ticketId: String?) {
        val vars = mapOf("ticketId" to (ticketId ?: "N/A"))
        val body = renderTemplate("ticket_closed", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "ticket_closed", subject = "Ticket Closed",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleTicketReopened(recipientId: String, ticketId: String?) {
        val vars = mapOf("ticketId" to (ticketId ?: "N/A"))
        val body = renderTemplate("ticket_reopened", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "ticket_reopened", subject = "Ticket Reopened",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleSlaBreached(ticketId: String, slaDeadline: String?, breachDurationSeconds: Long?) {
        val vars = mapOf("ticketId" to ticketId, "slaDeadline" to (slaDeadline ?: "N/A"),
            "breachDurationSeconds" to (breachDurationSeconds?.toString() ?: "N/A"))
        val body = renderTemplate("sla_breached", vars)
        val notification = Notification(
            recipientId = "support-team", channelType = NotificationChannel.EMAIL,
            templateId = "sla_breached", subject = "SLA Breached: Ticket $ticketId",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleCsatSubmitted(recipientId: String, ticketId: String?, score: Int?) {
        val vars = mapOf("ticketId" to (ticketId ?: "N/A"), "score" to (score?.toString() ?: "N/A"))
        val body = renderTemplate("csat_submitted", vars)
        val notification = Notification(
            recipientId = "support-team", channelType = NotificationChannel.EMAIL,
            templateId = "csat_submitted", subject = "CSAT Received: ${score ?: "N/A"} stars",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    // ── Marketing Event Handlers ──────────────────────────────────────────────

    @Transactional
    fun handleCampaignLaunched(campaignId: String?, name: String?) {
        val vars = mapOf("campaignId" to (campaignId ?: "N/A"), "name" to (name ?: "N/A"))
        val body = renderTemplate("campaign_launched", vars)
        val notification = Notification(
            recipientId = "marketing-team", channelType = NotificationChannel.EMAIL,
            templateId = "campaign_launched", subject = "Campaign Launched: ${name ?: "N/A"}",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleCampaignPaused(campaignId: String?) {
        val vars = mapOf("campaignId" to (campaignId ?: "N/A"))
        val body = renderTemplate("campaign_paused", vars)
        val notification = Notification(
            recipientId = "marketing-team", channelType = NotificationChannel.EMAIL,
            templateId = "campaign_paused", subject = "Campaign Paused",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleCampaignCompleted(campaignId: String?) {
        val vars = mapOf("campaignId" to (campaignId ?: "N/A"))
        val body = renderTemplate("campaign_completed", vars)
        val notification = Notification(
            recipientId = "marketing-team", channelType = NotificationChannel.EMAIL,
            templateId = "campaign_completed", subject = "Campaign Completed",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleRevenueAttributed(campaignId: String?, opportunityId: String?, amount: String?) {
        val vars = mapOf("campaignId" to (campaignId ?: "N/A"), "opportunityId" to (opportunityId ?: "N/A"), "amount" to (amount ?: "N/A"))
        val body = renderTemplate("revenue_attributed", vars)
        val notification = Notification(
            recipientId = "marketing-team", channelType = NotificationChannel.EMAIL,
            templateId = "revenue_attributed", subject = "Revenue Attributed to Campaign",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleConversionDeclared(campaignId: String?, customerId: String?, revenue: String?) {
        val vars = mapOf("campaignId" to (campaignId ?: "N/A"), "customerId" to (customerId ?: "N/A"), "revenue" to (revenue ?: "N/A"))
        val body = renderTemplate("conversion_declared", vars)
        val notification = Notification(
            recipientId = "marketing-team", channelType = NotificationChannel.EMAIL,
            templateId = "conversion_declared", subject = "Conversion Declared",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    // ── Sales Event Handlers ──────────────────────────────────────────────────

    @Transactional
    fun handleOpportunityCreated(recipientId: String, opportunityId: String?, name: String?) {
        val vars = mapOf("opportunityId" to (opportunityId ?: "N/A"), "name" to (name ?: "N/A"))
        val body = renderTemplate("opportunity_created", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "opportunity_created", subject = "New Opportunity: ${name ?: "N/A"}",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleOpportunityClosed(recipientId: String, opportunityId: String?, isWon: Boolean, totalAmount: String?) {
        val templateId = if (isWon) "opportunity_won" else "opportunity_lost"
        val emailSubject = if (isWon) "Opportunity Won!" else "Opportunity Lost"
        val vars = mapOf("opportunityId" to (opportunityId ?: "N/A"), "totalAmount" to (totalAmount ?: "N/A"))
        val body = renderTemplate(templateId, vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = templateId, subject = emailSubject,
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleQuoteGenerated(recipientId: String, opportunityId: String?, totalAmount: String?) {
        val vars = mapOf("opportunityId" to (opportunityId ?: "N/A"), "totalAmount" to (totalAmount ?: "N/A"))
        val body = renderTemplate("quote_generated", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "quote_generated", subject = "Quote Generated",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleQuoteSent(recipientId: String, opportunityId: String?) {
        val vars = mapOf("opportunityId" to (opportunityId ?: "N/A"))
        val body = renderTemplate("quote_sent", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "quote_sent", subject = "Quote Sent for Review",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleQuoteAccepted(recipientId: String, opportunityId: String?) {
        val vars = mapOf("opportunityId" to (opportunityId ?: "N/A"))
        val body = renderTemplate("quote_accepted", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "quote_accepted", subject = "Quote Accepted!",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleForecastUpdated(period: String?, projectedRevenue: String?) {
        val vars = mapOf("period" to (period ?: "N/A"), "projectedRevenue" to (projectedRevenue ?: "N/A"))
        val body = renderTemplate("forecast_updated", vars)
        val notification = Notification(
            recipientId = "sales-team", channelType = NotificationChannel.EMAIL,
            templateId = "forecast_updated", subject = "Forecast Updated: ${period ?: "N/A"}",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    @Transactional
    fun handleOwnerReassigned(recipientId: String?, opportunityId: String?, previousOwnerId: String?) {
        if (recipientId.isNullOrBlank()) return
        val vars = mapOf("opportunityId" to (opportunityId ?: "N/A"), "previousOwnerId" to (previousOwnerId ?: "N/A"))
        val body = renderTemplate("owner_reassigned", vars)
        val notification = Notification(
            recipientId = recipientId, channelType = NotificationChannel.EMAIL,
            templateId = "owner_reassigned", subject = "Opportunity Reassigned to You",
            body = body, templateVariables = vars,
        )
        saveAndQueue(notification)
    }

    // ── Qute Templating ───────────────────────────────────────────────────────

    private fun renderTemplate(templateId: String, variables: Map<String, String>): String {
        return try {
            val template: Template = quteEngine.getTemplate("notifications/$templateId")
                ?: return renderFallback(templateId, variables)
            template.data(variables).render()
        } catch (ex: Exception) {
            log.warnf(ex, "Template render failed for '%s', using fallback", templateId)
            renderFallback(templateId, variables)
        }
    }

    private fun renderFallback(templateId: String, variables: Map<String, String>): String {
        val sb = StringBuilder()
        sb.appendLine("Notification: $templateId")
        variables.forEach { (key, value) -> sb.appendLine("$key: $value") }
        return sb.toString()
    }

    private fun saveAndQueue(notification: Notification): Notification {
        val saved = notificationRepository.save(notification)
        saveOutbox(NotificationQueued(
            entityId = saved.notificationId.toString(), notificationId = saved.notificationId,
            recipientId = saved.recipientId, channelType = saved.channelType.name,
            templateId = saved.templateId, queuedAt = saved.createdAt,
        ))
        return saved
    }

    private fun saveOutbox(event: NotificationDomainEvent) {
        outboxRepository.save(OutboxEventEntity().apply {
            eventId = UUID.randomUUID(); entityId = event.entityId
            entityType = "notification"; eventType = event::class.simpleName!!
            source = "communication"
            this@apply.payload = EventEnvelope(
                eventType = event::class.simpleName!!, source = "communication",
                correlationId = null, actorId = null, payload = event,
            ).toJson()
            this@apply.createdAt = Instant.now()
        })
    }
}
