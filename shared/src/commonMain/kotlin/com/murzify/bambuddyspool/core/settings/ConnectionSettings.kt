package com.murzify.bambuddyspool.core.settings

import com.murzify.bambuddyspool.core.domain.PrinterId

const val CURRENT_SETTINGS_SCHEMA_VERSION: Int = 1

data class ConnectionSettings(
    val baseUrl: CanonicalBaseUrl?,
    val defaultPrinterId: PrinterId?,
    val httpConsentOrigin: UrlOrigin?,
    val tlsOverrideHostname: String?,
    val schemaVersion: Int = CURRENT_SETTINGS_SCHEMA_VERSION
) {
    init {
        require(schemaVersion >= CURRENT_SETTINGS_SCHEMA_VERSION) { "Unsupported settings schema version." }
        require(httpConsentOrigin == null || httpConsentOrigin.scheme == UrlScheme.Http) {
            "HTTP consent must be scoped to an HTTP origin."
        }
        require(tlsOverrideHostname == null || tlsOverrideHostname.isNotBlank()) {
            "TLS override hostname must not be blank."
        }
    }

    val configuredOrigin: UrlOrigin? = baseUrl?.origin

    companion object {
        val Empty: ConnectionSettings = ConnectionSettings(
            baseUrl = null,
            defaultPrinterId = null,
            httpConsentOrigin = null,
            tlsOverrideHostname = null
        )
    }
}

interface ConnectionSettingsStore {
    suspend fun read(): ConnectionSettings
    suspend fun replace(settings: ConnectionSettings)
}
