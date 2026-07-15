package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.settings.CanonicalBaseUrl
import com.murzify.bambuddyspool.core.settings.ConnectionSettings
import com.murzify.bambuddyspool.core.settings.UrlOrigin
import com.murzify.bambuddyspool.core.settings.UrlScheme
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom

/**
 * The authoritative network trust boundary. UI may describe its decisions but must never replace them.
 */
interface BambuddyNetworkSecurityPolicy {
    suspend fun validateInitialRequest(baseUrl: CanonicalBaseUrl, url: String): BambuddySecurityDecision

    suspend fun validateRedirect(
        baseUrl: CanonicalBaseUrl,
        fromUrl: String,
        toUrl: String,
        redirectCount: Int
    ): BambuddySecurityDecision

    /** True only for a certificate-validation retry on the configured HTTPS host. */
    fun permitsCertificateBypass(baseUrl: CanonicalBaseUrl, requestUrl: String): Boolean
}

sealed interface BambuddySecurityDecision {
    data object Allow : BambuddySecurityDecision
    data class Deny(val reason: SecurityPolicyFailureReason) : BambuddySecurityDecision
}

/**
 * Strict policy backed by persisted, non-secret connection settings.
 *
 * It deliberately has no permissive fallback: HTTP requires an exact origin acknowledgement and TLS exceptions are
 * constrained to the configured HTTPS hostname. Platform clients keep ordinary trust and hostname verification
 * enabled; a future certificate retry must call [permitsCertificateBypass] rather than installing global trust-all.
 */
class ConfiguredBambuddyNetworkSecurityPolicy(
    private val settings: ConnectionSettings
) : BambuddyNetworkSecurityPolicy {
    @Suppress("ReturnCount")
    override suspend fun validateInitialRequest(baseUrl: CanonicalBaseUrl, url: String): BambuddySecurityDecision {
        val target = parseTarget(url) ?: return denyInitial()
        if (target.origin != baseUrl.origin || !target.pathIsWithin(baseUrl)) return denyInitial()
        return allowOrigin(target.origin)
    }

    @Suppress("ReturnCount")
    override suspend fun validateRedirect(
        baseUrl: CanonicalBaseUrl,
        fromUrl: String,
        toUrl: String,
        redirectCount: Int
    ): BambuddySecurityDecision {
        if (redirectCount !in 1..MAX_REDIRECTS) return denyRedirect()
        val from = parseTarget(fromUrl) ?: return denyRedirect()
        val target = parseTarget(toUrl) ?: return denyRedirect()
        if (from.origin.host != baseUrl.host || target.origin.host != baseUrl.host) return denyRedirect()

        val sameOrigin = target.origin == from.origin
        val sameHostUpgrade = from.origin.scheme == UrlScheme.Http && target.origin.scheme == UrlScheme.Https
        if (!sameOrigin && !sameHostUpgrade) return denyRedirect()
        return allowOrigin(target.origin, redirect = true)
    }

    override fun permitsCertificateBypass(baseUrl: CanonicalBaseUrl, requestUrl: String): Boolean {
        val target = parseTarget(requestUrl) ?: return false
        return baseUrl.scheme == UrlScheme.Https &&
            target.origin.scheme == UrlScheme.Https &&
            target.origin.host == baseUrl.host &&
            settings.tlsOverrideHostname == baseUrl.host
    }

    private fun allowOrigin(origin: UrlOrigin, redirect: Boolean = false): BambuddySecurityDecision {
        if (origin.scheme == UrlScheme.Http && settings.httpConsentOrigin != origin) {
            return if (redirect) denyRedirect() else denyInitial()
        }
        return BambuddySecurityDecision.Allow
    }

    private fun denyInitial() = BambuddySecurityDecision.Deny(SecurityPolicyFailureReason.InitialRequestDenied)

    private fun denyRedirect() = BambuddySecurityDecision.Deny(SecurityPolicyFailureReason.RedirectDenied)
}

/** The bootstrap policy is deliberately fail-closed until persisted settings are available. */
object DenyingBambuddyNetworkSecurityPolicy : BambuddyNetworkSecurityPolicy {
    override suspend fun validateInitialRequest(baseUrl: CanonicalBaseUrl, url: String): BambuddySecurityDecision =
        BambuddySecurityDecision.Deny(SecurityPolicyFailureReason.InitialRequestDenied)

    override suspend fun validateRedirect(
        baseUrl: CanonicalBaseUrl,
        fromUrl: String,
        toUrl: String,
        redirectCount: Int
    ): BambuddySecurityDecision = BambuddySecurityDecision.Deny(SecurityPolicyFailureReason.RedirectDenied)

    override fun permitsCertificateBypass(baseUrl: CanonicalBaseUrl, requestUrl: String): Boolean = false
}

@Suppress("ComplexCondition")
private fun parseNetworkTarget(value: String): NetworkTarget? = try {
    val url = URLBuilder().takeFrom(value).build()
    val scheme = UrlScheme.parse(url.protocol.name) ?: return null
    if (
        url.host.isBlank() ||
        !url.user.isNullOrEmpty() ||
        !url.password.isNullOrEmpty() ||
        !url.fragment.isNullOrEmpty()
    ) return null
    NetworkTarget(UrlOrigin(scheme, url.host.lowercase(), url.port), url.encodedPath)
} catch (_: IllegalArgumentException) {
    null
}

private data class NetworkTarget(val origin: UrlOrigin, val encodedPath: String) {
    fun pathIsWithin(baseUrl: CanonicalBaseUrl): Boolean = baseUrl.basePath.isEmpty() ||
        encodedPath == baseUrl.basePath || encodedPath.startsWith("${baseUrl.basePath}/")
}

private fun parseTarget(value: String): NetworkTarget? = parseNetworkTarget(value)

private const val MAX_REDIRECTS = 5
