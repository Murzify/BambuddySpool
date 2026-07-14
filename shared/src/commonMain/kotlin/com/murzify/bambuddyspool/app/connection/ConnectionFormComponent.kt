package com.murzify.bambuddyspool.app.connection

import com.murzify.bambuddyspool.core.application.UdfComponent
import com.murzify.bambuddyspool.core.security.SecretValue
import com.murzify.bambuddyspool.core.settings.ConnectionReplacementAcknowledgements
import com.murzify.bambuddyspool.core.settings.ConnectionReplacementResult
import com.murzify.bambuddyspool.core.settings.ConnectionReplacementService
import com.murzify.bambuddyspool.core.settings.ConnectionTestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared connection controller for both first-run Setup and Settings.
 *
 * Tokens are supplied only to an immediate operation and never enter [state], navigation, or saved state.
 */
class ConnectionFormComponent(private val service: ConnectionReplacementService, private val scope: CoroutineScope) :
    UdfComponent<ConnectionFormState, ConnectionFormIntent> {
    private val mutableState = MutableStateFlow(ConnectionFormState())
    override val state: StateFlow<ConnectionFormState> = mutableState.asStateFlow()

    override fun accept(intent: ConnectionFormIntent) {
        reduce(intent)
    }

    fun test(plainToken: String) = submit(plainToken, ConnectionFormIntent.TestRequested)

    fun save(plainToken: String) = submit(plainToken, ConnectionFormIntent.SaveRequested)

    fun confirmWarning(plainToken: String) {
        val token = SecretValue.fromPlainText(plainToken) ?: run {
            reduce(ConnectionFormIntent.TokenPresenceChanged(false))
            return
        }
        if (state.value.pendingWarning == null) return
        reduce(ConnectionFormIntent.WarningConfirmed).also {
            performSave(
                token = token,
                acknowledgements = ConnectionReplacementAcknowledgements(
                    acceptedInstanceChangeWarning = state.value.acceptedInstanceChangeWarning,
                    acceptedHttpWarning = state.value.acceptedHttpWarning
                )
            )
        }
    }

    private fun submit(plainToken: String, intent: ConnectionFormIntent) {
        val token = SecretValue.fromPlainText(plainToken)
        reduce(ConnectionFormIntent.TokenPresenceChanged(token != null))
        if (token == null) {
            reduce(intent)
            return
        }
        val effects = reduce(intent)
        when {
            ConnectionFormEffect.Test in effects -> performTest(token)
            ConnectionFormEffect.Save in effects -> performSave(token, ConnectionReplacementAcknowledgements.None)
        }
    }

    private fun performTest(token: SecretValue) {
        scope.launch {
            when (val result = service.testConnection(state.value.baseUrl, token)) {
                ConnectionTestResult.Valid -> reduce(ConnectionFormIntent.TestSucceeded)
                is ConnectionTestResult.InvalidBaseUrl -> reduce(ConnectionFormIntent.InvalidUrl(result.reason))
                is ConnectionTestResult.ValidationFailed -> reduce(ConnectionFormIntent.ValidationFailed(result.reason))
            }
        }
    }

    private fun performSave(token: SecretValue, acknowledgements: ConnectionReplacementAcknowledgements) {
        scope.launch {
            when (val result = service.replaceConnection(state.value.baseUrl, token, acknowledgements)) {
                ConnectionReplacementResult.Replaced -> reduce(ConnectionFormIntent.SaveSucceeded)
                is ConnectionReplacementResult.InvalidBaseUrl -> reduce(ConnectionFormIntent.InvalidUrl(result.reason))
                is ConnectionReplacementResult.ValidationFailed -> reduce(
                    ConnectionFormIntent.ValidationFailed(result.reason)
                )
                ConnectionReplacementResult.InstanceChangeWarningRequired -> reduce(
                    ConnectionFormIntent.WarningRequired(ConnectionFormWarning.InstanceChange)
                )
                is ConnectionReplacementResult.HttpWarningRequired -> reduce(
                    ConnectionFormIntent.WarningRequired(ConnectionFormWarning.Http)
                )
            }
        }
    }

    private fun reduce(intent: ConnectionFormIntent): List<ConnectionFormEffect> {
        val reduction = ConnectionFormReducer.reduce(mutableState.value, intent)
        mutableState.value = reduction.state
        return reduction.effects
    }
}
