package com.murzify.bambuddyspool.app.connection

/** Presentation-only context for the one shared setup/settings connection form. */
sealed interface ConnectionFormPresentation {
    data object Setup : ConnectionFormPresentation
    data class Settings(val configuredUrl: String?, val hasSavedToken: Boolean) : ConnectionFormPresentation
}
