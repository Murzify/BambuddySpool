package com.murzify.bambuddyspool.core.domain

sealed interface DomainFailure

data class IncompatibleApiResponse(val reason: IncompatibleApiReason) : DomainFailure

enum class IncompatibleApiReason {
    MissingRequiredField,
    InvalidFieldValue,
    UnexpectedShape
}

data class StaleOrOfflineState(val reason: StaleOrOfflineReason) : DomainFailure

enum class StaleOrOfflineReason {
    Offline,
    AuthenticationInvalid,
    RefreshFailed,
    SnapshotGenerationMismatch
}

data class UnsupportedTopology(val reason: UnsupportedTopologyReason) : DomainFailure

enum class UnsupportedTopologyReason {
    UnknownExternalSlotMapping,
    PartialExternalSlotMapping,
    ConflictingCoordinates,
    AmsSlotSelectedForMutation
}

data class VerificationMismatch(
    val expectedSpoolId: SpoolId,
    val expectedSlot: SlotKey,
    val reason: VerificationMismatchReason
) : DomainFailure

enum class VerificationMismatchReason {
    MissingAssignment,
    DifferentSpool,
    DifferentSlot,
    DuplicateSlotAssignments
}
