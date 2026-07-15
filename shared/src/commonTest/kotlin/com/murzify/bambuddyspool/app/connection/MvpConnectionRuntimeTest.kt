package com.murzify.bambuddyspool.app.connection

import com.murzify.bambuddyspool.core.projections.CacheProjectionError
import com.murzify.bambuddyspool.core.projections.CacheProjectionState
import com.murzify.bambuddyspool.core.projections.PageRequest
import com.murzify.bambuddyspool.core.settings.ConnectionSettings
import com.murzify.bambuddyspool.core.settings.ConnectionValidationFailureReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class MvpConnectionRuntimeTest {

    @Test
    fun unconfiguredProductionGraphExposesTypedNoConnectionCacheState() = runTest {
        val runtime = MvpConnectionRuntime(CoroutineScope(SupervisorJob()))

        assertEquals(
            CacheProjectionState.FatalErrorWithoutCache(CacheProjectionError.NoConfiguredConnection),
            runtime.cache.observeDefaultSpoolPage(PageRequest(limit = 1, offset = 0)).first()
        )

        runtime.close()
    }

    @Test
    fun securityGateDoesNotCreateASecretOrConnectionState() = runTest {
        val runtime = MvpConnectionRuntime(CoroutineScope(coroutineContext + SupervisorJob()))

        runtime.form.accept(ConnectionFormIntent.BaseUrlChanged("https://bambuddy.example"))
        runtime.form.test("not-retained")
        testScheduler.advanceUntilIdle()

        assertEquals(ConnectionSettings.Empty, runtime.read())
        assertEquals(
            ConnectionFormMessage.ValidationFailed(ConnectionValidationFailureReason.SecurityPolicyNotReady),
            runtime.form.state.value.message
        )

        runtime.close()
    }
}
