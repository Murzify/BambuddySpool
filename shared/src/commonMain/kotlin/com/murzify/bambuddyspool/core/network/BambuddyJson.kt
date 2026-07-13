package com.murzify.bambuddyspool.core.network

import kotlinx.serialization.json.Json

internal val BambuddyJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = false
    coerceInputValues = false
}
