package com.murzify.bambuddyspool.app.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.murzify.bambuddyspool.app.ui.AccessibleButton
import com.murzify.bambuddyspool.core.settings.ConnectionValidationFailureReason
import com.murzify.bambuddyspool.shared.resources.Res
import com.murzify.bambuddyspool.shared.resources.connection_api_token
import com.murzify.bambuddyspool.shared.resources.connection_auth_rejected
import com.murzify.bambuddyspool.shared.resources.connection_base_url
import com.murzify.bambuddyspool.shared.resources.connection_busy
import com.murzify.bambuddyspool.shared.resources.connection_cancel
import com.murzify.bambuddyspool.shared.resources.connection_confirm
import com.murzify.bambuddyspool.shared.resources.connection_http_warning
import com.murzify.bambuddyspool.shared.resources.connection_http_warning_title
import com.murzify.bambuddyspool.shared.resources.connection_incompatible
import com.murzify.bambuddyspool.shared.resources.connection_instance_warning
import com.murzify.bambuddyspool.shared.resources.connection_instance_warning_title
import com.murzify.bambuddyspool.shared.resources.connection_invalid_url
import com.murzify.bambuddyspool.shared.resources.connection_required_fields
import com.murzify.bambuddyspool.shared.resources.connection_save
import com.murzify.bambuddyspool.shared.resources.connection_save_succeeded
import com.murzify.bambuddyspool.shared.resources.connection_saving
import com.murzify.bambuddyspool.shared.resources.connection_settings_title
import com.murzify.bambuddyspool.shared.resources.connection_setup_title
import com.murzify.bambuddyspool.shared.resources.connection_test
import com.murzify.bambuddyspool.shared.resources.connection_test_succeeded
import com.murzify.bambuddyspool.shared.resources.connection_testing
import com.murzify.bambuddyspool.shared.resources.connection_tls_failed
import com.murzify.bambuddyspool.shared.resources.connection_token_status_saved
import com.murzify.bambuddyspool.shared.resources.connection_unreachable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One scrollable form reused by first-run Setup and Settings, including their header/status treatment. */
@Composable
@Suppress("FunctionNaming", "LongMethod") // Compose entry point and cohesive accessible form layout.
fun ConnectionFormScreen(
    component: ConnectionFormComponent,
    presentation: ConnectionFormPresentation,
    modifier: Modifier = Modifier
) {
    val state by component.state.collectAsState()
    var token by remember { mutableStateOf("") }
    val busy = state.operation != ConnectionFormOperation.Idle

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(presentation.title()),
            modifier = Modifier.semantics { heading() }
        )
        (presentation as? ConnectionFormPresentation.Settings)?.let { settings ->
            settings.configuredUrl?.let { configuredUrl -> Text(configuredUrl) }
            if (settings.hasSavedToken) Text(stringResource(Res.string.connection_token_status_saved))
        }
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = { component.accept(ConnectionFormIntent.BaseUrlChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text(stringResource(Res.string.connection_base_url)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true
        )
        OutlinedTextField(
            value = token,
            onValueChange = {
                token = it
                component.accept(ConnectionFormIntent.TokenPresenceChanged(it.isNotBlank()))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text(stringResource(Res.string.connection_api_token)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            singleLine = true
        )
        AccessibleButton(
            text = stringResource(
                if (state.operation == ConnectionFormOperation.Testing) {
                    Res.string.connection_testing
                } else {
                    Res.string.connection_test
                }
            ),
            onClick = { component.test(token) },
            enabled = !busy,
            disabledReason = stringResource(Res.string.connection_busy),
            modifier = Modifier.fillMaxWidth()
        )
        AccessibleButton(
            text = stringResource(
                if (state.operation == ConnectionFormOperation.Saving) {
                    Res.string.connection_saving
                } else {
                    Res.string.connection_save
                }
            ),
            onClick = { component.save(token) },
            enabled = !busy,
            disabledReason = stringResource(Res.string.connection_busy),
            modifier = Modifier.fillMaxWidth()
        )
        state.message?.let { Text(connectionMessage(it)) }
    }

    state.pendingWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { component.accept(ConnectionFormIntent.WarningDismissed) },
            title = {
                Text(
                    stringResource(
                        if (warning == ConnectionFormWarning.InstanceChange) {
                            Res.string.connection_instance_warning_title
                        } else {
                            Res.string.connection_http_warning_title
                        }
                    ),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Text(
                    stringResource(
                        if (warning == ConnectionFormWarning.InstanceChange) {
                            Res.string.connection_instance_warning
                        } else {
                            Res.string.connection_http_warning
                        }
                    )
                )
            },
            confirmButton = {
                Button(onClick = { component.confirmWarning(token) }) {
                    Text(stringResource(Res.string.connection_confirm))
                }
            },
            dismissButton = {
                Button(onClick = { component.accept(ConnectionFormIntent.WarningDismissed) }) {
                    Text(stringResource(Res.string.connection_cancel))
                }
            }
        )
    }
}

private fun ConnectionFormPresentation.title(): StringResource = when (this) {
    ConnectionFormPresentation.Setup -> Res.string.connection_setup_title
    is ConnectionFormPresentation.Settings -> Res.string.connection_settings_title
}

@Composable
private fun connectionMessage(message: ConnectionFormMessage): String = stringResource(
    when (message) {
        ConnectionFormMessage.RequiredFields -> Res.string.connection_required_fields
        ConnectionFormMessage.TestSucceeded -> Res.string.connection_test_succeeded
        ConnectionFormMessage.SaveSucceeded -> Res.string.connection_save_succeeded
        ConnectionFormMessage.OperationFailed -> Res.string.connection_unreachable
        is ConnectionFormMessage.InvalidUrl -> Res.string.connection_invalid_url
        is ConnectionFormMessage.ValidationFailed -> when (message.reason) {
            ConnectionValidationFailureReason.Unreachable -> Res.string.connection_unreachable
            ConnectionValidationFailureReason.AuthenticationRejected -> Res.string.connection_auth_rejected
            ConnectionValidationFailureReason.IncompatibleResponse -> Res.string.connection_incompatible
            ConnectionValidationFailureReason.TlsValidationFailed -> Res.string.connection_tls_failed
        }
    }
)
