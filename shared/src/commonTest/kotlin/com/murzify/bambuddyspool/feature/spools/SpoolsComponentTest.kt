package com.murzify.bambuddyspool.feature.spools

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.projections.EmptyCacheProjectionRepository
import com.murzify.bambuddyspool.core.projections.SpoolListFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpoolsComponentTest {
    @Test
    fun reducerUpdatesSearchFiltersAndSafeDetailSelection() {
        val spoolId = requireNotNull(SpoolId.from(42))
        val searched = SpoolsReducer.reduce(SpoolsState(), SpoolsIntent.SearchChanged(" matte pla ")).state
        val filtered = SpoolsReducer.reduce(
            searched,
            SpoolsIntent.FiltersChanged(SpoolListFilters(includeArchived = true, includeEmpty = true))
        ).state
        val opened = SpoolsReducer.reduce(filtered, SpoolsIntent.OpenDetail(spoolId)).state

        assertEquals(" matte pla ", opened.query)
        assertEquals(SpoolListFilters(includeArchived = true, includeEmpty = true), opened.filters)
        assertEquals(spoolId, opened.selectedSpoolId)
        assertNull(SpoolsReducer.reduce(opened, SpoolsIntent.CloseDetail).state.selectedSpoolId)
    }

    @Test
    fun searchFiltersAndDetailRouteRestoreWithoutWorkflowState() {
        val stateKeeper = StateKeeperDispatcher()
        val first = component(stateKeeper)
        first.accept(SpoolsIntent.SearchChanged("carbon"))
        first.accept(SpoolsIntent.FiltersChanged(SpoolListFilters(includeInactive = true)))
        first.accept(SpoolsIntent.OpenDetail(requireNotNull(SpoolId.from(9))))

        val restored = component(StateKeeperDispatcher(stateKeeper.save()))

        assertEquals("carbon", restored.state.value.query)
        assertEquals(SpoolListFilters(includeInactive = true), restored.state.value.filters)
        assertEquals(9, restored.state.value.selectedSpoolId?.value)
    }

    private fun component(stateKeeper: StateKeeperDispatcher): SpoolsComponent = SpoolsComponent(
        componentContext = DefaultComponentContext(activeLifecycle(), stateKeeper = stateKeeper),
        repository = EmptyCacheProjectionRepository
    )

    private fun activeLifecycle(): LifecycleRegistry = LifecycleRegistry().apply {
        onCreate()
        onStart()
        onResume()
    }
}
