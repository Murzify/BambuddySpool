package com.murzify.bambuddyspool.app.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.router.stack.pushToFront
import com.murzify.bambuddyspool.app.navigation.RootDestination
import com.murzify.bambuddyspool.core.application.ComponentScope
import com.murzify.bambuddyspool.core.application.Reducer
import com.murzify.bambuddyspool.core.application.Reduction
import com.murzify.bambuddyspool.core.application.UdfComponent
import com.murzify.bambuddyspool.core.assignment.AssignmentContext
import com.murzify.bambuddyspool.core.domain.AssignmentSource
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.nfc.NfcScanCoordinationEffect
import com.murzify.bambuddyspool.core.nfc.NfcScanCoordinationEvent
import com.murzify.bambuddyspool.core.nfc.NfcScanCoordinationState
import com.murzify.bambuddyspool.core.nfc.NfcScanCoordinator
import com.murzify.bambuddyspool.core.nfc.NfcSessionId
import com.murzify.bambuddyspool.core.platform.NfcAvailability
import com.murzify.bambuddyspool.core.platform.NfcObservation
import com.murzify.bambuddyspool.core.platform.NfcService
import com.murzify.bambuddyspool.core.projections.CacheProjectionRepository
import com.murzify.bambuddyspool.feature.assignment.AssignmentIntent
import com.murzify.bambuddyspool.feature.assignment.CombinedAssignmentConfirmation
import com.murzify.bambuddyspool.feature.assignment.toCombinedConfirmation
import com.murzify.bambuddyspool.feature.printers.PrintersComponent
import com.murzify.bambuddyspool.feature.spools.SpoolsComponent
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

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
    val connectionState: HomeConnectionState = HomeConnectionState.NotConfigured,
    val nfcState: HomeNfcState = HomeNfcState.Unavailable,
    val transientWorkflow: RootTransientWorkflow? = null,
    /** The accepted scan is transient input for NFC coordination and is never restored or replayed. */
    val pendingNfcObservation: NfcObservation? = null,
    /** Opaque coordinator identity for the current process-local NFC workflow; it is never saved. */
    val activeNfcSessionId: NfcSessionId? = null,
    val pendingManualSpoolId: SpoolId? = null,
    val assignmentIntent: AssignmentIntent? = null,
    val assignmentConfirmation: CombinedAssignmentConfirmation? = null
)

/** Inputs accepted by the shared root component. */
sealed interface RootIntent {
    data class Select(val destination: RootDestination) : RootIntent
    data class OpenDetail(val destination: RootDestination, val id: Long) : RootIntent
    data class UpdateHomeStatus(val connectionState: HomeConnectionState, val nfcState: HomeNfcState) : RootIntent
    data class StartManualAssignment(val spoolId: Long) : RootIntent
    data class CreateManualAssignment(
        val spoolId: SpoolId,
        val slot: SlotKey,
        val expectedSnapshotGeneration: SnapshotGeneration
    ) : RootIntent

    /** Shared hand-off used by manual and future NFC resolution; it must stay transient. */
    data class StartAssignment(val intent: AssignmentIntent) : RootIntent

    /** Platform-neutral NFC entry hand-off; Android objects must never cross this boundary. */
    data class BeginNfcScan(val observation: NfcObservation, val sessionId: NfcSessionId? = null) : RootIntent

    /** Fresh server context only; this transient dialog is never restorable or an authorization by itself. */
    data class ShowAssignmentConfirmation(val context: AssignmentContext) : RootIntent
    data object ConfirmAssignment : RootIntent
    data object CancelAssignmentConfirmation : RootIntent
    data class StartTagLink(val spoolId: Long?) : RootIntent

    data class ShowTransient(val workflow: RootTransientWorkflow) : RootIntent
    data object DismissTransient : RootIntent
}

internal sealed interface RootEffect {
    data class Navigate(val destination: RootDestination) : RootEffect
    data class OpenDestinationDetail(val destination: RootDestination, val id: Long) : RootEffect
}

