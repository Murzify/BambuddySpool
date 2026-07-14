package com.murzify.bambuddyspool.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.murzify.bambuddyspool.app.connection.ConnectionFormComponent
import com.murzify.bambuddyspool.app.connection.ConnectionFormScreen
import com.murzify.bambuddyspool.shared.resources.Res
import com.murzify.bambuddyspool.shared.resources.connection_token_status_saved
import org.jetbrains.compose.resources.stringResource

/** Settings entry point; it deliberately reuses the setup connection form and service. */
@Composable
@Suppress("FunctionNaming") // Compose entry points use UpperCamelCase by convention.
fun SettingsConnectionScreen(
    component: ConnectionFormComponent,
    configuredUrl: String?,
    hasSavedToken: Boolean,
    modifier: Modifier = Modifier
) = Column(modifier) {
    configuredUrl?.let { Text(it) }
    if (hasSavedToken) Text(stringResource(Res.string.connection_token_status_saved))
    ConnectionFormScreen(component = component)
}
