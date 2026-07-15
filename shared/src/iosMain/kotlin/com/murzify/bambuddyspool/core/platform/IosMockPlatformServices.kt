package com.murzify.bambuddyspool.core.platform

import com.murzify.bambuddyspool.core.security.SecretValue
import kotlinx.coroutines.Dispatchers

/**
 * Builds the deliberately non-functional platform boundary used by the v1 iOS shell.
 *
 * The shell exists to launch and navigate the shared Compose root. It must never present an unsupported iOS
 * capability as a successful NFC, credential, clipboard, or network operation. There is no fallback persistence,
 * request queue, or synthetic inventory behind these bindings.
 */
internal fun iosMockPlatformServices(): PlatformServices = PlatformServices(
    secureStorage = object : SecureStorage {
        override suspend fun replaceToken(value: SecretValue): Nothing = unavailable("Credential storage")

        override suspend fun clearToken(): Nothing = unavailable("Credential storage")

        override suspend fun hasToken(): Boolean = false

        override suspend fun currentTokenForReplacement(): SecretValue? = null
    },
    nfc = object : NfcService {
        override val isAvailable: Boolean = false
        override val availability: NfcAvailability = NfcAvailability.Unavailable

        override suspend fun read(): Nothing = unavailable("NFC")
    },
    settings = object : PlatformSettingsNavigator {
        override fun openNfcSettings() = Unit
    },
    clipboard = object : ClipboardService {
        override fun copyRedacted(text: String): Boolean = false
    },
    haptics = object : HapticsService {
        override fun success() = Unit
    },
    dispatchers = object : AppDispatchers {
        override val main = Dispatchers.Default
        override val io = Dispatchers.Default
    },
    network = object : PlatformNetworkFactory {
        override fun create(policy: NetworkPolicy): Nothing = unavailable("Networking")
    }
)

/** A typed fail-closed result for platform features intentionally absent from the iOS mock shell. */
internal class IosMockServiceUnavailableException(service: String) :
    IllegalStateException("$service is unavailable in the iOS mock shell.")

private fun unavailable(service: String): Nothing = throw IosMockServiceUnavailableException(service)
