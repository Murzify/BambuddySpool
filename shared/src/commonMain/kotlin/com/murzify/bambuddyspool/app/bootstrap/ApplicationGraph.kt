package com.murzify.bambuddyspool.app.bootstrap

import com.arkivanov.decompose.ComponentContext
import com.murzify.bambuddyspool.app.root.RootComponent
import com.murzify.bambuddyspool.core.application.ApplicationScope
import com.murzify.bambuddyspool.core.application.ComponentScope
import com.murzify.bambuddyspool.core.platform.AppDispatchers
import com.murzify.bambuddyspool.core.platform.ClipboardService
import com.murzify.bambuddyspool.core.platform.HapticsService
import com.murzify.bambuddyspool.core.platform.NfcService
import com.murzify.bambuddyspool.core.platform.PlatformNetworkFactory
import com.murzify.bambuddyspool.core.platform.PlatformServices
import com.murzify.bambuddyspool.core.platform.PlatformSettingsNavigator
import com.murzify.bambuddyspool.core.platform.SecureStorage
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(ApplicationScope::class)
interface ApplicationGraph {
    val secureStorage: SecureStorage
    val nfcService: NfcService
    val settingsNavigator: PlatformSettingsNavigator
    val clipboardService: ClipboardService
    val hapticsService: HapticsService
    val dispatchers: AppDispatchers
    val networkFactory: PlatformNetworkFactory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides secureStorage: SecureStorage,
            @Provides nfcService: NfcService,
            @Provides settingsNavigator: PlatformSettingsNavigator,
            @Provides clipboardService: ClipboardService,
            @Provides hapticsService: HapticsService,
            @Provides dispatchers: AppDispatchers,
            @Provides networkFactory: PlatformNetworkFactory,
        ): ApplicationGraph
    }
}

@DependencyGraph(ComponentScope::class)
internal interface ComponentGraph {
    val rootComponent: RootComponent

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides componentContext: ComponentContext,
        ): ComponentGraph
    }
}

class RootGraph internal constructor(
    val applicationGraph: ApplicationGraph,
    val rootComponent: RootComponent,
)

fun createApplicationGraph(platformServices: PlatformServices): ApplicationGraph =
    createGraphFactory<ApplicationGraph.Factory>().create(
        secureStorage = platformServices.secureStorage,
        nfcService = platformServices.nfc,
        settingsNavigator = platformServices.settings,
        clipboardService = platformServices.clipboard,
        hapticsService = platformServices.haptics,
        dispatchers = platformServices.dispatchers,
        networkFactory = platformServices.network,
    )

fun createRootGraph(
    componentContext: ComponentContext,
    platformServices: PlatformServices,
): RootGraph {
    val componentGraph = createGraphFactory<ComponentGraph.Factory>().create(componentContext)
    return RootGraph(
        applicationGraph = createApplicationGraph(platformServices),
        rootComponent = componentGraph.rootComponent,
    )
}
