package com.murzify.bambuddyspool.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformBambuddyHttpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
