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
import com.murzify.bambuddyspool.app.ui.MinimumInteractiveTarget
import com.murzify.bambuddyspool.app.ui.UiWidthClass
import com.murzify.bambuddyspool.app.ui.WorkflowFeedbackSurface
import com.murzify.bambuddyspool.feature.assignment.CombinedAssignmentConfirmationDialog
import com.murzify.bambuddyspool.feature.printers.PrintersScreen
import com.murzify.bambuddyspool.feature.settings.SettingsConnectionScreen
import com.murzify.bambuddyspool.feature.setup.SetupScreen
import com.murzify.bambuddyspool.feature.spools.SpoolsScreen
import com.murzify.bambuddyspool.feature.tagmutation.TagMutationSurface
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun App(root: RootComponent, onProcessingComposed: () -> Unit = {}) {
    val state by root.state.collectAsState()
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthClass = UiWidthClass(maxWidth)
            val contentPadding = if (widthClass == UiWidthClass.Expanded) 48.dp else 24.dp
            if (widthClass == UiWidthClass.Compact) {
                Column(modifier = Modifier.fillMaxSize()) {
                    RootContent(state, root, Modifier.weight(1f), contentPadding, onProcessingComposed)
                    BottomNavigation(state.destination, root::accept)
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    RailNavigation(state.destination, root::accept)
                    RootContent(state, root, Modifier.weight(1f), contentPadding, onProcessingComposed)
                }
            }
        }
    }
}

private data class PrimaryNavigationItem(val destination: RootDestination, val selected: Boolean)

@Composable
private fun BottomNavigation(selected: RootDestination, accept: (RootIntent) -> Unit) = NavigationBar {
    primaryNavigationItems(selected).forEach { item ->
        NavigationBarItem(
            selected = item.selected,
            onClick = { accept(RootIntent.Select(item.destination)) },
            icon = {},
            label = { Text(stringResource(item.destination.label())) },
            modifier = MinimumInteractiveTarget.semantics { role = Role.Tab }
        )
    }
}

@Composable
private fun RailNavigation(selected: RootDestination, accept: (RootIntent) -> Unit) = NavigationRail {
    primaryNavigationItems(selected).forEach { item ->
        NavigationRailItem(
            selected = item.selected,
            onClick = { accept(RootIntent.Select(item.destination)) },
            icon = {},
            label = { Text(stringResource(item.destination.label())) },
            modifier = MinimumInteractiveTarget.semantics { role = Role.Tab }
        )
    }
}

/** The two Material containers share one destination ordering and selection calculation. */
private fun primaryNavigationItems(selected: RootDestination): List<PrimaryNavigationItem> =
    RootDestination.entries.map { destination -> PrimaryNavigationItem(destination, destination == selected) }

@Composable
private fun RootContent(
    state: RootState,
    root: RootComponent,
    modifier: Modifier,
    contentPadding: androidx.compose.ui.unit.Dp,
    onProcessingComposed: () -> Unit
) = Surface(modifier.fillMaxSize()) {
    val connectionSettings by root.connectionSettings.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (state.destination) {
            RootDestination.Home -> if (state.connectionState == HomeConnectionState.NotConfigured) {
                SetupScreen(root.connectionForm)
            } else {
                HomeScreen(state)
            }
            RootDestination.Spools -> SpoolsScreen(root.spoolsComponent, root::accept)
            RootDestination.Printers -> PrintersScreen(root.printersComponent, state.pendingManualSpoolId, root::accept)
            RootDestination.Settings -> SettingsConnectionScreen(
                component = root.connectionForm,
                configuredUrl = connectionSettings.baseUrl?.toString(),
                hasSavedToken = false
            )
        }
        state.transientWorkflow?.let { workflow ->
            WorkflowFeedbackSurface(workflow, onProcessingComposed)
        }
        state.assignmentConfirmation?.let { confirmation ->
            CombinedAssignmentConfirmationDialog(
                confirmation = confirmation,
                onConfirm = { root.accept(RootIntent.ConfirmAssignment) },
                onCancel = { root.accept(RootIntent.CancelAssignmentConfirmation) }
            )
        }
        if (state.transientWorkflow == com.murzify.bambuddyspool.app.root.RootTransientWorkflow.TagMutation) {
            TagMutationSurface(
                state = state.tagMutation,
                onConfirm = { root.accept(RootIntent.ConfirmTagMutation) },
                onCancel = { root.accept(RootIntent.CancelTagMutation) },
                onRetry = { root.accept(RootIntent.RetryTagMutation) }
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
