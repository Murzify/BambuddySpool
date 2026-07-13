package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.settings.CanonicalBaseUrl

interface BambuddyNetworkSecurityPolicy {
    suspend fun validateInitialRequest(baseUrl: CanonicalBaseUrl, url: String): BambuddySecurityDecision

    suspend fun validateRedirect(
        baseUrl: CanonicalBaseUrl,
        fromUrl: String,
        toUrl: String,
        redirectCount: Int
    ): BambuddySecurityDecision
}

sealed interface BambuddySecurityDecision {
    data object Allow : BambuddySecurityDecision
    data class Deny(val reason: SecurityPolicyFailureReason) : BambuddySecurityDecision
}

object AllowingBambuddyNetworkSecurityPolicy : BambuddyNetworkSecurityPolicy {
    override suspend fun validateInitialRequest(baseUrl: CanonicalBaseUrl, url: String): BambuddySecurityDecision =
        BambuddySecurityDecision.Allow

    override suspend fun validateRedirect(
        baseUrl: CanonicalBaseUrl,
        fromUrl: String,
        toUrl: String,
        redirectCount: Int
    ): BambuddySecurityDecision = BambuddySecurityDecision.Deny(SecurityPolicyFailureReason.RedirectDenied)
}
