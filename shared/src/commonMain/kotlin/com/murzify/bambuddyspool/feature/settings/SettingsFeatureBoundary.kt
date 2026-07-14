package com.murzify.bambuddyspool.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.murzify.bambuddyspool.app.connection.ConnectionFormComponent
import com.murzify.bambuddyspool.app.connection.ConnectionFormScreen

/** Settings entry point; it deliberately reuses the setup connection form and service. */
@Composable
@Suppress("FunctionNaming") // Compose entry points use UpperCamelCase by convention.
fun SettingsConnectionScreen(component: ConnectionFormComponent, modifier: Modifier = Modifier) =
    ConnectionFormScreen(component = component, modifier = modifier)
