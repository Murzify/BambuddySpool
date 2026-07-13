package com.murzify.bambuddyspool.core.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SecretValueTest {

    @Test
    fun secretToStringDoesNotRevealToken() {
        val secret = assertNotNull(SecretValue.fromPlainText("super-secret-token"))

        assertFalse(secret.toString().contains("super-secret-token"))
    }

    @Test
    fun blankTokenIsRejectedBeforeStorage() {
        assertNull(SecretValue.fromPlainText(""))
        assertNull(SecretValue.fromPlainText("   "))
    }

    @Test
    fun serializableSettingsStateDoesNotContainToken() {
        val json = Json.encodeToString(SafeNavigationState(baseUrl = "https://bambuddy.local"))

        assertFalse(json.contains("super-secret-token"))
        assertFalse(json.contains("token"))
    }

    @Serializable
    private data class SafeNavigationState(val baseUrl: String)
}
