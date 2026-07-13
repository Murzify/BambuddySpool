package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.domain.AssignmentCommand
import com.murzify.bambuddyspool.core.domain.AssignmentSource
import com.murzify.bambuddyspool.core.domain.IncompatibleApiReason
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.security.SecretValue
import com.murzify.bambuddyspool.core.settings.BaseUrlParseResult
import com.murzify.bambuddyspool.core.settings.CanonicalBaseUrl
import com.murzify.bambuddyspool.core.settings.parseCanonicalBaseUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class KtorBambuddyRepositoryTest {

    @Test
    fun everyMandatoryOperationUsesBoundedUrlBuilderAndApiKeyHeader() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repository("https://example.test/proxy") { request ->
            requests += request
            respond(
                content = responseFor(
                    method = request.method.value,
                    path = request.url.encodedPath,
                    printerIdQuery = request.url.parameters["printer_id"]
                ),
                headers = JSON_HEADERS
            )
        }

        assertIs<BambuddyNetworkResult.Success<Unit>>(repository.validateAuth())
        assertIs<BambuddyNetworkResult.Success<*>>(repository.getPrinters())
        assertIs<BambuddyNetworkResult.Success<*>>(repository.getPrinterStatus(printerId(1)))
        assertIs<BambuddyNetworkResult.Success<*>>(repository.getSpools())
        assertIs<BambuddyNetworkResult.Success<*>>(repository.getSpool(spoolId(3)))
        assertIs<BambuddyNetworkResult.Success<*>>(repository.getAssignments())
        assertIs<BambuddyNetworkResult.Success<*>>(repository.getAssignments(printerId(1)))
        assertIs<BambuddyNetworkResult.Success<*>>(repository.createAssignment(assignmentCommand()))

        assertEquals(
            listOf(
                "/proxy/api/v1/auth/me",
                "/proxy/api/v1/printers/",
                "/proxy/api/v1/printers/1/status",
                "/proxy/api/v1/inventory/spools",
                "/proxy/api/v1/inventory/spools/3",
                "/proxy/api/v1/inventory/assignments",
                "/proxy/api/v1/inventory/assignments",
                "/proxy/api/v1/inventory/assignments"
            ),
            requests.map { it.url.encodedPath }
        )
        assertEquals("true", requests[3].url.parameters["include_archived"])
        assertEquals("1", requests[6].url.parameters["printer_id"])
        assertEquals("synthetic-token", requests.single { it.method.value == "POST" }.headers["X-API-Key"])
        assertTrue(requests.all { it.headers["X-API-Key"] == "synthetic-token" })
    }

    @Test
    fun clientErrorsServerErrorsTransportContractTlsPolicyAndOversizeAreDistinct() = runTest {
        assertIs<BambuddyNetworkError.HttpClientError>(
            assertFailure(repositoryResponding(status = HttpStatusCode.Unauthorized).validateAuth())
        )
        assertIs<BambuddyNetworkError.HttpServerError>(
            assertFailure(repositoryResponding(status = HttpStatusCode.InternalServerError).validateAuth())
        )
        assertEquals(
            BambuddyNetworkError.Transport(TransportFailureReason.NetworkUnavailable),
            assertFailure(repositoryThrowing(IOException("synthetic transport failure")).validateAuth())
        )
        val contract = assertIs<BambuddyNetworkError.Contract>(
            assertFailure(repositoryResponding(content = """{"unexpected":true}""").getSpool(spoolId(3)))
        )
        assertEquals(IncompatibleApiReason.MissingRequiredField, contract.response.reason)
        assertEquals(
            BambuddyNetworkError.SecurityPolicy(SecurityPolicyFailureReason.TlsValidationFailed),
            assertFailure(repositoryThrowing(SyntheticCertificateFailure()).validateAuth())
        )
        assertEquals(
            BambuddyNetworkError.SecurityPolicy(SecurityPolicyFailureReason.InitialRequestDenied),
            assertFailure(repository(policy = DenyingInitialRequestPolicy).validateAuth())
        )
        assertEquals(
            BambuddyNetworkError.ResponseTooLarge(1_048_576L),
            assertFailure(repositoryResponding(content = "x".repeat(1_048_577)).validateAuth())
        )
    }

    @Test
    fun credentialValueDoesNotEnterTypedFailureText() = runTest {
        val secret = "token-that-must-not-leak"
        val repository = repository(token = secret) {
            throw IOException("transport text includes $secret")
        }

        val failure = assertFailure(repository.validateAuth())

        assertFalse(failure.toString().contains(secret), failure.toString())
        assertContains(failure.toString(), "Transport")
    }

    private fun repositoryResponding(
        status: HttpStatusCode = HttpStatusCode.OK,
        content: String = AUTH_ME_JSON
    ): BambuddyRepository = repository {
        if (status == HttpStatusCode.OK) {
            respond(content = content, headers = JSON_HEADERS)
        } else {
            respondError(status = status, content = ERROR_JSON, headers = JSON_HEADERS)
        }
    }

    private fun repositoryThrowing(throwable: Throwable): BambuddyRepository = repository {
        throw throwable
    }

    private fun repository(
        baseUrl: String = "https://example.test",
        token: String = "synthetic-token",
        policy: BambuddyNetworkSecurityPolicy = AllowingBambuddyNetworkSecurityPolicy,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
            respond(content = AUTH_ME_JSON, headers = JSON_HEADERS)
        }
    ): BambuddyRepository {
        val client = HttpClient(MockEngine(handler)) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            }
        }
        return KtorBambuddyRepository(
            client = client,
            baseUrl = canonicalBaseUrl(baseUrl),
            credentials = BambuddyCredentialProvider {
                SecretValue.fromPlainText(token)
            },
            securityPolicy = policy
        )
    }

    private fun responseFor(method: String, path: String, printerIdQuery: String?): String = when {
        method == "POST" && path == "/proxy/api/v1/inventory/assignments" -> ASSIGNMENT_JSON
        path == "/proxy/api/v1/auth/me" -> AUTH_ME_JSON
        path == "/proxy/api/v1/printers/" -> PRINTERS_JSON
        path == "/proxy/api/v1/printers/1/status" -> PRINTER_STATUS_JSON
        path == "/proxy/api/v1/inventory/spools" -> SPOOLS_JSON
        path == "/proxy/api/v1/inventory/spools/3" -> SPOOL_JSON
        path == "/proxy/api/v1/inventory/assignments" -> if (printerIdQuery == null) {
            ASSIGNMENTS_JSON
        } else {
            ASSIGNMENTS_BY_PRINTER_JSON
        }
        else -> error("Unexpected path: $path")
    }

    private fun assignmentCommand(): AssignmentCommand = AssignmentCommand.from(
        spoolId = spoolId(3),
        slot = SlotKey(printerId = printerId(1), amsId = 255, trayId = 0),
        source = AssignmentSource.Manual,
        expectedSnapshotGeneration = assertNotNull(SnapshotGeneration.from(0))
    )

    private fun assertFailure(result: BambuddyNetworkResult<*>): BambuddyNetworkError =
        assertIs<BambuddyNetworkResult.Failure>(result).error

    private fun canonicalBaseUrl(value: String): CanonicalBaseUrl = when (val result = parseCanonicalBaseUrl(value)) {
        is BaseUrlParseResult.Success -> result.value
        is BaseUrlParseResult.Failure -> error("Invalid test base URL: $value")
    }

    private fun printerId(value: Long): PrinterId = assertNotNull(PrinterId.from(value))

    private fun spoolId(value: Long): SpoolId = assertNotNull(SpoolId.from(value))

    private object DenyingInitialRequestPolicy : BambuddyNetworkSecurityPolicy {
        override suspend fun validateInitialRequest(baseUrl: CanonicalBaseUrl, url: String): BambuddySecurityDecision =
            BambuddySecurityDecision.Deny(SecurityPolicyFailureReason.InitialRequestDenied)

        override suspend fun validateRedirect(
            baseUrl: CanonicalBaseUrl,
            fromUrl: String,
            toUrl: String,
            redirectCount: Int
        ): BambuddySecurityDecision = BambuddySecurityDecision.Allow
    }

    private class SyntheticCertificateFailure : IOException("synthetic certificate failure")

    private companion object {
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
        const val ERROR_JSON = """{"detail":"synthetic error"}"""
        const val AUTH_ME_JSON =
            """{"id":1,"username":"synthetic","role":"admin","is_active":true,"is_admin":true,"created_at":"2000-01-01T00:00:00Z"}"""
        const val PRINTERS_JSON = """[{"id":1,"name":"Synthetic Printer","model":null,"is_active":true}]"""
        const val PRINTER_STATUS_JSON =
            """{"id":1,"name":"Synthetic Printer","connected":true,"vt_tray":[{"id":255,"tray_id_name":"External"}]}"""
        const val SPOOL_JSON =
            """{"id":3,"material":"PLA","subtype":"Blue","color_name":"Blue","rgba":"0000FFFF","brand":"Synthetic","label_weight":1000,"core_weight":250,"weight_used":100.0,"last_used":null,"archived_at":null,"created_at":"2000-01-01T00:00:00Z","updated_at":"2000-01-01T00:00:00Z"}"""
        const val SPOOLS_JSON = "[$SPOOL_JSON]"
        const val ASSIGNMENT_JSON =
            """{"id":4,"spool_id":3,"printer_id":1,"ams_id":255,"tray_id":0,"created_at":"2000-01-01T00:00:00Z","configured":true,"pending_config":false,"ams_label":"External"}"""
        const val ASSIGNMENTS_JSON = "[$ASSIGNMENT_JSON]"
        const val ASSIGNMENTS_BY_PRINTER_JSON = "[$ASSIGNMENT_JSON]"
    }
}
