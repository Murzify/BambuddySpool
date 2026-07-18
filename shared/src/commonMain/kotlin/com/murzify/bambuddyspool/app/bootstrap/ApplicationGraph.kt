package com.murzify.bambuddyspool.app.bootstrap

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.murzify.bambuddyspool.app.connection.MvpConnectionRuntime
import com.murzify.bambuddyspool.app.root.RootComponent
import com.murzify.bambuddyspool.core.application.ComponentScope
import com.murzify.bambuddyspool.core.platform.NfcService
import com.murzify.bambuddyspool.core.projections.CacheProjectionRepository
import com.murzify.bambuddyspool.core.projections.EmptyCacheProjectionRepository
import com.murzify.bambuddyspool.core.security.SecureTokenStore
import com.murzify.bambuddyspool.feature.tagmutation.LiveTagMutationBridge
import com.murzify.bambuddyspool.feature.tagmutation.UnavailableLiveTagMutationBridge
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
            @Provides connectionRuntime: MvpConnectionRuntime,
            @Provides liveTagMutationBridge: LiveTagMutationBridge
        ): ComponentGraph
    }
}

/** Retains the shared root component exposed to a platform shell. */
class RootGraph internal constructor(val rootComponent: RootComponent)

/** Builds a retained shared root from an explicit lifecycle context and NFC capability. */
fun createRootGraph(
    componentContext: ComponentContext,
    nfcService: NfcService,
    spoolProjectionRepository: CacheProjectionRepository = EmptyCacheProjectionRepository,
    secureTokenStore: SecureTokenStore? = null,
    liveTagMutationBridge: LiveTagMutationBridge = UnavailableLiveTagMutationBridge
): RootGraph {
    val connectionRuntime = MvpConnectionRuntime(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        tokenStore = secureTokenStore ?: RejectedTokenStore
    )
    componentContext.lifecycle.doOnDestroy(connectionRuntime::close)
    val componentGraph = createGraphFactory<ComponentGraph.Factory>().create(
        componentContext = componentContext,
        nfcService = nfcService,
        spoolProjectionRepository = if (spoolProjectionRepository === EmptyCacheProjectionRepository) {
            connectionRuntime.cache
        } else {
            spoolProjectionRepository
        },
        connectionRuntime = connectionRuntime,
        liveTagMutationBridge = liveTagMutationBridge
    )
    return RootGraph(rootComponent = componentGraph.rootComponent)
}

/** Used only by non-Android MVP shells until their platform storage is implemented. */
private object RejectedTokenStore : SecureTokenStore {
    override suspend fun replaceToken(value: com.murzify.bambuddyspool.core.security.SecretValue): Nothing =
        error("Android SEC-001 storage is unavailable in this shell.")

    override suspend fun clearToken(): Nothing = error("Android SEC-001 storage is unavailable in this shell.")

    override suspend fun hasToken(): Boolean = false

    override suspend fun currentTokenForReplacement(): com.murzify.bambuddyspool.core.security.SecretValue? = null
}
