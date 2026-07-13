package com.murzify.bambuddyspool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SanitizedContractFixtureTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
        coerceInputValues = false
    }

    @Test
    fun manifestEnumeratesOnlyMandatoryOperations() {
        val manifest = parseResource("manifest.json")
        assertEquals("0.2.4.7", manifest.requiredString("reference_version"))
        assertTrue(manifest.getValue("synthetic").jsonPrimitive.boolean)

        val operations = manifest.getValue("operations").jsonArray
        val actual = operations.associate { operation ->
            val value = operation.jsonObject
            value.requiredString("id") to (value.requiredString("method") to value.requiredString("path"))
        }

        assertEquals(EXPECTED_OPERATIONS, actual)
        operations.forEach { operation ->
            val value = operation.jsonObject
            assertEquals("X-API-Key header", value.requiredString("auth"))
            assertTrue(value.getValue("request") is JsonObject)
            val usedFields = value.getValue("used_fields").jsonArray
            assertTrue(usedFields.isNotEmpty() || value.requiredString("id") == "auth-me")
            usedFields.forEach { field ->
                val contract = field.jsonObject
                assertTrue(contract.requiredString("path").isNotBlank())
                assertTrue(contract.requiredString("type").isNotBlank())
                assertNotNull(contract["nullable"]?.jsonPrimitive?.boolean)
            }
            assertTrue(value.getValue("limits") is JsonObject)
            assertTrue(value.requiredString("fixture").endsWith(".json"))
        }
    }

    @Test
    fun everyOperationHasParseableSyntheticFixtureCategories() {
        val operations = parseResource("manifest.json").getValue("operations").jsonArray

        operations.forEach { operation ->
            val endpoint = operation.jsonObject
            val fixture = parseResource(endpoint.requiredString("fixture"))
            assertEquals(endpoint.requiredString("id"), fixture.requiredString("endpoint_id"))
            assertTrue(fixture.getValue("synthetic").jsonPrimitive.boolean)
            assertTrue(fixture.requiredString("provenance").contains("0.2.4.7"))

            val categories = fixture.getValue("categories").jsonObject
            assertEquals(REQUIRED_CATEGORIES, categories.keys)
            categories.values.forEach { category ->
                assertNotNull(category.jsonObject["payload"])
            }
            assertEquals(
                "IncompatibleApiResponse",
                categories.getValue("incompatible").jsonObject.requiredString("expected_result")
            )
            assertTrue(categories.getValue("representative_valid").containsKeyRecursively("synthetic_additive_field"))
        }
    }

    @Test
    fun fixturesContainNoPrivateConnectionOrDeviceMaterial() {
        val manifest = parseResource("manifest.json")
        manifest.getValue("operations").jsonArray.forEach { operation ->
            val fixtureName = operation.jsonObject.requiredString("fixture")
            val fixtureText = resourceText(fixtureName)
            val fixture = json.parseToJsonElement(fixtureText)
            val categoriesText = fixture.jsonObject.getValue("categories").toString()

            assertFalse(URL.containsMatchIn(categoriesText), fixtureName)
            assertFalse(IPV4.containsMatchIn(categoriesText), fixtureName)
            assertFalse(fixture.containsAnyKey(FORBIDDEN_KEYS), fixtureName)
        }
    }

    private fun parseResource(name: String): JsonObject = json.parseToJsonElement(resourceText(name)).jsonObject

    private fun resourceText(name: String): String =
        checkNotNull(javaClass.getResource("$RESOURCE_ROOT/$name")) { "Missing contract resource: $name" }.readText()

    private fun JsonObject.requiredString(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonElement.containsKeyRecursively(key: String): Boolean = when (this) {
        is JsonObject -> key in keys || values.any { it.containsKeyRecursively(key) }
        is JsonArray -> any { it.containsKeyRecursively(key) }
        else -> false
    }

    private fun JsonElement.containsAnyKey(keys: Set<String>): Boolean = when (this) {
        is JsonObject -> this.keys.any { it.lowercase() in keys } || values.any { it.containsAnyKey(keys) }
        is JsonArray -> any { it.containsAnyKey(keys) }
        else -> false
    }

    private companion object {
        const val RESOURCE_ROOT = "/contracts/bambuddy/0.2.4.7"
        val REQUIRED_CATEGORIES = setOf("minimal_valid", "representative_valid", "incompatible")
        val FORBIDDEN_KEYS = setOf(
            "access_code",
            "api_key",
            "ip_address",
            "serial_number",
            "tag_uid",
            "token",
            "tray_uuid"
        )
        val URL = Regex("https?://", RegexOption.IGNORE_CASE)
        val IPV4 = Regex("(?:^|[^0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?:[^0-9]|$)")
        val EXPECTED_OPERATIONS = mapOf(
            "auth-me" to ("GET" to "/api/v1/auth/me"),
            "printers-list" to ("GET" to "/api/v1/printers/"),
            "printer-status" to ("GET" to "/api/v1/printers/{printer_id}/status"),
            "spools-list" to ("GET" to "/api/v1/inventory/spools?include_archived=true"),
            "spool-detail" to ("GET" to "/api/v1/inventory/spools/{spool_id}"),
            "assignments-list" to ("GET" to "/api/v1/inventory/assignments"),
            "assignments-by-printer" to ("GET" to "/api/v1/inventory/assignments?printer_id=<id>"),
            "assignment-create" to ("POST" to "/api/v1/inventory/assignments")
        )
    }
}
