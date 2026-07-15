@file:Suppress("MaxLineLength")

package com.murzify.bambuddyspool.app.connection

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.AssignmentCommand
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.domain.VirtualTray
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError
import com.murzify.bambuddyspool.core.network.BambuddyNetworkResult
import com.murzify.bambuddyspool.core.network.BambuddyRepository
import com.murzify.bambuddyspool.core.network.TransportFailureReason
import com.murzify.bambuddyspool.core.projections.CacheProjectionState
import com.murzify.bambuddyspool.core.projections.MutationAvailability
import com.murzify.bambuddyspool.core.projections.PageRequest
import com.murzify.bambuddyspool.core.security.SecretValue
import com.murzify.bambuddyspool.core.security.SecureTokenStore
import com.murzify.bambuddyspool.core.settings.ConnectionSettings
import com.murzify.bambuddyspool.core.settings.parseCanonicalBaseUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope

@OptIn(ExperimentalCoroutinesApi::class)
class MvpConnectionRuntimeTest {

    @Test
    fun testUsesTheInjectedPolicyBoundRepositorySession() = runTest {
        val sessions = RecordingSessions(listOf(FakeRepository()))
        val runtime = runtime(sessions = sessions)

        runtime.form.accept(ConnectionFormIntent.BaseUrlChanged(HTTPS_URL))
        runtime.form.test("new-token")
        advanceUntilIdle()

        assertEquals(ConnectionFormMessage.TestSucceeded, runtime.form.state.value.message)
        assertEquals(1, sessions.created.size)
        assertEquals(HTTPS_URL, sessions.created.single().first.canonical)
        assertEquals(1, sessions.closed)
    }

    @Test
    fun savePublishesReadOnlySnapshotAndLeavesMutationDisabled() = runTest {
        val sessions = RecordingSessions(listOf(FakeRepository(), FakeRepository()))
        val tokens = FakeTokenStore()
        val runtime = runtime(sessions = sessions, tokenStore = tokens)

        runtime.form.accept(ConnectionFormIntent.BaseUrlChanged(HTTPS_URL))
        runtime.form.save("new-token")
        advanceUntilIdle()

        assertEquals(ConnectionFormMessage.SaveSucceeded, runtime.form.state.value.message)
        assertEquals("new-token", tokens.currentTokenForReplacement()?.useForTrustedRequestBoundary { it })
        val content = assertIs<CacheProjectionState.Content<*>>(
            runtime.cache.observeDefaultSpoolPage(PageRequest(limit = 10, offset = 0)).first()
        )
        assertEquals(MutationAvailability.Disabled::class, content.availability.mutation::class)
        assertEquals(2, sessions.created.size)
        assertEquals(2, sessions.closed)
    }

    @Test
    fun failedInitialSyncRestoresPreviousSettingsAndToken() = runTest {
        val sessions = RecordingSessions(
            listOf(FakeRepository(), FakeRepository(printersFailure = true))
        )
        val oldToken = requireNotNull(SecretValue.fromPlainText("old-token"))
        val tokens = FakeTokenStore(oldToken)
        val runtime = runtime(sessions = sessions, tokenStore = tokens)
        val prior = canonical("https://previous.example")
        runtime.replace(ConnectionSettings(prior, null, null, null))

        runtime.form.accept(ConnectionFormIntent.BaseUrlChanged(HTTPS_URL))
        runtime.form.save("new-token")
        advanceUntilIdle()
        runtime.form.confirmWarning("new-token")
        advanceUntilIdle()

        assertEquals(prior, runtime.read().baseUrl)
        assertEquals("old-token", tokens.currentTokenForReplacement()?.useForTrustedRequestBoundary { it })
        assertEquals(ConnectionFormMessage.ValidationFailed(
            com.murzify.bambuddyspool.core.settings.ConnectionValidationFailureReason.Unreachable
        ), runtime.form.state.value.message)
    }

