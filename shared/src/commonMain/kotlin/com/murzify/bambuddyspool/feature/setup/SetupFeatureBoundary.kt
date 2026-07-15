package com.murzify.bambuddyspool.feature.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.murzify.bambuddyspool.app.connection.ConnectionFormComponent
import com.murzify.bambuddyspool.app.connection.ConnectionFormPresentation
import com.murzify.bambuddyspool.app.connection.ConnectionFormScreen

/** First-run entry point; the connection form and service are shared with Settings. */
@Composable
@Suppress("FunctionNaming") // Compose entry points use UpperCamelCase by convention.
fun SetupScreen(component: ConnectionFormComponent, modifier: Modifier = Modifier) =
    ConnectionFormScreen(component = component, presentation = ConnectionFormPresentation.Setup, modifier = modifier)
