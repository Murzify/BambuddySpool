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
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

data class RootState(val destination: RootDestination = RootDestination.Home) {
    val title = destination.title
}
sealed interface RootIntent {
    data class Select(val destination: RootDestination) : RootIntent
}
sealed interface RootEffect {
    data class Navigate(val destination: RootDestination) : RootEffect
}

object RootReducer : Reducer<RootState, RootIntent, RootEffect> {
    override fun reduce(state: RootState, intent: RootIntent): Reduction<RootState, RootEffect> = when (intent) {
        is RootIntent.Select -> Reduction(
            RootState(intent.destination),
            listOf(RootEffect.Navigate(intent.destination))
        )
    }
}

@Serializable
private enum class RootConfig { Home, Spools, Printers, Settings }

data class RootChild(val title: String)

@Inject
@SingleIn(ComponentScope::class)
class RootComponent(componentContext: ComponentContext) :
    ComponentContext by componentContext,
    UdfComponent<RootState, RootIntent> {
    private val navigation = StackNavigation<RootConfig>()
    private val mutableState = MutableStateFlow(RootState())
    override val state: StateFlow<RootState> = mutableState.asStateFlow()

    private val childStack = childStack(
        source = navigation,
        serializer = RootConfig.serializer(),
        initialConfiguration = RootConfig.Home,
        handleBackButton = true
    ) { config, _ -> RootChild(config.name) }

    override fun accept(intent: RootIntent) {
        val reduction = RootReducer.reduce(mutableState.value, intent)
        mutableState.value = reduction.state
        reduction.effects.forEach(::handle)
    }

    private fun handle(effect: RootEffect) = when (effect) {
        is RootEffect.Navigate -> navigation.bringToFront(RootConfig.valueOf(effect.destination.name))
    }
}
