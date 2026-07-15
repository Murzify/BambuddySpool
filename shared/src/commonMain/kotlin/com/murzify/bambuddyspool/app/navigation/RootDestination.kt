package com.murzify.bambuddyspool.app.navigation

import kotlinx.serialization.Serializable

/** Primary destinations owned by the shared Decompose root. */
@Serializable
enum class RootDestination {
    Home,
    Spools,
    Printers,
    Settings
}
