package com.crm.marketing.infrastructure.messaging

import com.crm.test.CrmIntegrationTestResourceLifecycleManager

class MarketingIntegrationTestResource : CrmIntegrationTestResourceLifecycleManager(
    schema = "marketing"
)
