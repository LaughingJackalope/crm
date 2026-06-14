package com.crm.sales.domain.event

import com.crm.common.messaging.Identifiable
import java.time.Instant
import java.util.UUID

data class OpportunityCreated(
    override val entityId: String,
    val customerId: String,
    val name: String,
    val amount: String,
    val createdAt: Instant,
) : Identifiable

data class OpportunityStageAdvanced(
    override val entityId: String,
    val fromStage: String,
    val toStage: String,
    val advancedAt: Instant,
) : Identifiable

data class OpportunityClosed(
    override val entityId: String,
    val isWon: Boolean,
    val reason: String?,
    val closedAt: Instant,
) : Identifiable

data class QuoteGenerated(
    override val entityId: String,
    val opportunityId: String,
    val totalAmount: String,
    val generatedAt: Instant,
) : Identifiable

data class QuoteSent(
    override val entityId: String,
    val opportunityId: String,
    val sentAt: Instant,
) : Identifiable

data class QuoteAccepted(
    override val entityId: String,
    val opportunityId: String,
    val acceptedAt: Instant,
) : Identifiable

data class ForecastUpdated(
    override val entityId: String,
    val period: String,
    val projectedRevenue: String,
    val updatedAt: Instant,
) : Identifiable

data class OwnerReassigned(
    override val entityId: String,
    val previousOwnerId: String?,
    val newOwnerId: String,
    val reassignedAt: Instant,
) : Identifiable
