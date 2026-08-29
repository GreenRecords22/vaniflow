package com.vaniflow.app.engine

import com.vaniflow.app.engine.ai.DefaultCloudAIProvider
import com.vaniflow.app.engine.ai.ProviderConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAuditTest {

    @Test
    fun testCloudProviderDisabledByDefault() {
        val provider = DefaultCloudAIProvider()
        assertFalse("Cloud provider must be strictly disabled by default for MVP local-first privacy", provider.isAvailable())
        assertFalse(provider.config.isEnabled)
    }

    @Test
    fun testProviderConfigRequiresExplicitOptIn() {
        val config = ProviderConfig(
            providerId = "test",
            providerName = "Test",
            isEnabled = false
        )
        assertFalse(config.isEnabled)
        assertTrue(config.dailyRequestBudget > 0)
    }
}
