package com.murzify.bambuddyspool.core.platform

import com.murzify.bambuddyspool.core.security.SecretValue
import kotlinx.coroutines.Dispatchers

/** Deterministic test-only platform bindings; production shells must supply real capabilities. */
fun mockPlatformServices(): PlatformServices = PlatformServices(
    secureStorage = object : SecureStorage {
        override suspend fun replaceToken(value: SecretValue) = Unit
        override suspend fun clearToken() = Unit
        override suspend fun hasToken(): Boolean = false
        override suspend fun currentTokenForReplacement(): SecretValue? = null
    },
    nfc = object : NfcService {
        override val isAvailable: Boolean = false
        override suspend fun read(): NfcObservation = error("NFC is unavailable in this test binding")
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
