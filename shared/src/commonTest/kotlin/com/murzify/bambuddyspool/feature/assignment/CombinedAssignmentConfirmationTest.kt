package com.murzify.bambuddyspool.feature.assignment

import com.murzify.bambuddyspool.core.assignment.AssignmentContext
import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.domain.VirtualTray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CombinedAssignmentConfirmationTest {
    @Test
    fun moveConflictShowsAllCurrentLocationsTargetReplacementAndOneMoveEffect() {
        val confirmation = context(
            assignments = listOf(
                Assignment(spool(1), slot(2), configured = true, pendingConfiguration = false),
                Assignment(spool(1), slot(3), configured = true, pendingConfiguration = false),
                Assignment(spool(8), slot(1), configured = true, pendingConfiguration = false)
            )
        ).toCombinedConfirmation()

        assertEquals(AssignmentEffect.MoveAndAssign, confirmation.effect)
        assertEquals(listOf("Printer 2", "Printer 3"), confirmation.currentLocations.map { it.printerName })
        assertEquals("Printer 1", confirmation.target.printerName)
        assertEquals(8, confirmation.targetReplacement?.spoolId)
    }

    @Test
    fun targetReplacementAloneIsDisclosedButDoesNotBecomeMoveConfirmation() {
        val confirmation = context(
            assignments = listOf(Assignment(spool(8), slot(1), configured = true, pendingConfiguration = false))
        ).toCombinedConfirmation()

        assertEquals(AssignmentEffect.Assign, confirmation.effect)
        assertEquals("Printer 1", confirmation.targetReplacement?.printerName)
        assertEquals(emptyList(), confirmation.currentLocations)
    }

    @Test
    fun unassignedTargetHasNoReplacementOrMoveDisclosure() {
        val confirmation = context(assignments = emptyList()).toCombinedConfirmation()

        assertEquals(AssignmentEffect.Assign, confirmation.effect)
        assertNull(confirmation.targetReplacement)
        assertEquals(emptyList(), confirmation.currentLocations)
    }

    @Test
    fun focusOrderMakesContextAvailableBeforeTheDestructiveAction() {
        assertEquals(
            listOf(
                "title",
                "spool-and-current-locations",
                "target-printer",
                "target-slot",
                "warning",
                "confirm",
                "cancel"
            ),
            confirmationFocusOrder
        )
    }

    private fun context(assignments: List<Assignment>): AssignmentContext {
        val target = slot(1)
        val printers = listOf(1L, 2L, 3L).map { Printer(printer(it), "Printer $it") }
        val targetPrinter = printers.first()
        return AssignmentContext(
            spool = Spool(spool(1), "Blue PLA", null, "PLA", "Blue", 100),
            printers = printers,
            targetPrinter = targetPrinter,
            targetStatus = PrinterStatus(targetPrinter, true, listOf(VirtualTray(255, "External"))),
            assignments = assignments,
            targetSlot = target
        )
    }

    private fun printer(value: Long): PrinterId = requireNotNull(PrinterId.from(value))
    private fun spool(value: Long): SpoolId = requireNotNull(SpoolId.from(value))
    private fun slot(printerValue: Long): SlotKey = SlotKey(printer(printerValue), 255, 0)
}
