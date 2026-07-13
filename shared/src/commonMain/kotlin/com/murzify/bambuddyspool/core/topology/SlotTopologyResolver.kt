package com.murzify.bambuddyspool.core.topology

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterSlot
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SlotKind
import com.murzify.bambuddyspool.core.domain.UnsupportedTopology
import com.murzify.bambuddyspool.core.domain.UnsupportedTopologyReason

interface SlotTopologyResolver {
    fun resolve(printer: Printer, status: PrinterStatus, assignments: List<Assignment>): SlotTopologyResolution

    fun validateMutationTarget(resolution: SlotTopologyResolution, slot: SlotKey): SlotMutationTargetResolution
}

class KnownSlotTopologyResolver(private val ruleSet: SlotTopologyRuleSet = A1_SLOT_TOPOLOGY_RULE_SET) :
    SlotTopologyResolver {
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override fun resolve(
        printer: Printer,
        status: PrinterStatus,
        assignments: List<Assignment>
    ): SlotTopologyResolution {
        if (status.printer.id != printer.id) {
            return unsupported(UnsupportedTopologyReason.ConflictingCoordinates)
        }
        if (status.virtualTrays.isEmpty()) {
            return unsupported(UnsupportedTopologyReason.MissingPhysicalSlots)
        }
        if (status.virtualTrays.map { it.id }.toSet().size != status.virtualTrays.size) {
            return unsupported(UnsupportedTopologyReason.ConflictingCoordinates)
        }

        val ruleByVirtualTrayId = ruleSet.externalSlots.associateBy { it.virtualTrayId }
        if (ruleByVirtualTrayId.size != ruleSet.externalSlots.size) {
            return unsupported(UnsupportedTopologyReason.ConflictingCoordinates)
        }

        val knownExternalTrays = status.virtualTrays.mapNotNull { tray ->
            ruleByVirtualTrayId[tray.id]?.let { rule -> tray to rule }
        }
        if (knownExternalTrays.isEmpty()) {
            return unsupported(UnsupportedTopologyReason.UnknownExternalSlotMapping)
        }
        if (knownExternalTrays.size != status.virtualTrays.size) {
            return unsupported(UnsupportedTopologyReason.PartialExternalSlotMapping)
        }

        val knownExternalKeys = mutableSetOf<SlotKey>()
        val externalSlots = knownExternalTrays.mapIndexed { index, (tray, rule) ->
            val key = SlotKey.from(
                printerId = printer.id,
                amsId = rule.amsId,
                trayId = rule.trayId
            ) ?: return unsupported(UnsupportedTopologyReason.ConflictingCoordinates)
            if (!knownExternalKeys.add(key)) {
                return unsupported(UnsupportedTopologyReason.ConflictingCoordinates)
            }
            PrinterSlot(
                key = key,
                kind = SlotKind.External,
                label = tray.label ?: rule.label ?: "External slot ${index + 1}",
                assignedSpoolId = null
            )
        }

        val relevantAssignments = assignments.filter { it.slot.printerId == printer.id }
        if (relevantAssignments.map { it.slot }.toSet().size != relevantAssignments.size) {
            return unsupported(UnsupportedTopologyReason.ConflictingCoordinates)
        }
        val knownExternalAmsIds = knownExternalKeys.map { it.amsId }.toSet()
        if (relevantAssignments.any { it.slot.amsId in knownExternalAmsIds && it.slot !in knownExternalKeys }) {
            return unsupported(UnsupportedTopologyReason.ConflictingCoordinates)
        }

        val assignmentsBySlot = relevantAssignments.associateBy { it.slot }
        val externalSlotsWithAssignments = externalSlots.map { slot ->
            slot.copy(assignedSpoolId = assignmentsBySlot[slot.key]?.spoolId)
        }
        val amsSlots = relevantAssignments
            .filter { it.slot !in knownExternalKeys }
            .sortedWith(compareBy<Assignment> { it.slot.amsId }.thenBy { it.slot.trayId })
            .mapIndexed { index, assignment ->
                PrinterSlot(
                    key = assignment.slot,
                    kind = SlotKind.Ams,
                    label = "AMS slot ${index + 1}",
                    assignedSpoolId = assignment.spoolId
                )
            }

        return SlotTopologyResolution.Supported(
            slots = externalSlotsWithAssignments + amsSlots
        )
    }

    override fun validateMutationTarget(
        resolution: SlotTopologyResolution,
        slot: SlotKey
    ): SlotMutationTargetResolution = when (resolution) {
        is SlotTopologyResolution.Unsupported -> SlotMutationTargetResolution.Blocked(resolution.failure)
        is SlotTopologyResolution.Supported -> {
            val resolved = resolution.slots.firstOrNull { it.key == slot }
                ?: return SlotMutationTargetResolution.Blocked(
                    UnsupportedTopology(UnsupportedTopologyReason.UnknownExternalSlotMapping)
                )
            when (resolved.kind) {
                SlotKind.External -> SlotMutationTargetResolution.Valid(resolved)
                SlotKind.Ams -> SlotMutationTargetResolution.Blocked(
                    UnsupportedTopology(UnsupportedTopologyReason.AmsSlotSelectedForMutation)
                )
            }
        }
    }
}

sealed interface SlotTopologyResolution {
    data class Supported(val slots: List<PrinterSlot>) : SlotTopologyResolution
    data class Unsupported(val failure: UnsupportedTopology) : SlotTopologyResolution
}

sealed interface SlotMutationTargetResolution {
    data class Valid(val slot: PrinterSlot) : SlotMutationTargetResolution
    data class Blocked(val failure: UnsupportedTopology) : SlotMutationTargetResolution
}

data class SlotTopologyRuleSet(val externalSlots: List<KnownExternalSlotRule>) {
    init {
        require(externalSlots.isNotEmpty()) { "At least one known external slot rule is required" }
    }
}

data class KnownExternalSlotRule(val virtualTrayId: Int, val amsId: Int, val trayId: Int, val label: String?)

val A1_SLOT_TOPOLOGY_RULE_SET: SlotTopologyRuleSet = SlotTopologyRuleSet(
    externalSlots = listOf(
        KnownExternalSlotRule(
            virtualTrayId = 255,
            amsId = 255,
            trayId = 0,
            label = "External"
        )
    )
)

private fun unsupported(reason: UnsupportedTopologyReason): SlotTopologyResolution.Unsupported =
    SlotTopologyResolution.Unsupported(UnsupportedTopology(reason))
