package com.enginesense.app.dealerreport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DealerReportBackendConfigTest {

    @Test
    fun `remote is enabled now that the backend is deployed and verified`() {
        assertTrue(DealerReportBackendConfig.REMOTE_ENABLED)
    }

    @Test
    fun `the shipped endpoint URL is a real, valid-looking URL`() {
        assertTrue(DealerReportBackendConfig.isValidEndpoint(DealerReportBackendConfig.ENDPOINT_URL))
    }

    @Test
    fun `isConfigured is true as shipped`() {
        assertTrue(DealerReportBackendConfig.isConfigured)
    }

    @Test
    fun `a blank URL is never valid`() {
        assertFalse(DealerReportBackendConfig.isValidEndpoint(""))
        assertFalse(DealerReportBackendConfig.isValidEndpoint("   "))
    }

    @Test
    fun `placeholder-shaped URLs are rejected even if they look like real URLs`() {
        assertFalse(DealerReportBackendConfig.isValidEndpoint("https://REPLACE_WITH_REAL_ENDPOINT.example.com"))
        assertFalse(DealerReportBackendConfig.isValidEndpoint("https://your-project.supabase.co/functions/v1/YOUR_FUNCTION"))
    }

    @Test
    fun `a real-looking URL with no placeholder markers is accepted by isValidEndpoint`() {
        assertTrue(DealerReportBackendConfig.isValidEndpoint("https://xofkkvhkkbtcayibjaul.supabase.co/functions/v1/reports"))
    }
}
