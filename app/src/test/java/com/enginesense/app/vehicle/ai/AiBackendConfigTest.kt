package com.enginesense.app.vehicle.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBackendConfigTest {

    @Test
    fun `remote is enabled now that the backend is deployed and verified`() {
        assertTrue(AiBackendConfig.REMOTE_ENABLED)
    }

    @Test
    fun `the shipped endpoint URL is a real, valid-looking URL`() {
        assertTrue(AiBackendConfig.isValidEndpoint(AiBackendConfig.ENDPOINT_URL))
    }

    @Test
    fun `isConfigured is true as shipped`() {
        assertTrue(AiBackendConfig.isConfigured)
    }

    @Test
    fun `a blank URL is never valid`() {
        assertFalse(AiBackendConfig.isValidEndpoint(""))
        assertFalse(AiBackendConfig.isValidEndpoint("   "))
    }

    @Test
    fun `placeholder-shaped URLs are rejected even if they look like real URLs`() {
        assertFalse(AiBackendConfig.isValidEndpoint("https://REPLACE_WITH_REAL_ENDPOINT.example.com"))
        assertFalse(AiBackendConfig.isValidEndpoint("https://your-project.supabase.co/functions/v1/YOUR_FUNCTION"))
        assertFalse(AiBackendConfig.isValidEndpoint("https://api.example.com/ask"))
        assertFalse(AiBackendConfig.isValidEndpoint("TODO: set this"))
    }

    @Test
    fun `a real-looking URL with no placeholder markers is accepted by isValidEndpoint`() {
        assertTrue(AiBackendConfig.isValidEndpoint("https://getautosure-ai.fly.dev/ask"))
    }
}
