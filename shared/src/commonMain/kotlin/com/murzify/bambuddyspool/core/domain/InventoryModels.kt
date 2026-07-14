package com.murzify.bambuddyspool.core.domain

/** Immutable exact identity of a physical printer slot used for assignment verification. */
data class SlotKey(val printerId: PrinterId, val amsId: Int, val trayId: Int) {
    init {
        require(isValidSlotCoordinate(amsId)) { "amsId must be in $MIN_SLOT_COORDINATE..$MAX_SLOT_COORDINATE" }
        require(isValidSlotCoordinate(trayId)) { "trayId must be in $MIN_SLOT_COORDINATE..$MAX_SLOT_COORDINATE" }
    }

    companion object {
        fun from(printerId: Long, amsId: Int, trayId: Int): SlotKey? =
            PrinterId.from(printerId)?.let { from(it, amsId, trayId) }

        fun from(printerId: PrinterId, amsId: Int, trayId: Int): SlotKey? = if (
            isValidSlotCoordinate(amsId) && isValidSlotCoordinate(trayId)
        ) {
            SlotKey(printerId = printerId, amsId = amsId, trayId = trayId)
        } else {
            null
        }
    }
}

data class Printer(val id: PrinterId, val name: String?)

data class Spool(
    val id: SpoolId,
    val name: String?,
    val manufacturer: String?,
    val material: String?,
    val colorName: String?,
    val remainingGrams: Int?
) {
    init {
        require(remainingGrams == null || remainingGrams >= 0) { "remainingGrams must be non-negative" }
    }
}

enum class SlotKind {
    External,
    Ams
}

data class PrinterSlot(val key: SlotKey, val kind: SlotKind, val label: String?, val assignedSpoolId: SpoolId?)

data class Assignment(
    val spoolId: SpoolId,
    val slot: SlotKey,
    val configured: Boolean,
    val pendingConfiguration: Boolean
)
