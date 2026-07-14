package com.murzify.bambuddyspool.app.connection

import com.murzify.bambuddyspool.core.application.Reducer
import com.murzify.bambuddyspool.core.application.Reduction
import com.murzify.bambuddyspool.core.settings.BaseUrlParseFailureReason
import com.murzify.bambuddyspool.core.settings.ConnectionValidationFailureReason

/** Non-secret state for the shared setup and settings connection form. */
data class ConnectionFormState(
    val baseUrl: String = "",
    val tokenEntered: Boolean = false,
    val acceptedInstanceChangeWarning: Boolean = false,
    val acceptedHttpWarning: Boolean = false,
    val operation: ConnectionFormOperation = ConnectionFormOperation.Idle,
    val message: ConnectionFormMessage? = null,
    val pendingWarning: ConnectionFormWarning? = null
)

enum class ConnectionFormOperation { Idle, Testing, Saving }

sealed interface ConnectionFormMessage {
    data object RequiredFields : ConnectionFormMessage
    data object TestSucceeded : ConnectionFormMessage
    data object SaveSucceeded : ConnectionFormMessage
    data class InvalidUrl(val reason: BaseUrlParseFailureReason) : ConnectionFormMessage
    data class ValidationFailed(val reason: ConnectionValidationFailureReason) : ConnectionFormMessage
}

enum class ConnectionFormWarning { InstanceChange, Http }

sealed interface ConnectionFormIntent {
    data class BaseUrlChanged(val value: String) : ConnectionFormIntent
    data class TokenPresenceChanged(val entered: Boolean) : ConnectionFormIntent
    data object TestRequested : ConnectionFormIntent
    data object SaveRequested : ConnectionFormIntent
    data object TestSucceeded : ConnectionFormIntent
    data object SaveSucceeded : ConnectionFormIntent
    data class InvalidUrl(val reason: BaseUrlParseFailureReason) : ConnectionFormIntent
    data class ValidationFailed(val reason: ConnectionValidationFailureReason) : ConnectionFormIntent
    data class WarningRequired(val warning: ConnectionFormWarning) : ConnectionFormIntent
    data object WarningConfirmed : ConnectionFormIntent
    data object WarningDismissed : ConnectionFormIntent
}

internal sealed interface ConnectionFormEffect {
    data object Test : ConnectionFormEffect
    data object Save : ConnectionFormEffect
    data object SaveAfterWarning : ConnectionFormEffect
}

internal object ConnectionFormReducer :
    Reducer<ConnectionFormState, ConnectionFormIntent, ConnectionFormEffect> {
    override fun reduce(
        state: ConnectionFormState,
        intent: ConnectionFormIntent
    ): Reduction<ConnectionFormState, ConnectionFormEffect> = when (intent) {
        is ConnectionFormIntent.BaseUrlChanged -> Reduction(
            state.copy(
                baseUrl = intent.value,
                acceptedInstanceChangeWarning = false,
                acceptedHttpWarning = false,
                message = null,
                pendingWarning = null
            )
        )
        is ConnectionFormIntent.TokenPresenceChanged -> Reduction(
            state.copy(tokenEntered = intent.entered, message = null, pendingWarning = null)
        )
        ConnectionFormIntent.TestRequested -> request(state, ConnectionFormOperation.Testing, ConnectionFormEffect.Test)
        ConnectionFormIntent.SaveRequested -> request(state, ConnectionFormOperation.Saving, ConnectionFormEffect.Save)
        ConnectionFormIntent.TestSucceeded -> Reduction(
            state.copy(operation = ConnectionFormOperation.Idle, message = ConnectionFormMessage.TestSucceeded)
        )
        ConnectionFormIntent.SaveSucceeded -> Reduction(
            state.copy(operation = ConnectionFormOperation.Idle, message = ConnectionFormMessage.SaveSucceeded)
        )
        is ConnectionFormIntent.InvalidUrl -> failure(state, ConnectionFormMessage.InvalidUrl(intent.reason))
        is ConnectionFormIntent.ValidationFailed -> failure(
            state,
            ConnectionFormMessage.ValidationFailed(intent.reason)
        )
        is ConnectionFormIntent.WarningRequired -> Reduction(
            state.copy(operation = ConnectionFormOperation.Idle, pendingWarning = intent.warning)
        )
        ConnectionFormIntent.WarningConfirmed -> Reduction(
            state.copy(
                operation = ConnectionFormOperation.Saving,
                acceptedInstanceChangeWarning = state.acceptedInstanceChangeWarning ||
                    state.pendingWarning == ConnectionFormWarning.InstanceChange,
                acceptedHttpWarning = state.acceptedHttpWarning || state.pendingWarning == ConnectionFormWarning.Http,
                pendingWarning = null
            ),
            listOf(ConnectionFormEffect.SaveAfterWarning)
        )
        ConnectionFormIntent.WarningDismissed -> Reduction(
            state.copy(operation = ConnectionFormOperation.Idle, pendingWarning = null)
        )
    }

    private fun request(
        state: ConnectionFormState,
        operation: ConnectionFormOperation,
        effect: ConnectionFormEffect
    ): Reduction<ConnectionFormState, ConnectionFormEffect> = if (state.baseUrl.isBlank() || !state.tokenEntered) {
        Reduction(state.copy(operation = ConnectionFormOperation.Idle, message = ConnectionFormMessage.RequiredFields))
    } else {
        Reduction(state.copy(operation = operation, message = null, pendingWarning = null), listOf(effect))
    }

    private fun failure(
        state: ConnectionFormState,
        message: ConnectionFormMessage
    ): Reduction<ConnectionFormState, ConnectionFormEffect> = Reduction(
        state.copy(operation = ConnectionFormOperation.Idle, message = message, pendingWarning = null)
    )
}
