package com.crm.sales.infrastructure.messaging

import com.crm.test.CrmIntegrationTestResourceLifecycleManager

class SalesIntegrationTestResource : CrmIntegrationTestResourceLifecycleManager(
    schema = "sales"
)
