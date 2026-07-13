package com.murzify.bambuddyspool.core.topology

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SlotKind
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.domain.UnsupportedTopologyReason
import com.murzify.bambuddyspool.core.domain.VirtualTray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SlotTopologyResolverTest {
    @Test
    fun confirmedA1ExternalSlotResolvesFromPhysicalVirtualTray() {
        val resolver = KnownSlotTopologyResolver()
        val rule = A1_SLOT_TOPOLOGY_RULE_SET.externalSlots.single()

        val resolution = resolver.resolve(
            printer = printer(),
            status = status(VirtualTray(id = rule.virtualTrayId, label = "API External")),
            assignments = listOf(assignment(slot = slotKey(amsId = rule.amsId, trayId = rule.trayId)))
        )

        val supported = assertIs<SlotTopologyResolution.Supported>(resolution)
        assertEquals(1, supported.slots.size)
        assertEquals(SlotKind.External, supported.slots.single().kind)
        assertEquals("API External", supported.slots.single().label)
        assertEquals(spoolId(1), supported.slots.single().assignedSpoolId)
    }

    @Test
    fun multipleKnownExternalSlotsAreAllRepresentedWithoutChoosingOne() {
        val resolver = KnownSlotTopologyResolver(twoSlotRuleSet())

        val resolution = resolver.resolve(
            printer = printer(),
            status = status(
                VirtualTray(id = 10, label = null),
                VirtualTray(id = 11, label = "API Right")
            ),
            assignments = listOf(
                assignment(spoolId = spoolId(1), slot = slotKey(amsId = 250, trayId = 0)),
                assignment(spoolId = spoolId(2), slot = slotKey(amsId = 250, trayId = 1))
            )
        )

        val supported = assertIs<SlotTopologyResolution.Supported>(resolution)
        assertEquals(
            listOf(
                slotKey(amsId = 250, trayId = 0),
                slotKey(amsId = 250, trayId = 1)
            ),
            supported.slots.map { it.key }
        )
        assertEquals(listOf("External Left", "API Right"), supported.slots.map { it.label })
        assertEquals(listOf(spoolId(1), spoolId(2)), supported.slots.map { it.assignedSpoolId })
    }

    @Test
    fun fallbackExternalLabelsUseResolvedSlotNumberOnlyAfterApiAndKnownLabels() {
        val resolver = KnownSlotTopologyResolver(
            SlotTopologyRuleSet(
                externalSlots = listOf(
                    KnownExternalSlotRule(virtualTrayId = 10, amsId = 250, trayId = 0, label = null),
                    KnownExternalSlotRule(virtualTrayId = 11, amsId = 250, trayId = 1, label = null)
                )
            )
        )

        val resolution = resolver.resolve(
            printer = printer(),
            status = status(
                VirtualTray(id = 10, label = null),
                VirtualTray(id = 11, label = null)
            ),
            assignments = emptyList()
        )

        val supported = assertIs<SlotTopologyResolution.Supported>(resolution)
        assertEquals(listOf("External slot 1", "External slot 2"), supported.slots.map { it.label })
    }

    @Test
    fun amsAssignmentEvidenceIsRepresentedReadOnlyAndBlockedForMutation() {
        val resolver = KnownSlotTopologyResolver()
        val rule = A1_SLOT_TOPOLOGY_RULE_SET.externalSlots.single()
        val amsSlot = slotKey(amsId = 0, trayId = 2)
        val resolution = resolver.resolve(
            printer = printer(),
            status = status(VirtualTray(id = rule.virtualTrayId, label = null)),
            assignments = listOf(assignment(slot = amsSlot))
        )

        val supported = assertIs<SlotTopologyResolution.Supported>(resolution)
        val ams = supported.slots.single { it.kind == SlotKind.Ams }
        assertEquals(amsSlot, ams.key)
        assertEquals("AMS slot 1", ams.label)
        val blocked = assertIs<SlotMutationTargetResolution.Blocked>(
            resolver.validateMutationTarget(supported, amsSlot)
        )
        assertEquals(UnsupportedTopologyReason.AmsSlotSelectedForMutation, blocked.failure.reason)
    }

    @Test
    fun missingPhysicalVirtualTraysFailClosed() {
        val resolution = KnownSlotTopologyResolver().resolve(
            printer = printer(),
            status = status(),
            assignments = emptyList()
        )

        val unsupported = assertIs<SlotTopologyResolution.Unsupported>(resolution)
        assertEquals(UnsupportedTopologyReason.MissingPhysicalSlots, unsupported.failure.reason)
    }

    @Test
    fun contradictoryVirtualTrayCoordinatesFailClosed() {
        val resolution = KnownSlotTopologyResolver().resolve(
            printer = printer(),
            status = status(
                VirtualTray(id = 255, label = "One"),
                VirtualTray(id = 255, label = "Two")
            ),
            assignments = emptyList()
        )

        val unsupported = assertIs<SlotTopologyResolution.Unsupported>(resolution)
        assertEquals(UnsupportedTopologyReason.ConflictingCoordinates, unsupported.failure.reason)
    }

    @Test
    fun contradictoryAssignmentCoordinatesFailClosed() {
        val rule = A1_SLOT_TOPOLOGY_RULE_SET.externalSlots.single()
        val resolution = KnownSlotTopologyResolver().resolve(
            printer = printer(),
            status = status(VirtualTray(id = rule.virtualTrayId, label = null)),
            assignments = listOf(assignment(slot = slotKey(amsId = rule.amsId, trayId = rule.trayId + 1)))
        )

        val unsupported = assertIs<SlotTopologyResolution.Unsupported>(resolution)
        assertEquals(UnsupportedTopologyReason.ConflictingCoordinates, unsupported.failure.reason)
    }

    @Test
    fun unknownVirtualTrayMappingFailsClosed() {
        val resolution = KnownSlotTopologyResolver().resolve(
            printer = printer(),
            status = status(VirtualTray(id = 42, label = "Unknown")),
            assignments = emptyList()
        )

        val unsupported = assertIs<SlotTopologyResolution.Unsupported>(resolution)
        assertEquals(UnsupportedTopologyReason.UnknownExternalSlotMapping, unsupported.failure.reason)
    }

    @Test
    fun partialKnownAndUnknownVirtualTrayMappingFailsClosed() {
        val rule = A1_SLOT_TOPOLOGY_RULE_SET.externalSlots.single()
        val resolution = KnownSlotTopologyResolver().resolve(
            printer = printer(),
            status = status(
                VirtualTray(id = rule.virtualTrayId, label = null),
                VirtualTray(id = 42, label = null)
            ),
            assignments = emptyList()
        )

        val unsupported = assertIs<SlotTopologyResolution.Unsupported>(resolution)
        assertEquals(UnsupportedTopologyReason.PartialExternalSlotMapping, unsupported.failure.reason)
    }

    @Test
    fun unsupportedTopologyBlocksMutationWithSameTypedReason() {
        val resolver = KnownSlotTopologyResolver()
        val resolution = SlotTopologyResolution.Unsupported(
            com.murzify.bambuddyspool.core.domain.UnsupportedTopology(
                UnsupportedTopologyReason.UnknownExternalSlotMapping
            )
        )

        val blocked = assertIs<SlotMutationTargetResolution.Blocked>(
            resolver.validateMutationTarget(resolution, slotKey(amsId = 255, trayId = 0))
        )
        assertEquals(UnsupportedTopologyReason.UnknownExternalSlotMapping, blocked.failure.reason)
    }

    private fun twoSlotRuleSet(): SlotTopologyRuleSet = SlotTopologyRuleSet(
        externalSlots = listOf(
            KnownExternalSlotRule(
                virtualTrayId = 10,
                amsId = 250,
                trayId = 0,
                label = "External Left"
            ),
            KnownExternalSlotRule(
                virtualTrayId = 11,
                amsId = 250,
                trayId = 1,
                label = "External Right"
            )
        )
    )
}

private fun printer(): Printer = Printer(id = printerId(), name = "Printer")

private fun status(vararg virtualTrays: VirtualTray): PrinterStatus = PrinterStatus(
    printer = printer(),
    connected = true,
    virtualTrays = virtualTrays.toList()
)

private fun assignment(spoolId: SpoolId = spoolId(1), slot: SlotKey = slotKey(amsId = 255, trayId = 0)): Assignment =
    Assignment(
        spoolId = spoolId,
        slot = slot,
        configured = true,
        pendingConfiguration = false
    )

private fun slotKey(amsId: Int, trayId: Int): SlotKey =
    SlotKey.from(printerId = printerId(), amsId = amsId, trayId = trayId)
        ?: error("Test slot key must be valid")

private fun printerId(): PrinterId = PrinterId.from(1) ?: error("Test printer ID must be valid")

private fun spoolId(value: Long): SpoolId = SpoolId.from(value) ?: error("Test spool ID must be valid")
