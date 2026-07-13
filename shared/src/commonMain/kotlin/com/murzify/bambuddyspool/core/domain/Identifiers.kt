package com.murzify.bambuddyspool.core.domain

import kotlin.jvm.JvmInline

/** Positive Bambuddy printer database identifier. */
@JvmInline
value class PrinterId private constructor(val value: Long) {
    companion object {
        fun from(value: Long): PrinterId? = value.takeIf(::isPositiveDatabaseId)?.let(::PrinterId)
    }
}

/** Positive Bambuddy spool database identifier. */
@JvmInline
value class SpoolId private constructor(val value: Long) {
    companion object {
        fun from(value: Long): SpoolId? = value.takeIf(::isPositiveDatabaseId)?.let(::SpoolId)
    }
}

/** Non-negative snapshot generation used to bind commands to a validated server view. */
@JvmInline
value class SnapshotGeneration private constructor(val value: Long) {
    companion object {
        fun from(value: Long): SnapshotGeneration? = value.takeIf { it >= MIN_SNAPSHOT_GENERATION }?.let(
            ::SnapshotGeneration
        )
    }
}

internal const val MIN_SLOT_COORDINATE = 0
internal const val MAX_SLOT_COORDINATE = 255
private const val MIN_DATABASE_ID = 1L
private const val MIN_SNAPSHOT_GENERATION = 0L

internal fun isValidSlotCoordinate(value: Int): Boolean = value in MIN_SLOT_COORDINATE..MAX_SLOT_COORDINATE

private fun isPositiveDatabaseId(value: Long): Boolean = value >= MIN_DATABASE_ID
