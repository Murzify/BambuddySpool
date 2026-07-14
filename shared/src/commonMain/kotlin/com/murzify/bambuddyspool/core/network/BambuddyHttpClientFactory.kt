package com.murzify.bambuddyspool.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout

/**
 * Creates the shared HTTP client with the required timeout and redirect baseline.
 *
 * The composition root owns closing the returned client. Redirects stay disabled until the network-security policy
 * can validate each target; the platform engine owns execution dispatching.
 */
fun createBambuddyHttpClient(
    engineFactory: HttpClientEngineFactory<*> = platformBambuddyHttpEngineFactory()
): HttpClient = HttpClient(engineFactory) {
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
    }
}

expect fun platformBambuddyHttpEngineFactory(): HttpClientEngineFactory<*>

internal const val CONNECT_TIMEOUT_MILLIS = 3_000L
internal const val REQUEST_TIMEOUT_MILLIS = 10_000L
