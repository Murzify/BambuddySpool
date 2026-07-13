package com.murzify.bambuddyspool.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun platformBambuddyHttpEngineFactory(): HttpClientEngineFactory<*> = Darwin
