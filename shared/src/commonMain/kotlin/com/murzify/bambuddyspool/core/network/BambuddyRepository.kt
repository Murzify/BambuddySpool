package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.AssignmentCommand
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.settings.CanonicalBaseUrl
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.path
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.io.readByteArray

/**
 * Minimal Bambuddy API boundary used by synchronization and future assignment workflows.
 *
 * Implementations return typed protocol, transport, and security failures rather than raw exceptions. They must
 * preserve coroutine cancellation so callers retain control of workflow lifetime.
 */
interface BambuddyRepository {
    suspend fun validateAuth(): BambuddyNetworkResult<Unit>
    suspend fun getPrinters(): BambuddyNetworkResult<List<Printer>>
    suspend fun getPrinterStatus(printerId: PrinterId): BambuddyNetworkResult<PrinterStatus>
    suspend fun getSpools(includeArchived: Boolean = true): BambuddyNetworkResult<List<Spool>>
    suspend fun getSpool(spoolId: SpoolId): BambuddyNetworkResult<Spool>
    suspend fun getAssignments(printerId: PrinterId? = null): BambuddyNetworkResult<List<Assignment>>
    suspend fun createAssignment(command: AssignmentCommand): BambuddyNetworkResult<Assignment>
}

/**
 * Ktor implementation of [BambuddyRepository] for one validated Bambuddy origin.
 *
 * The caller owns the [client] lifecycle. Requests do not follow redirects, add credentials only at the trusted
 * request boundary, and enforce endpoint-specific decompressed response limits before mapping a response.
 */
