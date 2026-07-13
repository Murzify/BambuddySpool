package com.murzify.bambuddyspool.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConnectionSettingsDataStoreFieldsTest {

    @Test
    fun datastoreFieldContractContainsOnlyNonsecretSettings() {
        assertEquals(
            setOf(
                "canonical_base_url",
                "configured_origin_scheme",
                "configured_origin_host",
                "configured_origin_effective_port",
                "default_printer_id",
                "http_consent_origin",
                "tls_override_hostname",
                "settings_schema_version"
            ),
            ConnectionSettingsDataStoreFields.all
        )
        assertFalse(ConnectionSettingsDataStoreFields.all.any { it.contains("token", ignoreCase = true) })
        assertFalse(ConnectionSettingsDataStoreFields.all.any { it.contains("secret", ignoreCase = true) })
    }
}
