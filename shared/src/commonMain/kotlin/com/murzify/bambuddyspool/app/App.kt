@file:Suppress("FunctionNaming")

package com.murzify.bambuddyspool.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.murzify.bambuddyspool.app.navigation.RootDestination
import com.murzify.bambuddyspool.app.root.HomeConnectionState
import com.murzify.bambuddyspool.app.root.HomeNfcState
import com.murzify.bambuddyspool.app.root.RootComponent
import com.murzify.bambuddyspool.app.root.RootIntent
import com.murzify.bambuddyspool.app.root.RootState
import com.murzify.bambuddyspool.app.ui.FocusedStatusTitle
import com.murzify.bambuddyspool.app.ui.MinimumInteractiveTarget
import com.murzify.bambuddyspool.app.ui.UiWidthClass
import com.murzify.bambuddyspool.app.ui.processingAnnouncement
import com.murzify.bambuddyspool.feature.assignment.CombinedAssignmentConfirmationDialog
import com.murzify.bambuddyspool.feature.printers.PrintersScreen
import com.murzify.bambuddyspool.feature.spools.SpoolsScreen
import com.murzify.bambuddyspool.shared.resources.Res
import com.murzify.bambuddyspool.shared.resources.home_connection_not_configured
import com.murzify.bambuddyspool.shared.resources.home_connection_offline
import com.murzify.bambuddyspool.shared.resources.home_connection_online
import com.murzify.bambuddyspool.shared.resources.home_connection_stale
import com.murzify.bambuddyspool.shared.resources.home_hold_tag
import com.murzify.bambuddyspool.shared.resources.home_nfc_available
import com.murzify.bambuddyspool.shared.resources.home_nfc_disabled
import com.murzify.bambuddyspool.shared.resources.home_nfc_unavailable
import com.murzify.bambuddyspool.shared.resources.navigation_home
import com.murzify.bambuddyspool.shared.resources.navigation_printers
import com.murzify.bambuddyspool.shared.resources.navigation_settings
import com.murzify.bambuddyspool.shared.resources.navigation_spools
import com.murzify.bambuddyspool.shared.resources.placeholder_settings
import com.murzify.bambuddyspool.shared.resources.workflow_confirmation
import com.murzify.bambuddyspool.shared.resources.workflow_error
import com.murzify.bambuddyspool.shared.resources.workflow_processing
import com.murzify.bambuddyspool.shared.resources.workflow_success
import com.murzify.bambuddyspool.shared.resources.workflow_tag_mutation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun App(root: RootComponent) {
    val state by root.state.collectAsState()
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthClass = UiWidthClass(maxWidth)
            val contentPadding = if (widthClass == UiWidthClass.Expanded) 48.dp else 24.dp
            if (widthClass == UiWidthClass.Compact) {
                Column(modifier = Modifier.fillMaxSize()) {
                    RootContent(state, root, Modifier.weight(1f), contentPadding)
                    BottomNavigation(state.destination, root::accept)
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    RailNavigation(state.destination, root::accept)
                    RootContent(state, root, Modifier.weight(1f), contentPadding)
                }
            }
        }
    }
}

@Composable
private fun BottomNavigation(selected: RootDestination, accept: (RootIntent) -> Unit) = NavigationBar {
    RootDestination.entries.forEach { destination ->
        NavigationBarItem(
            selected = selected == destination,
            onClick = { accept(RootIntent.Select(destination)) },
            icon = {},
            label = { Text(stringResource(destination.label())) },
            modifier = MinimumInteractiveTarget.semantics { role = Role.Tab }
        )
    }
}

@Composable
private fun RailNavigation(selected: RootDestination, accept: (RootIntent) -> Unit) = NavigationRail {
    RootDestination.entries.forEach { destination ->
        NavigationRailItem(
            selected = selected == destination,
            onClick = { accept(RootIntent.Select(destination)) },
            icon = {},
            label = { Text(stringResource(destination.label())) },
            modifier = MinimumInteractiveTarget.semantics { role = Role.Tab }
        )
    }
}

@Composable
private fun RootContent(
    state: RootState,
    root: RootComponent,
    modifier: Modifier,
    contentPadding: androidx.compose.ui.unit.Dp
) = Surface(modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (state.destination) {
            RootDestination.Home -> HomeScreen(state)
            RootDestination.Spools -> SpoolsScreen(root.spoolsComponent, root::accept)
            RootDestination.Printers -> PrintersScreen(root.printersComponent, state.pendingManualSpoolId, root::accept)
            RootDestination.Settings -> Text(stringResource(Res.string.placeholder_settings))
        }
        state.transientWorkflow?.let { workflow ->
            val label = stringResource(workflow.label())
            when (workflow) {
                com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Processing ->
                    Text(label, modifier = Modifier.processingAnnouncement())
                com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Success,
                com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Error -> FocusedStatusTitle(label)
                else -> Text(label)
            }
        }
        state.assignmentConfirmation?.let { confirmation ->
            CombinedAssignmentConfirmationDialog(
                confirmation = confirmation,
                onConfirm = { root.accept(RootIntent.ConfirmAssignment) },
                onCancel = { root.accept(RootIntent.CancelAssignmentConfirmation) }
            )
        }
    }
}

@Composable
private fun HomeScreen(state: RootState) {
    Text(stringResource(Res.string.home_hold_tag))
    Text(stringResource(state.connectionState.label()))
    Text(stringResource(state.nfcState.label()))
}

private fun RootDestination.label(): StringResource = when (this) {
    RootDestination.Home -> Res.string.navigation_home
    RootDestination.Spools -> Res.string.navigation_spools
    RootDestination.Printers -> Res.string.navigation_printers
    RootDestination.Settings -> Res.string.navigation_settings
}

private fun HomeConnectionState.label(): StringResource = when (this) {
    HomeConnectionState.NotConfigured -> Res.string.home_connection_not_configured
    HomeConnectionState.Online -> Res.string.home_connection_online
    HomeConnectionState.Offline -> Res.string.home_connection_offline
    HomeConnectionState.Stale -> Res.string.home_connection_stale
}

private fun HomeNfcState.label(): StringResource = when (this) {
    HomeNfcState.Available -> Res.string.home_nfc_available
    HomeNfcState.Unavailable -> Res.string.home_nfc_unavailable
    HomeNfcState.Disabled -> Res.string.home_nfc_disabled
}

private fun com.murzify.bambuddyspool.app.root.RootTransientWorkflow.label(): StringResource = when (this) {
    com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Processing -> Res.string.workflow_processing
    com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Confirmation -> Res.string.workflow_confirmation
    com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Success -> Res.string.workflow_success
    com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Error -> Res.string.workflow_error
    com.murzify.bambuddyspool.app.root.RootTransientWorkflow.TagMutation -> Res.string.workflow_tag_mutation
}
