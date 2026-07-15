package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.settings.BaseUrlParseResult
import com.murzify.bambuddyspool.core.settings.CanonicalBaseUrl
import com.murzify.bambuddyspool.core.settings.ConnectionSettings
import com.murzify.bambuddyspool.core.settings.parseCanonicalBaseUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ConfiguredBambuddyNetworkSecurityPolicyTest {
    @Test
    fun initialRequestRequiresConfiguredOriginBasePathAndHttpConsent() = runTest {
        val base = canonical("http://printer.example:8080/bambuddy")
        val denied = policy(base, httpConsent = null)
        assertDenied(
            denied.validateInitialRequest(base, "http://printer.example:8080/bambuddy/api/v1/auth/me")
        )
        assertDenied(
            denied.validateInitialRequest(base, "http://printer.example:8080/other/api/v1/auth/me")
        )
        assertDenied(
            denied.validateInitialRequest(base, "http://other.example:8080/bambuddy/api/v1/auth/me")
        )

        val allowed = policy(base, httpConsent = base.origin)
        assertEquals(
            BambuddySecurityDecision.Allow,
            allowed.validateInitialRequest(base, "http://printer.example:8080/bambuddy/api/v1/auth/me")
        )
    }

    @Test
    fun redirectsAllowOnlySameOriginOrSameHostHttpUpgradeWithinLimit() = runTest {
        val base = canonical("http://printer.example:8080/proxy")
        val policy = policy(base, httpConsent = base.origin)
        assertEquals(
            BambuddySecurityDecision.Allow,
            policy.validateRedirect(
                base,
                "http://printer.example:8080/proxy/a",
                "https://printer.example:8443/proxy/b",
                1
            )
        )
        assertDenied(
            policy.validateRedirect(base, "https://printer.example/proxy/a", "http://printer.example/proxy/b", 1)
        )
        assertDenied(
            policy.validateRedirect(base, "http://printer.example/proxy/a", "https://evil.example/proxy/b", 1)
        )
        assertDenied(
            policy.validateRedirect(base, "http://printer.example/proxy/a", "http://127.0.0.1/proxy/b", 1)
        )
        assertDenied(
            policy.validateRedirect(base, "http://printer.example/proxy/a", "http://printer.example/proxy/b", 6)
        )
    }

    @Test
    fun tlsBypassIsExactHttpsConfiguredHostOnlyAndReversible() {
        val base = canonical("https://printer.example:8443/proxy")
        val enabled = policy(base, tlsHost = "printer.example")
        assertTrue(enabled.permitsCertificateBypass(base, "https://printer.example:8443/proxy/api"))
        assertFalse(enabled.permitsCertificateBypass(base, "https://sub.printer.example/proxy/api"))
        assertFalse(enabled.permitsCertificateBypass(base, "http://printer.example:8443/proxy/api"))

        val disabled = policy(base, tlsHost = null)
        assertFalse(disabled.permitsCertificateBypass(base, "https://printer.example:8443/proxy/api"))
    }

    private fun policy(
        base: CanonicalBaseUrl,
        httpConsent: com.murzify.bambuddyspool.core.settings.UrlOrigin? = null,
        tlsHost: String? = null
    ) = ConfiguredBambuddyNetworkSecurityPolicy(
        ConnectionSettings(
            base,
            defaultPrinterId = null,
            httpConsentOrigin = httpConsent,
            tlsOverrideHostname = tlsHost
        )
    )

    private fun assertDenied(decision: BambuddySecurityDecision) {
        assertIs<BambuddySecurityDecision.Deny>(decision)
    }

    private fun canonical(raw: String): CanonicalBaseUrl = when (val result = parseCanonicalBaseUrl(raw)) {
        is BaseUrlParseResult.Success -> result.value
        is BaseUrlParseResult.Failure -> error("Invalid test URL: $raw")
    }
}
