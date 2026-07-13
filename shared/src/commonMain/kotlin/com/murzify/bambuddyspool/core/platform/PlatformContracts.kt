package com.murzify.bambuddyspool.core.platform

import kotlinx.coroutines.CoroutineDispatcher

interface SecretValue

interface SecureStorage {
    suspend fun replace(value: SecretValue)
    suspend fun clear()
    suspend fun isPresent(): Boolean
}

data class NfcObservation(val fingerprint: String, val payload: String?)

interface NfcService {
    val isAvailable: Boolean
    suspend fun read(): NfcObservation
}

interface PlatformSettingsNavigator {
    fun openNfcSettings()
}
interface ClipboardService {
    fun copyRedacted(text: String): Boolean
}
interface HapticsService {
    fun success()
}

interface AppDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
}

data class NetworkPolicy(val allowCleartext: Boolean, val allowInvalidTls: Boolean)
interface PlatformHttpEngine
interface PlatformNetworkFactory {
    fun create(policy: NetworkPolicy): PlatformHttpEngine
}

data class PlatformServices(
    val secureStorage: SecureStorage,
    val nfc: NfcService,
    val settings: PlatformSettingsNavigator,
    val clipboard: ClipboardService,
    val haptics: HapticsService,
    val dispatchers: AppDispatchers,
    val network: PlatformNetworkFactory
)
