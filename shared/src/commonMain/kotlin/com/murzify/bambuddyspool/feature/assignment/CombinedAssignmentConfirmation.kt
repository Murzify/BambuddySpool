@file:Suppress("FunctionNaming")

package com.murzify.bambuddyspool.feature.assignment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.murzify.bambuddyspool.app.ui.AccessibleButton
import com.murzify.bambuddyspool.app.ui.AccessibleOutlinedButton
import com.murzify.bambuddyspool.core.assignment.AssignmentContext
import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.shared.resources.Res
import com.murzify.bambuddyspool.shared.resources.assignment_cancel
import com.murzify.bambuddyspool.shared.resources.assignment_confirm
import com.murzify.bambuddyspool.shared.resources.assignment_current_locations
import com.murzify.bambuddyspool.shared.resources.assignment_effect_assign
import com.murzify.bambuddyspool.shared.resources.assignment_effect_move_and_assign
import com.murzify.bambuddyspool.shared.resources.assignment_location
import com.murzify.bambuddyspool.shared.resources.assignment_move_warning
import com.murzify.bambuddyspool.shared.resources.assignment_replace_target
import com.murzify.bambuddyspool.shared.resources.assignment_spool
import com.murzify.bambuddyspool.shared.resources.assignment_target_printer
import com.murzify.bambuddyspool.shared.resources.assignment_target_slot
import com.murzify.bambuddyspool.shared.resources.assignment_title
import com.murzify.bambuddyspool.shared.resources.assignment_unnamed_printer
import com.murzify.bambuddyspool.shared.resources.spools_unnamed
import org.jetbrains.compose.resources.stringResource

/**
 * One immutable, fresh-server confirmation summary. It intentionally contains all conflicts at once: callers must
 * not split printer, slot, move, or replacement questions into sequential dialogs.
 */
data class CombinedAssignmentConfirmation(
    val spoolName: String,
    val spoolId: Long,
    val currentLocations: List<AssignmentLocation>,
    val target: AssignmentLocation,
    val targetReplacement: AssignmentLocation?,
    val effect: AssignmentEffect
)

data class AssignmentLocation(
    val printerName: String,
    val slotLabel: String,
    val slot: SlotKey,
    val spoolId: Long? = null
)

enum class AssignmentEffect { Assign, MoveAndAssign }

fun AssignmentContext.toCombinedConfirmation(): CombinedAssignmentConfirmation {
    val printerNames = printers.associate { it.id to it.name.orEmpty() }
    fun Assignment.location(): AssignmentLocation = AssignmentLocation(
        printerName = printerNames.getValue(slot.printerId),
        slotLabel = slotCoordinates(slot),
        slot = slot,
        spoolId = spoolId.value
    )
    val current = assignments.filter { it.spoolId == spool.id && it.slot != targetSlot }.map { it.location() }
    val replacement = assignments.firstOrNull { it.slot == targetSlot && it.spoolId != spool.id }?.location()
    return CombinedAssignmentConfirmation(
        spoolName = spool.name.orEmpty(),
        spoolId = spool.id.value,
        currentLocations = current,
        target = AssignmentLocation(
            printerName = targetPrinter.name.orEmpty(),
            slotLabel = targetStatus.virtualTrays.firstOrNull { it.id == targetSlot.amsId }?.label
                ?: slotCoordinates(targetSlot),
            slot = targetSlot
        ),
        targetReplacement = replacement,
        effect = if (current.isEmpty()) AssignmentEffect.Assign else AssignmentEffect.MoveAndAssign
    )
}

private fun slotCoordinates(slot: SlotKey): String = "${slot.amsId}/${slot.trayId}"

internal const val ASSIGNMENT_CONFIRMATION_TAG = "assignment-combined-confirmation"
internal const val ASSIGNMENT_CONFIRM_TAG = "assignment-confirm"
internal const val ASSIGNMENT_CANCEL_TAG = "assignment-cancel"

internal val confirmationFocusOrder = listOf(
    "title",
    "spool-and-current-locations",
    "target-printer",
    "target-slot",
    "warning",
    "confirm",
    "cancel"
)

/**
 * Accessibility order follows TECHSPEC 15.2 exactly: title, spool/current assignment, target printer, target
 * slot, warning, primary action, then cancel. Compose emits children in this source order.
 */
@Composable
fun CombinedAssignmentConfirmationDialog(
    confirmation: CombinedAssignmentConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag(ASSIGNMENT_CONFIRMATION_TAG),
        onDismissRequest = onCancel,
        title = {
            Text(
                stringResource(Res.string.assignment_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        Res.string.assignment_spool,
                        confirmation.spoolName.ifBlank { stringResource(Res.string.spools_unnamed) },
                        confirmation.spoolId
                    )
                )
                if (confirmation.currentLocations.isNotEmpty()) {
                    Text(stringResource(Res.string.assignment_current_locations))
                    confirmation.currentLocations.forEach { location -> Text(location.displayText()) }
                }
                Text(
                    stringResource(
                        Res.string.assignment_target_printer,
                        confirmation.target.printerName.ifBlank {
                            stringResource(
                                Res.string.assignment_unnamed_printer,
                                confirmation.target.slot.printerId.value
                            )
                        }
                    )
                )
                Text(stringResource(Res.string.assignment_target_slot, confirmation.target.slotLabel))
                confirmation.targetReplacement?.let { replacement ->
                    Text(
                        stringResource(
                            Res.string.assignment_replace_target,
                            replacement.spoolId ?: 0,
                            replacement.printerName.ifBlank {
                                stringResource(
                                    Res.string.assignment_unnamed_printer,
                                    replacement.slot.printerId.value
                                )
                            },
                            replacement.slotLabel
                        )
                    )
                }
                if (confirmation.effect == AssignmentEffect.MoveAndAssign) {
                    Text(stringResource(Res.string.assignment_move_warning))
                }
                Text(
                    stringResource(
                        if (confirmation.effect == AssignmentEffect.MoveAndAssign) {
                            Res.string.assignment_effect_move_and_assign
                        } else {
                            Res.string.assignment_effect_assign
                        }
                    )
                )
            }
        },
        confirmButton = {
            AccessibleButton(
                text = stringResource(Res.string.assignment_confirm),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().testTag(ASSIGNMENT_CONFIRM_TAG)
            )
        },
        dismissButton = {
            AccessibleOutlinedButton(
                text = stringResource(Res.string.assignment_cancel),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().testTag(ASSIGNMENT_CANCEL_TAG)
            )
        }
    )
}

@Composable
private fun AssignmentLocation.displayText(): String = stringResource(
    Res.string.assignment_location,
    printerName.ifBlank { stringResource(Res.string.assignment_unnamed_printer, slot.printerId.value) },
    slotLabel
)