internal object RootReducer : Reducer<RootState, RootIntent, RootEffect> {
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun reduce(state: RootState, intent: RootIntent): Reduction<RootState, RootEffect> = when (intent) {
        is RootIntent.Select -> if (state.destination == intent.destination) {
            Reduction(state)
        } else {
            Reduction(state.copy(destination = intent.destination), listOf(RootEffect.Navigate(intent.destination)))
        }

        is RootIntent.OpenDetail -> {
            require(intent.id > 0) { "Detail identifiers must be positive." }
            val navigationEffects = if (state.destination == intent.destination) {
                emptyList()
            } else {
                listOf(RootEffect.Navigate(intent.destination))
            }
            Reduction(
                state.copy(destination = intent.destination),
                navigationEffects + RootEffect.OpenDestinationDetail(intent.destination, intent.id)
            )
        }

        is RootIntent.UpdateHomeStatus -> Reduction(
            state.copy(connectionState = intent.connectionState, nfcState = intent.nfcState)
        )

        is RootIntent.StartManualAssignment -> {
            require(intent.spoolId > 0) { "Spool identifiers must be positive." }
            Reduction(
                state.copy(
                    destination = RootDestination.Printers,
                    pendingManualSpoolId = requireNotNull(SpoolId.from(intent.spoolId)),
                    transientWorkflow = null,
                    pendingNfcObservation = null,
                    activeNfcSessionId = null,
                    assignmentIntent = null,
                    assignmentConfirmation = null
                ),
                listOf(RootEffect.Navigate(RootDestination.Printers))
            )
        }

        is RootIntent.CreateManualAssignment -> Reduction(
            state.copy(
                pendingManualSpoolId = null,
                transientWorkflow = RootTransientWorkflow.Processing,
                pendingNfcObservation = null,
                activeNfcSessionId = null,
                assignmentIntent = AssignmentIntent(
                    spoolId = intent.spoolId,
                    slot = intent.slot,
                    source = AssignmentSource.Manual,
                    expectedSnapshotGeneration = intent.expectedSnapshotGeneration
                ),
                assignmentConfirmation = null
            )
        )

        is RootIntent.StartAssignment -> Reduction(
            state.copy(
                pendingManualSpoolId = null,
                transientWorkflow = RootTransientWorkflow.Processing,
                activeNfcSessionId = null,
                assignmentIntent = intent.intent,
                assignmentConfirmation = null
            )
        )

        is RootIntent.BeginNfcScan -> {
            require(intent.observation.fingerprint.isNotBlank()) { "NFC fingerprints must not be blank." }
            require(!intent.observation.payload.isNullOrBlank()) { "Accepted NFC payloads must not be blank." }
            Reduction(
                state.copy(
                    transientWorkflow = RootTransientWorkflow.Processing,
                    pendingNfcObservation = intent.observation,
                    activeNfcSessionId = intent.sessionId,
                    pendingManualSpoolId = null,
                    assignmentIntent = null,
                    assignmentConfirmation = null
                )
            )
        }

        is RootIntent.ShowAssignmentConfirmation -> Reduction(
            state.copy(
                transientWorkflow = RootTransientWorkflow.Confirmation,
                assignmentConfirmation = intent.context.toCombinedConfirmation()
            )
        )

        RootIntent.ConfirmAssignment -> Reduction(
            state.copy(transientWorkflow = RootTransientWorkflow.Processing, assignmentConfirmation = null)
        )

        RootIntent.CancelAssignmentConfirmation -> Reduction(
            state.copy(
                transientWorkflow = null,
                pendingNfcObservation = null,
                activeNfcSessionId = null,
                assignmentIntent = null,
                assignmentConfirmation = null
            )
        )

        is RootIntent.StartTagLink -> Reduction(
            state.copy(
                transientWorkflow = RootTransientWorkflow.TagMutation,
                pendingNfcObservation = null,
                activeNfcSessionId = null
            )
        )

        is RootIntent.ShowTransient -> Reduction(state.copy(transientWorkflow = intent.workflow))
        RootIntent.DismissTransient -> Reduction(
            state.copy(
                transientWorkflow = null,
                pendingNfcObservation = null,
                activeNfcSessionId = null,
                assignmentIntent = null,
                assignmentConfirmation = null
            )
        )
    }
}

@Serializable
private enum class RootConfig { Home, Spools, Printers, Settings }

/** Persisted state deliberately contains only the selected primary destination. */
@Serializable
private data class RestoredRootState(val destination: RootDestination)

/** A safe route in one primary destination's own Decompose child stack. */
@Serializable
sealed interface DestinationRoute {
    @Serializable
    data object List : DestinationRoute

    @Serializable
    data class Detail(val id: Long) : DestinationRoute
}

private data class DestinationChild(val route: DestinationRoute)

/**
 * Owns one primary destination's independent safe navigation history.
 *
 * Decompose saves this stack through the destination child context. Credentials, active NFC sessions and operation
 * authorizations are intentionally absent from [DestinationRoute], so restoration cannot replay an operation.
 */
internal class DestinationStackComponent(componentContext: ComponentContext) : ComponentContext by componentContext {
    private val navigation = StackNavigation<DestinationRoute>()
    private val stack = childStack(
        source = navigation,
        serializer = DestinationRoute.serializer(),
        initialConfiguration = DestinationRoute.List,
        handleBackButton = true
    ) { route, _ -> DestinationChild(route) }

    fun openDetail(id: Long) {
        require(id > 0) { "Detail identifiers must be positive." }
        navigation.pushNew(DestinationRoute.Detail(id))
    }

    fun history(): List<DestinationRoute> = stack.value.items.map { it.configuration }
}

private data class RootChild(val component: DestinationStackComponent)

