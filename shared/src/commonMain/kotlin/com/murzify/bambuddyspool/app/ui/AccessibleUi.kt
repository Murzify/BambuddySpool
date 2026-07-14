@file:Suppress("FunctionNaming", "MatchingDeclarationName") // Compose composables use UpperCamelCase.

package com.murzify.bambuddyspool.app.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared adaptive breakpoints from TECHSPEC 14.11. Height is deliberately not used, so rotation stays supported. */
enum class UiWidthClass { Compact, Medium, Expanded }

fun UiWidthClass(width: Dp): UiWidthClass = when {
    width < 600.dp -> UiWidthClass.Compact
    width < 840.dp -> UiWidthClass.Medium
    else -> UiWidthClass.Expanded
}

/** Shared baseline for pointer, keyboard, switch, and TalkBack activation targets. */
val MinimumInteractiveTarget: Modifier = Modifier.heightIn(min = 48.dp)

/** Announces an in-progress operation without moving focus away from the current workflow. */
fun Modifier.processingAnnouncement(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }

/** Requests initial accessibility focus for terminal workflow titles after they enter composition. */
@Composable
fun FocusedStatusTitle(text: String, modifier: Modifier = Modifier) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(text) { requester.requestFocus() }
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier.focusRequester(requester).focusable().semantics { liveRegion = LiveRegionMode.Assertive }
    )
}

/** A full-size action that exposes a disabled reason both visually and to accessibility services. */
@Composable
fun AccessibleButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledReason: String? = null,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(MinimumInteractiveTarget).semantics {
            if (!enabled && disabledReason != null) stateDescription = disabledReason
        }
    ) { Text(text) }
    if (!enabled && disabledReason != null) {
        Text(
            disabledReason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AccessibleOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.then(MinimumInteractiveTarget)) { Text(text) }
}
