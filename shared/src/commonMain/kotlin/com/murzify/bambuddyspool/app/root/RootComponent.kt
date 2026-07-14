package com.murzify.bambuddyspool.app.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.murzify.bambuddyspool.app.navigation.RootDestination
import com.murzify.bambuddyspool.core.application.ComponentScope
import com.murzify.bambuddyspool.core.application.Reducer
import com.murzify.bambuddyspool.core.application.Reduction
import com.murzify.bambuddyspool.core.application.UdfComponent
import com.murzify.bambuddyspool.core.platform.NfcService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** Safe child-stack state; feature-specific screens may extend it without leaking platform values. */
@Serializable
data class DestinationStackState(val selectedDetailId: Long? = null)

/** Connection status rendered by Home. Network operations update this through explicit intents. */
@Serializable
enum class HomeConnectionState {
    NotConfigured,
    Online,
    Offline,
    Stale
}

/** Platform-neutral NFC availability exposed by Home. */
@Serializable
enum class HomeNfcState {
    Available,
    Unavailable,
    Disabled
}

/** Transient root flows are intentionally excluded from restoration and mutation replay. */
@Serializable
enum class RootTransientWorkflow {
    Processing,
    Confirmation,
    Success,
    Error,
    TagMutation
}

/** Immutable state exposed by the shared root component. */
data class RootState(
    val destination: RootDestination = RootDestination.Home,
    val stacks: Map<RootDestination, DestinationStackState> = RootDestination.entries.associateWith {
        DestinationStackState()
    },
    val connectionState: HomeConnectionState = HomeConnectionState.NotConfigured,
    val nfcState: HomeNfcState = HomeNfcState.Unavailable,
    val transientWorkflow: RootTransientWorkflow? = null
) {
    val selectedDetailId: Long? get() = stacks.getValue(destination).selectedDetailId
}

/** Inputs accepted by the shared root component. */
sealed interface RootIntent {
    data class Select(val destination: RootDestination) : RootIntent
    data class OpenDetail(val destination: RootDestination, val id: Long) : RootIntent
    data class UpdateHomeStatus(val connectionState: HomeConnectionState, val nfcState: HomeNfcState) : RootIntent

    data class ShowTransient(val workflow: RootTransientWorkflow) : RootIntent
    data object DismissTransient : RootIntent
}

internal sealed interface RootEffect {
    data class Navigate(val destination: RootDestination) : RootEffect
}

internal object RootReducer : Reducer<RootState, RootIntent, RootEffect> {
    override fun reduce(state: RootState, intent: RootIntent): Reduction<RootState, RootEffect> = when (intent) {
        is RootIntent.Select -> Reduction(
            state.copy(destination = intent.destination),
            listOf(RootEffect.Navigate(intent.destination))
        )

        is RootIntent.OpenDetail -> {
            require(intent.id > 0) { "Detail identifiers must be positive." }
            Reduction(
                state.copy(
                    destination = intent.destination,
                    stacks = state.stacks + (intent.destination to DestinationStackState(intent.id))
                ),
                listOf(RootEffect.Navigate(intent.destination))
            )
        }

        is RootIntent.UpdateHomeStatus -> Reduction(
            state.copy(connectionState = intent.connectionState, nfcState = intent.nfcState)
        )

        is RootIntent.ShowTransient -> Reduction(state.copy(transientWorkflow = intent.workflow))
        RootIntent.DismissTransient -> Reduction(state.copy(transientWorkflow = null))
    }
}

@Serializable
private enum class RootConfig { Home, Spools, Printers, Settings }

/** Persisted state deliberately contains only navigation and safe feature-detail selections. */
@Serializable
private data class RestoredRootState(
    val destination: RootDestination,
    val stacks: Map<RootDestination, DestinationStackState>
)

private data class RootChild(val destination: RootDestination)

/** Shared Decompose root and UDF boundary rendered by both platform shells. */
@Inject
@SingleIn(ComponentScope::class)
class RootComponent(componentContext: ComponentContext, nfcService: NfcService) :
    ComponentContext by componentContext,
    UdfComponent<RootState, RootIntent> {
    private val navigation = StackNavigation<RootConfig>()
    private val restored = stateKeeper.consume("root-navigation", RestoredRootState.serializer())
    private val mutableState = MutableStateFlow(
        RootState(
            destination = restored?.destination ?: RootDestination.Home,
            stacks = restored?.stacks ?: RootDestination.entries.associateWith { DestinationStackState() },
            nfcState = if (nfcService.isAvailable) HomeNfcState.Available else HomeNfcState.Unavailable
        )
    )
    override val state: StateFlow<RootState> = mutableState.asStateFlow()

    private val childStack = childStack(
        source = navigation,
        serializer = RootConfig.serializer(),
        initialConfiguration = mutableState.value.destination.toConfig(),
        handleBackButton = true
    ) { config, _ -> RootChild(config.toDestination()) }

    init {
        stateKeeper.register("root-navigation", RestoredRootState.serializer()) {
            mutableState.value.let { current ->
                RestoredRootState(destination = current.destination, stacks = current.stacks)
            }
        }
    }

    override fun accept(intent: RootIntent) {
        val reduction = RootReducer.reduce(mutableState.value, intent)
        mutableState.value = reduction.state
        reduction.effects.forEach(::handle)
    }

    private fun handle(effect: RootEffect) = when (effect) {
        is RootEffect.Navigate -> navigation.bringToFront(effect.destination.toConfig())
    }
}

private fun RootDestination.toConfig(): RootConfig = RootConfig.valueOf(name)

private fun RootConfig.toDestination(): RootDestination = RootDestination.valueOf(name)
