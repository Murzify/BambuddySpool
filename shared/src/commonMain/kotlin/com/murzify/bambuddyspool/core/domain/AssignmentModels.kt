package com.murzify.bambuddyspool.core.domain

enum class AssignmentSource {
    NfcScan,
    Manual
}

data class AssignmentCommand(
    val spoolId: SpoolId,
    val slot: SlotKey,
    val source: AssignmentSource,
    val expectedSnapshotGeneration: SnapshotGeneration
) {
    companion object {
        fun from(
            spoolId: SpoolId,
            slot: SlotKey,
            source: AssignmentSource,
            expectedSnapshotGeneration: SnapshotGeneration
        ): AssignmentCommand = AssignmentCommand(
            spoolId = spoolId,
            slot = slot,
            source = source,
            expectedSnapshotGeneration = expectedSnapshotGeneration
        )
    }
}

sealed interface AssignmentSuccess {
    val spoolId: SpoolId
    val slot: SlotKey
}

data class AssignedAndConfigured(override val spoolId: SpoolId, override val slot: SlotKey) : AssignmentSuccess

data class AssignedConfigurationPending(override val spoolId: SpoolId, override val slot: SlotKey) : AssignmentSuccess

data class AssignedInventoryOnly(override val spoolId: SpoolId, override val slot: SlotKey) : AssignmentSuccess

data class AlreadyAssigned(override val spoolId: SpoolId, override val slot: SlotKey) : AssignmentSuccess

sealed interface AssignmentResult {
    data class Success(val outcome: AssignmentSuccess) : AssignmentResult
    data class Failure(val reason: DomainFailure) : AssignmentResult
}
