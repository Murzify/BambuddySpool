package com.murzify.bambuddyspool.core.application

import kotlinx.coroutines.flow.StateFlow

data class Reduction<State : Any, Effect : Any>(val state: State, val effects: List<Effect> = emptyList())

fun interface Reducer<State : Any, Intent : Any, Effect : Any> {
    fun reduce(state: State, intent: Intent): Reduction<State, Effect>
}

interface UdfComponent<State : Any, Intent : Any> {
    val state: StateFlow<State>
    fun accept(intent: Intent)
}
