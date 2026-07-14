package com.murzify.bambuddyspool.core.platform

import com.murzify.bambuddyspool.core.security.SecureTokenStore
import kotlinx.coroutines.CoroutineDispatcher

/** Device-bound storage boundary for the single Bambuddy API token. */
interface SecureStorage : SecureTokenStore

/** Platform-neutral result of one NFC observation. */
data class NfcObservation(val fingerprint: String, val payload: String?)

/** NFC availability distinguished without exposing platform framework types to shared presentation. */
enum class NfcAvailability {
    Available,
    Unavailable,
    Disabled
}

/** Narrow NFC capability required by shared workflows. */
interface NfcService {
    val isAvailable: Boolean
    val availability: NfcAvailability
        get() = if (isAvailable) NfcAvailability.Available else NfcAvailability.Unavailable

    suspend fun read(): NfcObservation
}

/** Opens platform-owned settings that cannot be changed from shared code. */
interface PlatformSettingsNavigator {
    fun openNfcSettings()
}

/** Copies only content that has already passed structural redaction. */
interface ClipboardService {
    fun copyRedacted(text: String): Boolean
}

/** Emits non-essential platform haptic feedback. */
interface HapticsService {
    fun success()
}

/** Coroutine dispatchers owned by the platform runtime. */
interface AppDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
}

/** Explicit transport exceptions approved for one configured server policy. */
data class NetworkPolicy(val allowCleartext: Boolean, val allowInvalidTls: Boolean)

/** Opaque platform HTTP engine consumed by the later shared network layer. */
interface PlatformHttpEngine

/** Creates a platform HTTP engine for an already validated network policy. */
interface PlatformNetworkFactory {
    fun create(policy: NetworkPolicy): PlatformHttpEngine
}

/** Explicit platform capabilities supplied when a shell creates the shared graph. */
data class PlatformServices(
    val secureStorage: SecureStorage,
    val nfc: NfcService,
    val settings: PlatformSettingsNavigator,
    val clipboard: ClipboardService,
    val haptics: HapticsService,
    val dispatchers: AppDispatchers,
    val network: PlatformNetworkFactory
)
