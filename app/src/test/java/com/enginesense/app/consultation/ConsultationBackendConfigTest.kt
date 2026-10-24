package com.enginesense.app.consultation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultationBackendConfigTest {

    @Test
    fun `remote is enabled now that the backend is deployed and verified`() {
        assertTrue(ConsultationBackendConfig.REMOTE_ENABLED)
    }

    @Test
    fun `the shipped endpoint URL is a real, valid-looking URL`() {
        assertTrue(ConsultationBackendConfig.isValidEndpoint(ConsultationBackendConfig.ENDPOINT_URL))
    }

    @Test
    fun `isConfigured is true as shipped`() {
        assertTrue(ConsultationBackendConfig.isConfigured)
    }

    @Test
    fun `a blank URL is never valid`() {
        assertFalse(ConsultationBackendConfig.isValidEndpoint(""))
        assertFalse(ConsultationBackendConfig.isValidEndpoint("   "))
    }

    @Test
    fun `placeholder-shaped URLs are rejected even if they look like real URLs`() {
        assertFalse(ConsultationBackendConfig.isValidEndpoint("https://REPLACE_WITH_REAL_ENDPOINT.example.com"))
        assertFalse(ConsultationBackendConfig.isValidEndpoint("https://your-project.supabase.co/functions/v1/YOUR_FUNCTION"))
    }

    @Test
    fun `a real-looking URL with no placeholder markers is accepted by isValidEndpoint`() {
        assertTrue(ConsultationBackendConfig.isValidEndpoint("https://xofkkvhkkbtcayibjaul.supabase.co/functions/v1/consultations"))
    }
}
