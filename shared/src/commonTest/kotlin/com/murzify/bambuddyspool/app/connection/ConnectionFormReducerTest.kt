package com.murzify.bambuddyspool.app.connection

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionFormReducerTest {

    @Test
    fun saveRequiresBothNonSecretInputs() {
        val reduction = ConnectionFormReducer.reduce(ConnectionFormState(), ConnectionFormIntent.SaveRequested)

        assertEquals(ConnectionFormMessage.RequiredFields, reduction.state.message)
        assertEquals(ConnectionFormOperation.Idle, reduction.state.operation)
        assertEquals(emptyList(), reduction.effects)
    }

    @Test
    fun saveUsesEffectWithoutPuttingTokenInState() {
        val state = ConnectionFormState(baseUrl = "https://bambuddy.example", tokenEntered = true)

        val reduction = ConnectionFormReducer.reduce(state, ConnectionFormIntent.SaveRequested)

        assertEquals(ConnectionFormOperation.Saving, reduction.state.operation)
        assertEquals(listOf(ConnectionFormEffect.Save), reduction.effects)
        assertEquals(state.tokenEntered, reduction.state.tokenEntered)
    }

    @Test
    fun confirmingBothWarningsRetainsBothAcknowledgementsForOneReplacement() {
        val initial = ConnectionFormState(baseUrl = "http://new.example", tokenEntered = true)
        val instanceWarning = ConnectionFormReducer.reduce(
            initial,
            ConnectionFormIntent.WarningRequired(ConnectionFormWarning.InstanceChange)
        )
        val instanceConfirmed = ConnectionFormReducer.reduce(
            instanceWarning.state,
            ConnectionFormIntent.WarningConfirmed
        )
        val httpWarning = ConnectionFormReducer.reduce(
            instanceConfirmed.state,
            ConnectionFormIntent.WarningRequired(ConnectionFormWarning.Http)
        )
        val httpConfirmed = ConnectionFormReducer.reduce(httpWarning.state, ConnectionFormIntent.WarningConfirmed)

        assertEquals(true, httpConfirmed.state.acceptedInstanceChangeWarning)
        assertEquals(true, httpConfirmed.state.acceptedHttpWarning)
        assertEquals(listOf(ConnectionFormEffect.SaveAfterWarning), httpConfirmed.effects)
    }

    @Test
    fun changingUrlRevokesPriorWarningAcknowledgements() {
        val state = ConnectionFormState(
            baseUrl = "http://old.example",
            tokenEntered = true,
            acceptedInstanceChangeWarning = true,
            acceptedHttpWarning = true
        )

        val reduction = ConnectionFormReducer.reduce(
            state,
            ConnectionFormIntent.BaseUrlChanged("http://new.example")
        )

        assertEquals(false, reduction.state.acceptedInstanceChangeWarning)
        assertEquals(false, reduction.state.acceptedHttpWarning)
    }
}