/** Shared Decompose root and UDF boundary rendered by both platform shells. */
@Inject
@SingleIn(ComponentScope::class)
class RootComponent(
    componentContext: ComponentContext,
    nfcService: NfcService,
    spoolProjectionRepository: CacheProjectionRepository
) : ComponentContext by componentContext,
    UdfComponent<RootState, RootIntent> {
    /** The shared browser owns safe search/filter/detail restoration for the Spools destination. */
    val spoolsComponent = SpoolsComponent(componentContext, spoolProjectionRepository)

    /** The shared printer browser uses only Room-backed cache projections. */
    val printersComponent = PrintersComponent(componentContext, spoolProjectionRepository)
    private val navigation = StackNavigation<RootConfig>()
    private val destinationComponents = mutableMapOf<RootDestination, DestinationStackComponent>()
    private val restored = stateKeeper.consume("root-navigation", RestoredRootState.serializer())
    private val mutableState = MutableStateFlow(
        RootState(
            destination = restored?.destination ?: RootDestination.Home,
            nfcState = nfcService.availability.toHomeNfcState()
        )
    )
    override val state: StateFlow<RootState> = mutableState.asStateFlow()

    /** NFC coordination is process-local; state keeper never sees accepted scans or mutation boundaries. */
    private var nfcCoordination = NfcScanCoordinationState()

    private val childStack = childStack(
        source = navigation,
        serializer = RootConfig.serializer(),
        initialConfiguration = mutableState.value.destination.toConfig(),
        handleBackButton = true
    ) { config, childContext ->
        DestinationStackComponent(childContext).also { component ->
            destinationComponents[config.toDestination()] = component
        }.let(::RootChild)
    }

    init {
        stateKeeper.register("root-navigation", RestoredRootState.serializer()) {
            mutableState.value.let { current ->
                RestoredRootState(destination = current.destination)
            }
        }
    }

    override fun accept(intent: RootIntent) {
        if (intent is RootIntent.BeginNfcScan) {
            acceptNfcObservation(intent.observation)
            return
        }
        val reduction = RootReducer.reduce(mutableState.value, intent)
        mutableState.value = reduction.state
        reduction.effects.forEach(::handle)
    }

    /** Future NFC assignment orchestration must call this immediately before its first POST. */
    fun onNfcPostStarted(sessionId: NfcSessionId) {
        applyNfcCoordination(NfcScanCoordinationEvent.PostStarted(sessionId))
    }

    /** Finishes a matching NFC operation and starts at most the one newest scan retained after its POST boundary. */
    fun onNfcWorkflowCompleted(sessionId: NfcSessionId, succeeded: Boolean) {
        val effect = applyNfcCoordination(NfcScanCoordinationEvent.Completed(sessionId, succeeded))
        if (effect !is NfcScanCoordinationEffect.Start && effect !is NfcScanCoordinationEffect.IgnoredStaleCallback) {
            applyRoot(
                RootIntent.ShowTransient(if (succeeded) RootTransientWorkflow.Success else RootTransientWorkflow.Error)
            )
        }
    }

    private fun acceptNfcObservation(observation: NfcObservation) {
        applyNfcCoordination(NfcScanCoordinationEvent.Scan(observation))
    }

    private fun applyNfcCoordination(event: NfcScanCoordinationEvent): NfcScanCoordinationEffect? {
        val reduction = NfcScanCoordinator.reduce(nfcCoordination, event)
        nfcCoordination = reduction.state
        when (val effect = reduction.effect) {
            is NfcScanCoordinationEffect.Start -> applyRoot(
                RootIntent.BeginNfcScan(effect.session.observation, effect.session.id)
            )
            is NfcScanCoordinationEffect.ReplacedBeforePost -> applyRoot(
                RootIntent.BeginNfcScan(effect.started.observation, effect.started.id)
            )
            else -> Unit
        }
        return reduction.effect
    }

    private fun applyRoot(intent: RootIntent) {
        val reduction = RootReducer.reduce(mutableState.value, intent)
        mutableState.value = reduction.state
        reduction.effects.forEach(::handle)
    }

    private fun handle(effect: RootEffect) = when (effect) {
        is RootEffect.Navigate -> navigation.pushToFront(effect.destination.toConfig())
        is RootEffect.OpenDestinationDetail -> destinationComponents.getValue(effect.destination).openDetail(effect.id)
    }

    /** Exposed for deterministic navigation tests and future feature hosts, never for persisted operation state. */
    internal fun destinationHistory(destination: RootDestination): List<DestinationRoute> =
        destinationComponents.getValue(destination).history()
}

private fun RootDestination.toConfig(): RootConfig = RootConfig.valueOf(name)

private fun RootConfig.toDestination(): RootDestination = RootDestination.valueOf(name)

private fun NfcAvailability.toHomeNfcState(): HomeNfcState = when (this) {
    NfcAvailability.Available -> HomeNfcState.Available
    NfcAvailability.Unavailable -> HomeNfcState.Unavailable
    NfcAvailability.Disabled -> HomeNfcState.Disabled
}