class KtorBambuddyRepository(
    private val client: HttpClient,
    private val baseUrl: CanonicalBaseUrl,
    private val credentials: BambuddyCredentialProvider,
    private val securityPolicy: BambuddyNetworkSecurityPolicy = AllowingBambuddyNetworkSecurityPolicy
) : BambuddyRepository {

    override suspend fun validateAuth(): BambuddyNetworkResult<Unit> = request(
        method = HttpMethod.Get,
        endpoint = BambuddyEndpoint.AuthMe,
        pathSegments = listOf("api", "v1", "auth", "me"),
        mapper = ::parseAuthMeResponse
    )

    override suspend fun getPrinters(): BambuddyNetworkResult<List<Printer>> = request(
        method = HttpMethod.Get,
        endpoint = BambuddyEndpoint.PrintersList,
        pathSegments = listOf("api", "v1", "printers"),
        trailingSlash = true,
        mapper = ::parsePrintersResponse
    )

    override suspend fun getPrinterStatus(printerId: PrinterId): BambuddyNetworkResult<PrinterStatus> = request(
        method = HttpMethod.Get,
        endpoint = BambuddyEndpoint.PrinterStatus,
        pathSegments = listOf("api", "v1", "printers", printerId.value.toString(), "status"),
        mapper = ::parsePrinterStatusResponse
    )

    override suspend fun getSpools(includeArchived: Boolean): BambuddyNetworkResult<List<Spool>> = request(
        method = HttpMethod.Get,
        endpoint = BambuddyEndpoint.SpoolsSnapshot,
        pathSegments = listOf("api", "v1", "inventory", "spools"),
        configure = {
            url.parameters.append("include_archived", includeArchived.toString())
        },
        mapper = ::parseSpoolsResponse
    )

    override suspend fun getSpool(spoolId: SpoolId): BambuddyNetworkResult<Spool> = request(
        method = HttpMethod.Get,
        endpoint = BambuddyEndpoint.SpoolDetail,
        pathSegments = listOf("api", "v1", "inventory", "spools", spoolId.value.toString()),
        mapper = ::parseSpoolResponse
    )

    override suspend fun getAssignments(printerId: PrinterId?): BambuddyNetworkResult<List<Assignment>> = request(
        method = HttpMethod.Get,
        endpoint = BambuddyEndpoint.Assignments,
        pathSegments = listOf("api", "v1", "inventory", "assignments"),
        configure = {
            if (printerId != null) {
                url.parameters.append("printer_id", printerId.value.toString())
            }
        },
        mapper = ::parseAssignmentsResponse
    )

    override suspend fun createAssignment(command: AssignmentCommand): BambuddyNetworkResult<Assignment> = request(
        method = HttpMethod.Post,
        endpoint = BambuddyEndpoint.AssignmentCreate,
        pathSegments = listOf("api", "v1", "inventory", "assignments"),
        configure = {
            contentType(ContentType.Application.Json)
            setBody(encodeAssignmentRequest(command))
        },
        mapper = ::parseAssignmentResponse
    )

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private suspend fun <T> request(
        method: HttpMethod,
        endpoint: BambuddyEndpoint,
        pathSegments: List<String>,
        trailingSlash: Boolean = false,
        configure: HttpRequestBuilder.() -> Unit = {},
        mapper: (String) -> BambuddyMappingResult<T>
    ): BambuddyNetworkResult<T> {
        val url = buildEndpointUrl(pathSegments = pathSegments, trailingSlash = trailingSlash)
        when (val decision = securityPolicy.validateInitialRequest(baseUrl = baseUrl, url = url)) {
            BambuddySecurityDecision.Allow -> Unit
            is BambuddySecurityDecision.Deny -> return BambuddyNetworkResult.Failure(
                BambuddyNetworkError.SecurityPolicy(decision.reason)
            )
        }

        val token = credentials.loadApiToken()
            ?: return BambuddyNetworkResult.Failure(BambuddyNetworkError.MissingCredential)

        val response = try {
            token.useForTrustedRequestBoundary { plainToken ->
                when (method) {
                    HttpMethod.Get -> client.get(url) {
                        header(API_KEY_HEADER, plainToken)
                        configure()
                    }
                    HttpMethod.Post -> client.post(url) {
                        header(API_KEY_HEADER, plainToken)
                        configure()
                    }
                    else -> error("Unsupported Bambuddy HTTP method.")
                }
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            return BambuddyNetworkResult.Failure(classifyThrowable(throwable))
        }

        return response.toNetworkResult(endpoint = endpoint, mapper = mapper)
    }

    private fun buildEndpointUrl(pathSegments: List<String>, trailingSlash: Boolean): String {
        val baseSegments = baseUrl.basePath
            .split('/')
            .filter { it.isNotBlank() }
        val allSegments = if (trailingSlash) {
            baseSegments + pathSegments + ""
        } else {
            baseSegments + pathSegments
        }
        return URLBuilder().apply {
            protocol = io.ktor.http.URLProtocol.createOrDefault(baseUrl.scheme.wireName)
            host = baseUrl.host
            port = baseUrl.effectivePort
            path(*allSegments.toTypedArray())
        }.buildString()
    }
}

private suspend fun <T> HttpResponse.toNetworkResult(
    endpoint: BambuddyEndpoint,
    mapper: (String) -> BambuddyMappingResult<T>
): BambuddyNetworkResult<T> {
    val statusCode = status.value
    return when {
        statusCode in CLIENT_ERROR_RANGE -> BambuddyNetworkResult.Failure(
            BambuddyNetworkError.HttpClientError(statusCode)
        )
        statusCode in SERVER_ERROR_RANGE -> BambuddyNetworkResult.Failure(
            BambuddyNetworkError.HttpServerError(statusCode)
        )
        statusCode != HttpStatusCode.OK.value && statusCode != HttpStatusCode.Created.value ->
            BambuddyNetworkResult.Failure(BambuddyNetworkError.HttpUnexpectedStatus(statusCode))
        else -> readBoundedText(endpoint.responseLimitBytes).fold(
            onSuccess = { body ->
                when (val mapped = mapper(body)) {
                    is BambuddyMappingResult.Success -> BambuddyNetworkResult.Success(mapped.value)
                    is BambuddyMappingResult.Failure -> BambuddyNetworkResult.Failure(
                        BambuddyNetworkError.Contract(mapped.error)
                    )
                }
            },
            onFailure = { BambuddyNetworkResult.Failure(it) }
        )
    }
}

@Suppress("ReturnCount")
private suspend fun HttpResponse.readBoundedText(limitBytes: Long): BoundedBodyReadResult {
    val declaredLength = headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declaredLength != null && declaredLength > limitBytes) {
        return BoundedBodyReadResult.Failure(BambuddyNetworkError.ResponseTooLarge(limitBytes))
    }
    val packet = try {
        bodyAsChannel().readRemaining(limitBytes + 1)
    } catch (_: NoTransformationFoundException) {
        return BoundedBodyReadResult.Failure(BambuddyNetworkError.Transport(TransportFailureReason.Unknown))
    } catch (_: IOException) {
        return BoundedBodyReadResult.Failure(BambuddyNetworkError.Transport(TransportFailureReason.NetworkUnavailable))
    }
    val bytes = packet.readByteArray()
    return if (bytes.size > limitBytes) {
        BoundedBodyReadResult.Failure(BambuddyNetworkError.ResponseTooLarge(limitBytes))
    } else {
        BoundedBodyReadResult.Success(bytes.decodeToString())
    }
}

