package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.domain.IncompatibleApiResponse

sealed interface BambuddyNetworkResult<out T> {
    data class Success<T>(val value: T) : BambuddyNetworkResult<T>
    data class Failure(val error: BambuddyNetworkError) : BambuddyNetworkResult<Nothing>
}

sealed interface BambuddyNetworkError {
    data object MissingCredential : BambuddyNetworkError
    data class HttpClientError(val statusCode: Int) : BambuddyNetworkError
    data class HttpServerError(val statusCode: Int) : BambuddyNetworkError
    data class HttpUnexpectedStatus(val statusCode: Int) : BambuddyNetworkError
    data class Transport(val reason: TransportFailureReason) : BambuddyNetworkError
    data class Contract(val response: IncompatibleApiResponse) : BambuddyNetworkError
    data class SecurityPolicy(val reason: SecurityPolicyFailureReason) : BambuddyNetworkError
    data class ResponseTooLarge(val limitBytes: Long) : BambuddyNetworkError
}

enum class TransportFailureReason {
    ConnectTimeout,
    RequestTimeout,
    NetworkUnavailable,
    Unknown
}

enum class SecurityPolicyFailureReason {
    InitialRequestDenied,
    RedirectDenied,
    TlsValidationFailed
}
