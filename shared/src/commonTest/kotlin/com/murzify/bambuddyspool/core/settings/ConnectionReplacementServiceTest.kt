package com.murzify.bambuddyspool.core.settings

import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.security.SecretValue
import com.murzify.bambuddyspool.core.security.SecureTokenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class ConnectionReplacementServiceTest {

    @Test
    fun hostChangeResetsTlsOverride() = runBlocking {
        val oldUrl = url("https://old.example.local:8443/api")
        val settings = FakeSettingsStore(
            ConnectionSettings(
                baseUrl = oldUrl,
                defaultPrinterId = assertNotNull(PrinterId.from(7)),
                httpConsentOrigin = null,
                tlsOverrideHostname = "old.example.local"
            )
        )
        val service = service(settings)

        assertEquals(
            ConnectionReplacementResult.Replaced,
            service.replaceConnection(
                rawBaseUrl = "https://new.example.local:8443/api",
                token = secret("new-token"),
                acknowledgements = ConnectionReplacementAcknowledgements(
                    acceptedInstanceChangeWarning = true,
                    acceptedHttpWarning = false
                )
            )
        )

        assertEquals(null, settings.current.tlsOverrideHostname)
        assertEquals(null, settings.current.defaultPrinterId)
    }

    @Test
    fun portAndPathChangeKeepTlsHostnameOverride() = runBlocking {
        val settings = FakeSettingsStore(
            ConnectionSettings(
                baseUrl = url("https://bambuddy.local/api"),
                defaultPrinterId = null,
                httpConsentOrigin = null,
                tlsOverrideHostname = "bambuddy.local"
            )
        )
        val service = service(settings)

        assertEquals(
            ConnectionReplacementResult.Replaced,
            service.replaceConnection(
                rawBaseUrl = "https://bambuddy.local:9443/other",
                token = secret("new-token"),
                acknowledgements = ConnectionReplacementAcknowledgements(
                    acceptedInstanceChangeWarning = true,
                    acceptedHttpWarning = false
                )
            )
        )

        assertEquals("bambuddy.local", settings.current.tlsOverrideHostname)
    }

    @Test
    fun originChangeResetsHttpConsentAndRequiresAcknowledgement() = runBlocking {
        val original = url("http://bambuddy.local/api")
        val settings = FakeSettingsStore(
            ConnectionSettings(
                baseUrl = original,
                defaultPrinterId = null,
                httpConsentOrigin = original.origin,
                tlsOverrideHostname = null
            )
        )
        val service = service(settings)

        val blocked = service.replaceConnection(
            rawBaseUrl = "http://bambuddy.local:8080/api",
            token = secret("new-token"),
            acknowledgements = ConnectionReplacementAcknowledgements(
                acceptedInstanceChangeWarning = true,
                acceptedHttpWarning = false
            )
        )

        assertIs<ConnectionReplacementResult.HttpWarningRequired>(blocked)
        assertEquals(original, settings.current.baseUrl)

        assertEquals(
            ConnectionReplacementResult.Replaced,
            service.replaceConnection(
                rawBaseUrl = "http://bambuddy.local:8080/api",
                token = secret("new-token"),
                acknowledgements = ConnectionReplacementAcknowledgements(
                    acceptedInstanceChangeWarning = true,
                    acceptedHttpWarning = true
                )
            )
        )
        assertEquals(url("http://bambuddy.local:8080/api").origin, settings.current.httpConsentOrigin)
    }

    @Test
    fun failedConnectionValidationPreservesPreviousSettingsTokenAndSnapshot() = runBlocking {
        val original = url("https://old.example.local/api")
        val oldToken = secret("old-token")
        val settings = FakeSettingsStore(
            ConnectionSettings(
                baseUrl = original,
                defaultPrinterId = assertNotNull(PrinterId.from(5)),
                httpConsentOrigin = null,
                tlsOverrideHostname = "old.example.local"
            )
        )
        val tokenStore = FakeTokenStore(oldToken)
        val cache = FakeReplacementTransaction(settings, tokenStore)
        val validator =
            FakeValidator(connectionResult = failure(ConnectionValidationFailureReason.AuthenticationRejected))
        val service = service(settings = settings, tokenStore = tokenStore, validator = validator, cache = cache)

        val result = service.replaceConnection(
            rawBaseUrl = "https://new.example.local/api",
            token = secret("bad-token"),
            acknowledgements = ConnectionReplacementAcknowledgements(
                acceptedInstanceChangeWarning = true,
                acceptedHttpWarning = false
            )
        )

        assertEquals(
            ConnectionReplacementResult.ValidationFailed(ConnectionValidationFailureReason.AuthenticationRejected),
            result
        )
        assertEquals(original, settings.current.baseUrl)
        assertEquals(oldToken, tokenStore.currentToken)
        assertEquals(0, cache.clearCount)
    }

    @Test
    fun failedTokenReplacementPreservesPreviousTokenAndSnapshot() = runBlocking {
        val oldToken = secret("old-token")
        val settings = FakeSettingsStore(
            ConnectionSettings(
                baseUrl = url("https://bambuddy.local/api"),
                defaultPrinterId = null,
                httpConsentOrigin = null,
                tlsOverrideHostname = null
            )
        )
        val tokenStore = FakeTokenStore(oldToken)
        val cache = FakeReplacementTransaction(settings, tokenStore)
        val validator = FakeValidator(tokenResult = failure(ConnectionValidationFailureReason.AuthenticationRejected))
        val service = service(settings = settings, tokenStore = tokenStore, validator = validator, cache = cache)

        val result = service.replaceToken(secret("bad-token"))

        assertEquals(
            TokenReplacementResult.ValidationFailed(ConnectionValidationFailureReason.AuthenticationRejected),
            result
        )
        assertEquals(oldToken, tokenStore.currentToken)
        assertEquals(0, cache.clearCount)
    }

    @Test
    fun testConnectionNeverMutatesTheActiveConnection() = runBlocking {
        val original = url("https://old.example.local/api")
        val oldToken = secret("old-token")
        val settings = FakeSettingsStore(
            ConnectionSettings(original, null, null, null)
        )
        val tokenStore = FakeTokenStore(oldToken)
        val cache = FakeReplacementTransaction(settings, tokenStore)
        val service = service(settings = settings, tokenStore = tokenStore, cache = cache)

        assertEquals(
            ConnectionTestResult.Valid,
            service.testConnection("https://new.example.local/api", secret("new-token"))
        )
        assertEquals(original, settings.current.baseUrl)
        assertEquals(oldToken, tokenStore.currentToken)
        assertEquals(0, cache.clearCount)
    }

    @Test
    fun validationOccursBeforeTheInstanceChangeWarning() = runBlocking {
        val settings = FakeSettingsStore(ConnectionSettings(url("https://old.example.local"), null, null, null))
        val validator = FakeValidator(
            connectionResult = failure(ConnectionValidationFailureReason.AuthenticationRejected)
        )
        val service = service(settings = settings, validator = validator)

        val result = service.replaceConnection(
            rawBaseUrl = "https://new.example.local",
            token = secret("bad-token"),
            acknowledgements = ConnectionReplacementAcknowledgements.None
        )

        assertEquals(
            ConnectionReplacementResult.ValidationFailed(ConnectionValidationFailureReason.AuthenticationRejected),
            result
        )
        assertEquals(1, validator.connectionValidationCount)
    }

    @Test
    fun confirmedReplacementClearsTheSnapshotAndRequestsInitialSync() = runBlocking {
        val settings = FakeSettingsStore(ConnectionSettings(url("https://old.example.local"), null, null, null))
        val tokenStore = FakeTokenStore()
        val cache = FakeReplacementTransaction(settings, tokenStore)
        val sync = FakeInitialSync()
        val service = service(settings = settings, cache = cache, sync = sync)

        assertEquals(
            ConnectionReplacementResult.Replaced,
            service.replaceConnection(
                rawBaseUrl = "https://new.example.local",
                token = secret("new-token"),
                acknowledgements = ConnectionReplacementAcknowledgements(
                    acceptedInstanceChangeWarning = true,
                    acceptedHttpWarning = false
                )
            )
        )
        assertEquals(1, cache.clearCount)
        assertEquals(1, sync.requestCount)
    }

    @Test
    fun savingTheSameUrlReplacesOnlyTheValidatedToken() = runBlocking {
        val activeUrl = url("https://bambuddy.example/api")
        val settings = FakeSettingsStore(ConnectionSettings(activeUrl, null, null, null))
        val tokenStore = FakeTokenStore(secret("old-token"))
        val cache = FakeReplacementTransaction(settings, tokenStore)
        val sync = FakeInitialSync()
        val service = service(settings = settings, tokenStore = tokenStore, cache = cache, sync = sync)

        assertEquals(
            ConnectionReplacementResult.Replaced,
            service.replaceConnection(
                rawBaseUrl = activeUrl.canonical,
                token = secret("new-token"),
                acknowledgements = ConnectionReplacementAcknowledgements.None
            )
        )
        assertEquals(activeUrl, settings.current.baseUrl)
        assertEquals(0, cache.clearCount)
        assertEquals(0, sync.requestCount)
    }

    private fun service(
        settings: FakeSettingsStore,
        tokenStore: FakeTokenStore = FakeTokenStore(),
        validator: FakeValidator = FakeValidator(),
        cache: FakeReplacementTransaction = FakeReplacementTransaction(settings, tokenStore),
        sync: FakeInitialSync = FakeInitialSync()
    ): ConnectionReplacementService = ConnectionReplacementService(
        settingsStore = settings,
        tokenStore = tokenStore,
        validator = validator,
        cacheMaintenance = cache,
        initialSync = sync
    )

    private fun url(raw: String): CanonicalBaseUrl =
        assertIs<BaseUrlParseResult.Success>(parseCanonicalBaseUrl(raw)).value
    private fun secret(value: String): SecretValue = assertNotNull(SecretValue.fromPlainText(value))
    private fun failure(reason: ConnectionValidationFailureReason): ConnectionValidationResult =
        ConnectionValidationResult.Failure(reason)
}

