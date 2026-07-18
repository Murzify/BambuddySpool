package com.murzify.bambuddyspool.feature.tagmutation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.murzify.bambuddyspool.app.ui.AccessibleButton
import com.murzify.bambuddyspool.shared.resources.Res
import com.murzify.bambuddyspool.shared.resources.tag_cancel
import com.murzify.bambuddyspool.shared.resources.tag_confirm_clear
import com.murzify.bambuddyspool.shared.resources.tag_confirm_link
import com.murzify.bambuddyspool.shared.resources.tag_confirm_overwrite
import com.murzify.bambuddyspool.shared.resources.tag_done
import com.murzify.bambuddyspool.shared.resources.tag_failed
import com.murzify.bambuddyspool.shared.resources.tag_hold_nearby
import com.murzify.bambuddyspool.shared.resources.tag_keep_held
import com.murzify.bambuddyspool.shared.resources.tag_linked
import com.murzify.bambuddyspool.shared.resources.tag_remove_before_done
import com.murzify.bambuddyspool.shared.resources.tag_retry
import org.jetbrains.compose.resources.stringResource

/** Shared explicit confirmation surface; touching a tag never itself authorizes a physical write. */
@Composable
fun TagMutationSurface(
    state: TagMutationState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    when (state) {
        TagMutationState.Idle -> Column(
            modifier = Modifier.semantics {
                liveRegion =
                    androidx.compose.ui.semantics.LiveRegionMode.Polite
            }
        ) {
            Text(stringResource(Res.string.tag_hold_nearby))
            AccessibleButton(stringResource(Res.string.tag_cancel), onCancel)
        }
        is TagMutationState.LinkReady -> Confirmation(
            stringResource(Res.string.tag_confirm_link),
            onConfirm,
            onCancel
        )
        is TagMutationState.OverwriteConfirmation -> Confirmation(
            stringResource(Res.string.tag_confirm_overwrite),
            onConfirm,
            onCancel
        )
        is TagMutationState.ClearConfirmation -> Confirmation(
            stringResource(Res.string.tag_confirm_clear),
            onConfirm,
            onCancel
        )
        is TagMutationState.AlreadyLinked -> Column {
            Text(stringResource(Res.string.tag_linked))
            AccessibleButton(stringResource(Res.string.tag_cancel), onCancel)
        }
        is TagMutationState.AwaitingReadBeforeRetry,
        is TagMutationState.RetryReady -> Column {
            Text(stringResource(Res.string.tag_hold_nearby))
            AccessibleButton(stringResource(Res.string.tag_retry), onRetry)
            AccessibleButton(stringResource(Res.string.tag_cancel), onCancel)
        }
        is TagMutationState.Succeeded -> Column {
            Text(stringResource(Res.string.tag_linked))
            Text(stringResource(Res.string.tag_remove_before_done))
            AccessibleButton(stringResource(Res.string.tag_done), onDone)
        }
        is TagMutationState.Failed -> Column {
            Text(stringResource(Res.string.tag_failed))
            if (state.retry != null) AccessibleButton(stringResource(Res.string.tag_retry), onRetry)
            AccessibleButton(stringResource(Res.string.tag_cancel), onCancel)
        }
    }
}

@Composable
private fun Confirmation(text: String, onConfirm: () -> Unit, onCancel: () -> Unit) = AlertDialog(
    onDismissRequest = onCancel,
    title = {
        Column {
            Text(text)
            Text(stringResource(Res.string.tag_keep_held))
        }
    },
    confirmButton = { AccessibleButton(stringResource(Res.string.tag_confirm_link), onConfirm) },
    dismissButton = { AccessibleButton(stringResource(Res.string.tag_cancel), onCancel) }
)
