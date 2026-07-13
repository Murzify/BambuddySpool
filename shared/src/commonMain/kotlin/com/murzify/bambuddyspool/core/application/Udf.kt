package com.murzify.bambuddyspool.core.application

import kotlinx.coroutines.flow.StateFlow

/** Result of a pure reducer step: new immutable state plus ordered effects. */
data class Reduction<State : Any, Effect : Any>(val state: State, val effects: List<Effect> = emptyList())

/** Pure state transition contract for a unidirectional data-flow component. */
fun interface Reducer<State : Any, Intent : Any, Effect : Any> {
    fun reduce(state: State, intent: Intent): Reduction<State, Effect>
}

/** Read-only state and intent boundary exposed by a unidirectional data-flow component. */
interface UdfComponent<State : Any, Intent : Any> {
    val state: StateFlow<State>
    fun accept(intent: Intent)
}