private class FakeSettingsStore(initial: ConnectionSettings) : ConnectionSettingsStore {
    var current: ConnectionSettings = initial
        private set

    override suspend fun read(): ConnectionSettings = current

    override suspend fun replace(settings: ConnectionSettings) {
        current = settings
    }
}

private class FakeTokenStore(initial: SecretValue? = null) : SecureTokenStore {
    var currentToken: SecretValue? = initial
        private set

    override suspend fun replaceToken(value: SecretValue) {
        currentToken = value
    }

    override suspend fun clearToken() {
        currentToken = null
    }

    override suspend fun hasToken(): Boolean = currentToken != null
    override suspend fun currentTokenForReplacement(): SecretValue? = currentToken
}

private class FakeValidator(
    private val connectionResult: ConnectionValidationResult = ConnectionValidationResult.Valid,
    private val tokenResult: ConnectionValidationResult = ConnectionValidationResult.Valid
) : ConnectionValidator {
    var connectionValidationCount: Int = 0
        private set

    override suspend fun validateConnection(baseUrl: CanonicalBaseUrl, token: SecretValue): ConnectionValidationResult =
        connectionResult.also { connectionValidationCount++ }

    override suspend fun validateToken(
        activeBaseUrl: CanonicalBaseUrl,
        token: SecretValue
    ): ConnectionValidationResult = tokenResult
}

private class FakeReplacementTransaction(
    private val settingsStore: FakeSettingsStore,
    private val tokenStore: FakeTokenStore
) : ConnectionCacheMaintenance {
    var clearCount: Int = 0
        private set

    override suspend fun clearDomainSnapshot() {
        clearCount++
    }
}

private class FakeInitialSync : InitialConnectionSync {
    var requestCount: Int = 0
        private set

    override suspend fun requestInitialSync() {
        requestCount++
    }
}
