package com.crm.billing.infrastructure.messaging

import com.crm.test.CrmIntegrationTestResourceLifecycleManager

/**
 * Thin lifecycle manager for billing-service integration tests.
 * Extends the shared [CrmIntegrationTestResourceLifecycleManager] with the
 * billing-specific schema name.
 */
class BillingIntegrationTestResource : CrmIntegrationTestResourceLifecycleManager(
    schema = "billing"
)