    @Test
    fun httpDoesNotCreateASessionOrLoadCredentials() = runTest {
        val sessions = RecordingSessions(emptyList())
        val runtime = runtime(sessions = sessions)

        runtime.form.accept(ConnectionFormIntent.BaseUrlChanged("http://bambuddy.example"))
        runtime.form.test("new-token")
        advanceUntilIdle()

        assertEquals(0, sessions.created.size)
        assertEquals(ConnectionFormMessage.ValidationFailed(
            com.murzify.bambuddyspool.core.settings.ConnectionValidationFailureReason.Unreachable
        ), runtime.form.state.value.message)
    }

    private fun TestScope.runtime(
        sessions: RecordingSessions,
        tokenStore: SecureTokenStore = FakeTokenStore()
    ): MvpConnectionRuntime = MvpConnectionRuntime(
        scope = CoroutineScope(coroutineContext + SupervisorJob()),
        tokenStore = tokenStore,
        repositorySessions = sessions
    )
}

private class RecordingSessions(repositories: List<BambuddyRepository>) : MvpRepositorySessionFactory {
    private val queued = ArrayDeque(repositories)
    val created = mutableListOf<Triple<com.murzify.bambuddyspool.core.settings.CanonicalBaseUrl, SecretValue, ConnectionSettings>>()
    var closed = 0

    override fun create(
        baseUrl: com.murzify.bambuddyspool.core.settings.CanonicalBaseUrl,
        token: SecretValue,
        settings: ConnectionSettings
    ): MvpRepositorySession {
        created += Triple(baseUrl, token, settings)
        val repository = queued.removeFirst()
        return object : MvpRepositorySession {
            override val repository: BambuddyRepository = repository
            override fun close() {
                closed += 1
            }
        }
    }
}

private class FakeTokenStore(initial: SecretValue? = null) : SecureTokenStore {
    private var value = initial
    override suspend fun replaceToken(value: SecretValue) {
        this.value = value
    }
    override suspend fun clearToken() {
        value = null
    }
    override suspend fun hasToken(): Boolean = value != null
    override suspend fun currentTokenForReplacement(): SecretValue? = value
}

private class FakeRepository(private val printersFailure: Boolean = false) : BambuddyRepository {
    override suspend fun validateAuth(): BambuddyNetworkResult<Unit> = BambuddyNetworkResult.Success(Unit)
    override suspend fun getPrinters(): BambuddyNetworkResult<List<Printer>> = if (printersFailure) {
        BambuddyNetworkResult.Failure(BambuddyNetworkError.Transport(TransportFailureReason.Unknown))
    } else BambuddyNetworkResult.Success(listOf(printer()))
    override suspend fun getPrinterStatus(printerId: PrinterId): BambuddyNetworkResult<PrinterStatus> =
        BambuddyNetworkResult.Success(PrinterStatus(printer(), true, listOf(VirtualTray(255, "External"))))
    override suspend fun getSpools(includeArchived: Boolean): BambuddyNetworkResult<List<Spool>> =
        BambuddyNetworkResult.Success(listOf(spool()))
    override suspend fun getSpool(spoolId: SpoolId): BambuddyNetworkResult<Spool> = BambuddyNetworkResult.Success(spool())
    override suspend fun getAssignments(printerId: PrinterId?): BambuddyNetworkResult<List<Assignment>> =
        BambuddyNetworkResult.Success(emptyList())
    override suspend fun createAssignment(command: AssignmentCommand): BambuddyNetworkResult<Assignment> = error("Read-only MVP")
}

private fun canonical(value: String) = (parseCanonicalBaseUrl(value) as com.murzify.bambuddyspool.core.settings.BaseUrlParseResult.Success).value
private fun printer() = Printer(printerId(), "Printer")
private fun printerId() = requireNotNull(PrinterId.from(1))
private fun spool() = Spool(requireNotNull(SpoolId.from(1)), "Spool", null, "PLA", null, 100)
private const val HTTPS_URL = "https://bambuddy.example"
