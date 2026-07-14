@file:Suppress("ktlint:standard:max-line-length")

package com.murzify.bambuddyspool.feature.printers

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.murzify.bambuddyspool.app.root.RootIntent
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SlotKind
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

private typealias DeviceSpoolPage =
    Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>>

@OptIn(ExperimentalTestApi::class)
class PrintersScreenDeviceTest {
    @Test
    fun externalSlotCanCreateManualIntentButAmsRemainsReadOnly() = runComposeUiTest {
        val component = component(available = true)
        val intents = mutableListOf<RootIntent>()
        setContent { PrintersScreen(component, requireNotNull(SpoolId.from(9)), intents::add) }

        onNodeWithText("A1").performClick()
        onNodeWithTag(PRINTER_EXTERNAL_ASSIGN_TAG).assertIsEnabled().performClick()
        onNodeWithTag(PRINTER_AMS_TAG).assertIsDisplayed()
        assertEquals(1, intents.filterIsInstance<RootIntent.CreateManualAssignment>().size)
    }

    @Test
    fun staleProjectionDisablesExternalSlotAssignment() = runComposeUiTest {
        val component = component(available = false)
        setContent { PrintersScreen(component, requireNotNull(SpoolId.from(9)), {}) }

        onNodeWithText("A1").performClick()
        onNodeWithTag(PRINTER_EXTERNAL_ASSIGN_TAG).assertIsNotEnabled()
    }

    private fun component(available: Boolean) = PrintersComponent(
        DefaultComponentContext(
            LifecycleRegistry().apply {
                onCreate()
                onStart()
                onResume()
            }
        ),
        PrintersDeviceRepository(available)
    )
}

private class PrintersDeviceRepository(available: Boolean) : CacheProjectionRepository {
    private val printerId = requireNotNull(PrinterId.from(1))
    private val availability = CacheAvailability(
        isStale = !available,
        nonBlockingError = null,
        mutation = if (available) {
            MutationAvailability.Available(requireNotNull(SnapshotGeneration.from(1)))
        } else {
            MutationAvailability.Disabled(com.murzify.bambuddyspool.core.projections.MutationDisabledReason.Offline)
        }
    )

    override fun observePrinters(): Flow<CacheProjectionState<List<PrinterSummaryProjection>>> = flowOf(
        CacheProjectionState.Content(listOf(PrinterSummaryProjection(printerId, "A1", 1, 1, true)), availability)
    )

    override fun observePrinterSlots(printerId: PrinterId): Flow<CacheProjectionState<List<PrinterSlotProjection>>> =
        flowOf(
            CacheProjectionState.Content(
                listOf(
                    PrinterSlotProjection(
                        requireNotNull(SlotKey.from(printerId, 255, 0)),
                        "A1",
                        SlotKind.External,
                        "External",
                        null
                    ),
                    PrinterSlotProjection(
                        requireNotNull(SlotKey.from(printerId, 0, 0)),
                        "A1",
                        SlotKind.Ams,
                        "AMS 1",
                        null
                    )
                ),
                availability
            )
        )

    override fun observeDefaultSpoolPage(page: PageRequest): DeviceSpoolPage = error("Not used")
    override fun observeSpoolSearch(queries: Flow<SpoolSearchQuery>, page: PageRequest): DeviceSpoolPage =
        error("Not used")
    override fun observeSpool(spoolId: SpoolId): Flow<CacheProjectionState<SpoolSummaryProjection?>> = error("Not used")
}