private fun classifyThrowable(throwable: Throwable): BambuddyNetworkError = when (throwable) {
    is ConnectTimeoutException -> BambuddyNetworkError.Transport(TransportFailureReason.ConnectTimeout)
    is HttpRequestTimeoutException -> BambuddyNetworkError.Transport(TransportFailureReason.RequestTimeout)
    else -> if (throwable.isTlsFailure()) {
        BambuddyNetworkError.SecurityPolicy(SecurityPolicyFailureReason.TlsValidationFailed)
    } else {
        classifyTransportThrowable(throwable)
    }
}

private fun classifyTransportThrowable(throwable: Throwable): BambuddyNetworkError = when (throwable) {
    is IOException -> BambuddyNetworkError.Transport(TransportFailureReason.NetworkUnavailable)
    else -> BambuddyNetworkError.Transport(TransportFailureReason.Unknown)
}

private fun Throwable.isTlsFailure(): Boolean {
    val names = sequence {
        var current: Throwable? = this@isTlsFailure
        while (current != null) {
            yield(current::class.simpleName.orEmpty())
            current = current.cause
        }
    }
    return names.any { name ->
        name.contains("SSL", ignoreCase = true) ||
            name.contains("TLS", ignoreCase = true) ||
            name.contains("Certificate", ignoreCase = true)
    }
}

private sealed interface BoundedBodyReadResult {
    data class Success(val body: String) : BoundedBodyReadResult
    data class Failure(val error: BambuddyNetworkError) : BoundedBodyReadResult

    fun <T> fold(
        onSuccess: (String) -> BambuddyNetworkResult<T>,
        onFailure: (BambuddyNetworkError) -> BambuddyNetworkResult<T>
    ): BambuddyNetworkResult<T> = when (this) {
        is Success -> onSuccess(body)
        is Failure -> onFailure(error)
    }
}

private enum class BambuddyEndpoint(val responseLimitBytes: Long) {
    AuthMe(MEBIBYTE),
    PrintersList(ASSIGNMENTS_LIMIT_BYTES),
    PrinterStatus(PRINTER_STATUS_LIMIT_BYTES),
    SpoolsSnapshot(SPOOLS_SNAPSHOT_LIMIT_BYTES),
    SpoolDetail(MEBIBYTE),
    Assignments(ASSIGNMENTS_LIMIT_BYTES),
    AssignmentCreate(ASSIGNMENTS_LIMIT_BYTES)
}

private const val API_KEY_HEADER = "X-API-Key"
private const val MEBIBYTE = 1_048_576L
private const val PRINTER_STATUS_LIMIT_BYTES = 4L * MEBIBYTE
private const val ASSIGNMENTS_LIMIT_BYTES = 16L * MEBIBYTE
private const val SPOOLS_SNAPSHOT_LIMIT_BYTES = 64L * MEBIBYTE
private val CLIENT_ERROR_RANGE = 400..499
private val SERVER_ERROR_RANGE = 500..599
