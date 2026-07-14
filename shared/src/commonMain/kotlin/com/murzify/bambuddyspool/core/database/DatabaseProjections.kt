package com.murzify.bambuddyspool.core.database

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.PrinterSlot
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SlotKind
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.SpoolId

data class PrinterListProjection(
    val printerId: Long,
    val name: String?,
    val externalSlotCount: Int,
    val assignedSlotCount: Int
) {
    fun toDomainPrinter(): Printer = Printer(id = printerId.toPrinterId(), name = name)
}

data class PrinterSlotAssignmentProjection(
    val printerId: Long,
    val printerName: String?,
    val amsId: Int,
    val trayId: Int,
    val kind: String,
    val label: String?,
    val assignedSpoolId: Long?,
    val assignedSpoolName: String?,
    val assignedMaterial: String?,
    val assignedColorName: String?
) {
    fun toDomainSlot(): PrinterSlot = PrinterSlot(
        key = slotKey(printerId = printerId, amsId = amsId, trayId = trayId),
        kind = kind.toSlotKind(),
        label = label,
        assignedSpoolId = assignedSpoolId?.toSpoolId()
    )
}

@Suppress("LongParameterList")
data class SpoolListProjection(
    val spoolId: Long,
    val displayName: String?,
    val manufacturer: String?,
    val material: String?,
    val colorName: String?,
    val remainingGrams: Int?,
    val archivedAtEpochMillis: Long?,
    val lastUsedAtEpochMillis: Long?,
    val assignedPrinterId: Long?,
    val assignedPrinterName: String?,
    val assignedAmsId: Int?,
    val assignedTrayId: Int?
) {
    fun toDomainSpool(): Spool = Spool(
        id = spoolId.toSpoolId(),
        name = displayName,
        manufacturer = manufacturer,
        material = material,
        colorName = colorName,
        remainingGrams = remainingGrams
    )
}

data class SyncMetadataProjection(val lastSuccessfulSyncAtEpochMillis: Long?, val snapshotGeneration: Long) {
    fun domainGeneration(): SnapshotGeneration = SnapshotGeneration.from(snapshotGeneration)
        ?: error("Persisted snapshot generation must be non-negative")
}

enum class PersistedSlotKind(val storageValue: String) {
    External("external"),
    Ams("ams")
}

fun SlotKind.toPersistedSlotKind(): PersistedSlotKind = when (this) {
    SlotKind.External -> PersistedSlotKind.External
    SlotKind.Ams -> PersistedSlotKind.Ams
}

private fun String.toSlotKind(): SlotKind = when (this) {
    PersistedSlotKind.External.storageValue -> SlotKind.External
    PersistedSlotKind.Ams.storageValue -> SlotKind.Ams
    else -> error("Unknown persisted slot kind: $this")
}

private fun Long.toPrinterId(): PrinterId = PrinterId.from(this) ?: error("Persisted printer ID must be positive")

private fun Long.toSpoolId(): SpoolId = SpoolId.from(this) ?: error("Persisted spool ID must be positive")

private fun slotKey(printerId: Long, amsId: Int, trayId: Int): SlotKey = SlotKey.from(
    printerId = printerId.toPrinterId(),
    amsId = amsId,
    trayId = trayId
) ?: error("Persisted SlotKey coordinates must be valid")
