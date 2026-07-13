package com.murzify.bambuddyspool.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout

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
