package com.murzify.bambuddyspool.core.settings

/**
 * Canonical DataStore field contract for the nonsecret connection settings.
 *
 * The adapter that owns actual DataStore I/O must use these names and must not add token fields.
 */
object ConnectionSettingsDataStoreFields {
    const val CANONICAL_BASE_URL: String = "canonical_base_url"
    const val CONFIGURED_ORIGIN_SCHEME: String = "configured_origin_scheme"
    const val CONFIGURED_ORIGIN_HOST: String = "configured_origin_host"
    const val CONFIGURED_ORIGIN_EFFECTIVE_PORT: String = "configured_origin_effective_port"
    const val DEFAULT_PRINTER_ID: String = "default_printer_id"
    const val HTTP_CONSENT_ORIGIN: String = "http_consent_origin"
    const val TLS_OVERRIDE_HOSTNAME: String = "tls_override_hostname"
    const val SCHEMA_VERSION: String = "settings_schema_version"

    val all: Set<String> = setOf(
        CANONICAL_BASE_URL,
        CONFIGURED_ORIGIN_SCHEME,
        CONFIGURED_ORIGIN_HOST,
        CONFIGURED_ORIGIN_EFFECTIVE_PORT,
        DEFAULT_PRINTER_ID,
        HTTP_CONSENT_ORIGIN,
        TLS_OVERRIDE_HOSTNAME,
        SCHEMA_VERSION
    )
}
