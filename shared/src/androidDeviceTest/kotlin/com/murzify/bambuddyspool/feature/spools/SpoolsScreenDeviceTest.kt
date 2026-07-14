package com.murzify.bambuddyspool.feature.spools

import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.projections.CacheAvailability
import com.murzify.bambuddyspool.core.projections.CacheProjectionRepository
import com.murzify.bambuddyspool.core.projections.CacheProjectionState
import com.murzify.bambuddyspool.core.projections.MutationAvailability
import com.murzify.bambuddyspool.core.projections.PageRequest
import com.murzify.bambuddyspool.core.projections.PagedResult
import com.murzify.bambuddyspool.core.projections.PrinterSlotProjection
import com.murzify.bambuddyspool.core.projections.PrinterSummaryProjection
import com.murzify.bambuddyspool.core.projections.SpoolSearchQuery
import com.murzify.bambuddyspool.core.projections.SpoolSummaryProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalTestApi::class)
class SpoolsScreenDeviceTest {
    @Test
    fun filtersRemainReadableAtCompactWidth() = runComposeUiTest {
        val spool = SpoolSummaryProjection(
            requireNotNull(SpoolId.from(8)),
            "Carbon", null, "PLA", "Black", 450, null, null, null
        )
        setContent {
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = androidx.compose.ui.Modifier.width(320.dp)
            ) {
                SpoolsScreen(component(spool, available = true), {})
            }
        }

        onNodeWithText("Include inactive").assertIsDisplayed()
        onNodeWithText("Include archived").assertIsDisplayed()
        onNodeWithText("Include empty").assertIsDisplayed()
    }

    @Test
    fun freshActionsAreEnabledAndStaleActionsAreDisabled() = runComposeUiTest {
        val spool =
            SpoolSummaryProjection(
                requireNotNull(
                    SpoolId.from(7)
                ),
                "Carbon", null, "PLA", "Black", 450, null, null, null
            )
        val fresh = component(spool, available = true)
        val intents = mutableListOf<com.murzify.bambuddyspool.app.root.RootIntent>()
        fresh.accept(SpoolsIntent.OpenDetail(spool.id))
        setContent { SpoolsScreen(fresh, intents::add) }
        onNodeWithTag(SPOOL_ASSIGN_TAG).assertIsEnabled().performClick()
        onNodeWithTag(SPOOL_LINK_TAG).assertIsEnabled().performClick()
        assertEquals(2, intents.size)

        val stale = component(spool, available = false)
        stale.accept(SpoolsIntent.OpenDetail(spool.id))
        setContent { SpoolsScreen(stale, {}) }
        onNodeWithTag(SPOOL_ASSIGN_TAG).assertIsNotEnabled()
        onNodeWithTag(SPOOL_LINK_TAG).assertIsNotEnabled()
    }

    private fun component(spool: SpoolSummaryProjection, available: Boolean) = SpoolsComponent(
        DefaultComponentContext(
            LifecycleRegistry().apply {
                onCreate()
                onStart()
                onResume()
            }
        ),
        DeviceProjectionRepository(spool, available)
    )
}

private class DeviceProjectionRepository(private val spool: SpoolSummaryProjection, available: Boolean) :
    CacheProjectionRepository {
    private val availability = CacheAvailability(
        isStale = !available,
        nonBlockingError = null,
        mutation = if (available) {
            MutationAvailability.Available(
                requireNotNull(SnapshotGeneration.from(1))
            )
        } else {
            MutationAvailability.Disabled(
                com.murzify.bambuddyspool.core.projections.MutationDisabledReason.Offline
            )
        }
    )

    override fun observePrinters(): Flow<CacheProjectionState<List<PrinterSummaryProjection>>> =
        flowOf(CacheProjectionState.InitialLoading)
    override fun observePrinterSlots(
        printerId: com.murzify.bambuddyspool.core.domain.PrinterId
    ): Flow<CacheProjectionState<List<PrinterSlotProjection>>> = flowOf(CacheProjectionState.InitialLoading)
    override fun observeDefaultSpoolPage(
        page: PageRequest
    ): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>> = page()
    override fun observeSpoolSearch(
        queries: Flow<SpoolSearchQuery>,
        page: PageRequest
    ): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>> = page()
    override fun observeSpool(spoolId: SpoolId): Flow<CacheProjectionState<SpoolSummaryProjection?>> =
        flowOf(CacheProjectionState.Content(spool, availability))

    private fun page(): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>> =
        flowOf(CacheProjectionState.Content(PagedResult(listOf(spool), PageRequest(100, 0)), availability))
}
