package com.murzify.bambuddyspool.core.settings

import com.murzify.bambuddyspool.core.security.SecretValue
import com.murzify.bambuddyspool.core.security.SecureTokenStore

class ConnectionReplacementService(
    private val settingsStore: ConnectionSettingsStore,
    private val tokenStore: SecureTokenStore,
    private val validator: ConnectionValidator,
    private val cacheMaintenance: ConnectionCacheMaintenance,
    private val initialSync: InitialConnectionSync
) {

    @Suppress("ReturnCount")
    suspend fun replaceConnection(
        rawBaseUrl: String,
        token: SecretValue,
        acknowledgements: ConnectionReplacementAcknowledgements
    ): ConnectionReplacementResult {
        val newBaseUrl = when (val parsed = parseCanonicalBaseUrl(rawBaseUrl)) {
            is BaseUrlParseResult.Success -> parsed.value
            is BaseUrlParseResult.Failure -> return ConnectionReplacementResult.InvalidBaseUrl(parsed.reason)
        }

        val activeSettings = settingsStore.read()
        if (activeSettings.baseUrl != null &&
            activeSettings.baseUrl != newBaseUrl &&
            !acknowledgements.acceptedInstanceChangeWarning
        ) {
            return ConnectionReplacementResult.InstanceChangeWarningRequired
        }

        val httpConsentOrigin = if (newBaseUrl.scheme == UrlScheme.Http) {
            httpConsentForReplacement(
                activeSettings = activeSettings,
                newBaseUrl = newBaseUrl,
                acknowledgement = acknowledgements.acceptedHttpWarning
            ) ?: return ConnectionReplacementResult.HttpWarningRequired(newBaseUrl.origin)
        } else {
            null
        }

        val validation = validator.validateConnection(newBaseUrl, token)
        if (validation is ConnectionValidationResult.Failure) {
            return ConnectionReplacementResult.ValidationFailed(validation.reason)
        }

        tokenStore.replaceToken(token)
        settingsStore.replace(
            activeSettings.forConnectionReplacement(
                newBaseUrl = newBaseUrl,
                httpConsentOrigin = httpConsentOrigin
            )
        )
        cacheMaintenance.clearDomainSnapshot()
        initialSync.requestInitialSync()
        return ConnectionReplacementResult.Replaced
    }

    @Suppress("ReturnCount")
    suspend fun replaceToken(token: SecretValue): TokenReplacementResult {
        val activeBaseUrl = settingsStore.read().baseUrl ?: return TokenReplacementResult.NoActiveConnection
        val validation = validator.validateToken(activeBaseUrl, token)
        if (validation is ConnectionValidationResult.Failure) {
            return TokenReplacementResult.ValidationFailed(validation.reason)
        }

        tokenStore.replaceToken(token)
        return TokenReplacementResult.Replaced
    }
}

data class ConnectionReplacementAcknowledgements(
    val acceptedInstanceChangeWarning: Boolean,
    val acceptedHttpWarning: Boolean
) {
    companion object {
        val None: ConnectionReplacementAcknowledgements = ConnectionReplacementAcknowledgements(
            acceptedInstanceChangeWarning = false,
            acceptedHttpWarning = false
        )
    }
}

interface ConnectionValidator {
    suspend fun validateConnection(baseUrl: CanonicalBaseUrl, token: SecretValue): ConnectionValidationResult
    suspend fun validateToken(activeBaseUrl: CanonicalBaseUrl, token: SecretValue): ConnectionValidationResult
}

sealed interface ConnectionValidationResult {
    data object Valid : ConnectionValidationResult
    data class Failure(val reason: ConnectionValidationFailureReason) : ConnectionValidationResult
}

enum class ConnectionValidationFailureReason {
    Unreachable,
    AuthenticationRejected,
    IncompatibleResponse,
    TlsValidationFailed
}

interface ConnectionCacheMaintenance {
    suspend fun clearDomainSnapshot()
}

interface InitialConnectionSync {
    suspend fun requestInitialSync()
}

sealed interface ConnectionReplacementResult {
    data object Replaced : ConnectionReplacementResult
    data class InvalidBaseUrl(val reason: BaseUrlParseFailureReason) : ConnectionReplacementResult
    data object InstanceChangeWarningRequired : ConnectionReplacementResult
    data class HttpWarningRequired(val origin: UrlOrigin) : ConnectionReplacementResult
    data class ValidationFailed(val reason: ConnectionValidationFailureReason) : ConnectionReplacementResult
}

sealed interface TokenReplacementResult {
    data object Replaced : TokenReplacementResult
    data object NoActiveConnection : TokenReplacementResult
    data class ValidationFailed(val reason: ConnectionValidationFailureReason) : TokenReplacementResult
}

private fun ConnectionSettings.forConnectionReplacement(
    newBaseUrl: CanonicalBaseUrl,
    httpConsentOrigin: UrlOrigin?
): ConnectionSettings = copy(
    baseUrl = newBaseUrl,
    defaultPrinterId = null,
    httpConsentOrigin = httpConsentOrigin,
    tlsOverrideHostname = tlsOverrideHostname
        ?.takeIf { newBaseUrl.scheme == UrlScheme.Https && it == newBaseUrl.host },
    schemaVersion = CURRENT_SETTINGS_SCHEMA_VERSION
)

private fun httpConsentForReplacement(
    activeSettings: ConnectionSettings,
    newBaseUrl: CanonicalBaseUrl,
    acknowledgement: Boolean
): UrlOrigin? {
    if (activeSettings.httpConsentOrigin == newBaseUrl.origin) return newBaseUrl.origin
    return newBaseUrl.origin.takeIf { acknowledgement }
}
