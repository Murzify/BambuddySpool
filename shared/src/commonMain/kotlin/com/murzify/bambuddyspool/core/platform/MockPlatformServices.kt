package com.murzify.bambuddyspool.core.platform

import kotlinx.coroutines.Dispatchers

fun mockPlatformServices(): PlatformServices = PlatformServices(
    secureStorage = object : SecureStorage {
        override suspend fun replace(value: SecretValue) = Unit
        override suspend fun clear() = Unit
        override suspend fun isPresent(): Boolean = false
    },
    nfc = object : NfcService {
        override val isAvailable: Boolean = false
        override suspend fun read(): NfcObservation = error("NFC is unavailable in the mock shell")
    },
    settings = object : PlatformSettingsNavigator {
        override fun openNfcSettings() = Unit
    },
    clipboard = object : ClipboardService {
        override fun copyRedacted(text: String) = false
    },
    haptics = object : HapticsService {
        override fun success() = Unit
    },
    dispatchers = object : AppDispatchers {
        override val main = Dispatchers.Main
        override val io = Dispatchers.Default
    },
    network = object : PlatformNetworkFactory {
        override fun create(policy: NetworkPolicy): PlatformHttpEngine = object : PlatformHttpEngine {}
    }
)
