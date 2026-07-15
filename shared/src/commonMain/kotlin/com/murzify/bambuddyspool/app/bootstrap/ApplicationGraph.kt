package com.murzify.bambuddyspool.app.bootstrap

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.murzify.bambuddyspool.app.connection.MvpConnectionRuntime
import com.murzify.bambuddyspool.app.root.RootComponent
import com.murzify.bambuddyspool.core.application.ComponentScope
import com.murzify.bambuddyspool.core.platform.NfcService
import com.murzify.bambuddyspool.core.projections.CacheProjectionRepository
import com.murzify.bambuddyspool.core.projections.EmptyCacheProjectionRepository
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@DependencyGraph(ComponentScope::class)
internal interface ComponentGraph {
    val rootComponent: RootComponent

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides componentContext: ComponentContext,
            @Provides nfcService: NfcService,
            @Provides spoolProjectionRepository: CacheProjectionRepository,
            @Provides connectionRuntime: MvpConnectionRuntime
        ): ComponentGraph
    }
}

/** Retains the shared root component exposed to a platform shell. */
class RootGraph internal constructor(val rootComponent: RootComponent)

/** Builds a retained shared root from an explicit lifecycle context and NFC capability. */
fun createRootGraph(
    componentContext: ComponentContext,
    nfcService: NfcService,
    spoolProjectionRepository: CacheProjectionRepository = EmptyCacheProjectionRepository
): RootGraph {
    val connectionRuntime = MvpConnectionRuntime(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    componentContext.lifecycle.doOnDestroy(connectionRuntime::close)
    val componentGraph = createGraphFactory<ComponentGraph.Factory>().create(
        componentContext = componentContext,
        nfcService = nfcService,
        spoolProjectionRepository = if (spoolProjectionRepository === EmptyCacheProjectionRepository) {
            connectionRuntime.cache
        } else {
            spoolProjectionRepository
        },
        connectionRuntime = connectionRuntime
    )
    return RootGraph(rootComponent = componentGraph.rootComponent)
}
