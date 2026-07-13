package com.murzify.bambuddyspool.core.domain

sealed interface TagMutationOutcome

data class TagMutationSuccess(val spoolId: SpoolId?) : TagMutationOutcome

data class TagMutationNotApplied(val reason: TagMutationNotAppliedReason) : TagMutationOutcome

enum class TagMutationNotAppliedReason {
    UnsupportedTag,
    ReadOnlyTag,
    InsufficientCapacity,
    DifferentTagDetected,
    TagRemovedBeforeWrite,
    PreconditionsFailed
}

data class TagMutationAppliedButUnverified(val reason: TagMutationAppliedButUnverifiedReason) : TagMutationOutcome

enum class TagMutationAppliedButUnverifiedReason {
    VerificationMismatch,
    VerificationInterrupted,
    RereadFailed
}

data class TagMutationOutcomeUnknown(val reason: TagMutationOutcomeUnknownReason) : TagMutationOutcome

enum class TagMutationOutcomeUnknownReason {
    TagRemovedAfterWriteStarted,
    PlatformResultUnavailable
}
