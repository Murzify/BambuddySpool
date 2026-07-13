package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.domain.IncompatibleApiResponse
import kotlin.test.Test
import kotlin.test.assertIs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BambuddyContractFixtureMappingTest {

    @Test
    fun allContractFixturesMapOrFailPredictably() {
        val manifest = parseResource("manifest.json").jsonObject
        manifest.getValue("operations").jsonArray.forEach { operation ->
            val endpointId = operation.jsonObject.string("id")
            val fixture = parseResource(operation.jsonObject.string("fixture")).jsonObject
            val categories = fixture.getValue("categories").jsonObject

            categories.getValue("minimal_valid").jsonObject.payloadOrNull()?.let { payload ->
                assertSuccess(endpointId, payload)
            }
            categories.getValue("minimal_valid").jsonObject.requestOrNull()?.let { request ->
                assertRequest(endpointId, request, expectSuccess = true)
            }
            categories.getValue("representative_valid").jsonObject.payloadOrNull()?.let { payload ->
                assertSuccess(endpointId, payload)
            }
            categories.getValue("representative_valid").jsonObject.requestOrNull()?.let { request ->
                assertRequest(endpointId, request, expectSuccess = true)
            }
            categories.getValue("incompatible").jsonObject.payloadOrNull()?.let { payload ->
                if (categories.getValue("incompatible").jsonObject.requestOrNull() == null) {
                    assertIncompatible(endpointId, payload)
                } else {
                    assertSuccess(endpointId, payload)
                }
            }
            categories.getValue("incompatible").jsonObject.requestOrNull()?.let { request ->
                assertRequest(endpointId, request, expectSuccess = false)
            }
        }
    }

    private fun assertSuccess(endpointId: String, payload: JsonElement) {
        val result = map(endpointId, payload)
        assertIs<BambuddyMappingResult.Success<*>>(result, endpointId)
    }

    private fun assertIncompatible(endpointId: String, payload: JsonElement) {
        val result = map(endpointId, payload)
        val failure = assertIs<BambuddyMappingResult.Failure>(result, endpointId)
        assertIs<IncompatibleApiResponse>(failure.error)
    }

    private fun assertRequest(endpointId: String, request: JsonElement, expectSuccess: Boolean) {
        if (endpointId == "assignment-create") {
            val result = validateAssignmentRequestBody(request.toString())
            if (expectSuccess) {
                assertIs<BambuddyMappingResult.Success<Unit>>(result, endpointId)
            } else {
                val failure = assertIs<BambuddyMappingResult.Failure>(result, endpointId)
                assertIs<IncompatibleApiResponse>(failure.error)
            }
        }
    }

    private fun map(endpointId: String, payload: JsonElement): BambuddyMappingResult<*> = when (endpointId) {
        "auth-me" -> parseAuthMeResponse(payload.toString())
        "printers-list" -> parsePrintersResponse(payload.toString())
        "printer-status" -> parsePrinterStatusResponse(payload.toString())
        "spools-list" -> parseSpoolsResponse(payload.toString())
        "spool-detail" -> parseSpoolResponse(payload.toString())
        "assignments-list",
        "assignments-by-printer" -> parseAssignmentsResponse(payload.toString())
        "assignment-create" -> parseAssignmentResponse(payload.toString())
        else -> error("Unexpected endpoint: $endpointId")
    }

    private fun parseResource(name: String): JsonElement = json.parseToJsonElement(resourceText(name))

    private fun resourceText(name: String): String =
        checkNotNull(javaClass.getResource("$RESOURCE_ROOT/$name")) { "Missing contract resource: $name" }.readText()

    private fun Map<String, JsonElement>.payloadOrNull(): JsonElement? = get("payload")

    private fun Map<String, JsonElement>.requestOrNull(): JsonElement? = get("request")

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private companion object {
        const val RESOURCE_ROOT = "/contracts/bambuddy/0.2.4.7"
        val json = Json { ignoreUnknownKeys = true }
    }
}
