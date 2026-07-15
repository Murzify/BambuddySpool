package com.murzify.bambuddyspool.feature.assignment

import com.murzify.bambuddyspool.core.domain.AssignmentSource
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.SpoolId

/**
 * An immutable, in-memory request to begin the shared assignment workflow.
 *
 * Both manual selection and a future NFC resolver create this same boundary. It is intentionally not serializable
 * and must never be restored, queued, or treated as authorization to perform a mutation.
 */
data class AssignmentIntent(
    val spoolId: SpoolId,
    val slot: SlotKey,
    val source: AssignmentSource,
    val expectedSnapshotGeneration: SnapshotGeneration
) {
    companion object {
        /** Manual selection enters the same orchestration boundary as a resolved NFC scan. */
        fun manual(spoolId: SpoolId, slot: SlotKey, expectedSnapshotGeneration: SnapshotGeneration): AssignmentIntent =
            AssignmentIntent(spoolId, slot, AssignmentSource.Manual, expectedSnapshotGeneration)

        /** NFC resolution differs only by provenance; its freshness and mutation rules are identical. */
        fun nfc(spoolId: SpoolId, slot: SlotKey, expectedSnapshotGeneration: SnapshotGeneration): AssignmentIntent =
            AssignmentIntent(spoolId, slot, AssignmentSource.NfcScan, expectedSnapshotGeneration)
    }
}
