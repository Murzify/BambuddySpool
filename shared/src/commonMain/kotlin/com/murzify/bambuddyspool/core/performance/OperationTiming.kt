package com.murzify.bambuddyspool.core.performance

import kotlin.time.TimeSource

/**
 * A process-local monotonic clock. Wall time is deliberately unsuitable for latency measurements because it can
 * change while an operation is running.
 */
fun interface MonotonicClock {
    fun nowMillis(): Long
}

object SystemMonotonicClock : MonotonicClock {
    private val origin = TimeSource.Monotonic.markNow()

    override fun nowMillis(): Long = origin.elapsedNow().inWholeMilliseconds
}

/** Stages retained only for the current operation or a test assertion. */
enum class AssignmentTimingStage {
    PayloadDecoded,
    ContextRefreshed,
    PostCompleted,
    Verified,
    Published
}

data class AssignmentTimingSample(
    val stage: AssignmentTimingStage,
    /** Elapsed from the operation start, never a wall-clock timestamp. */
    val elapsedMillis: Long
)

/**
 * Ephemeral timing boundary. Implementations must not forward samples to analytics, persist them, or use them to
 * change mutation decisions. Production intentionally uses [NoOpAssignmentTiming].
 */
interface AssignmentTiming {
    fun mark(stage: AssignmentTimingStage)
}

object NoOpAssignmentTiming : AssignmentTiming {
    override fun mark(stage: AssignmentTimingStage) = Unit
}

/** In-memory timing recorder suitable for local engineering checks and deterministic tests. */
class InMemoryAssignmentTiming(private val clock: MonotonicClock = SystemMonotonicClock) : AssignmentTiming {
    private val startedAtMillis = clock.nowMillis()
    private val mutableSamples = mutableListOf<AssignmentTimingSample>()

    override fun mark(stage: AssignmentTimingStage) {
        mutableSamples += AssignmentTimingSample(stage, (clock.nowMillis() - startedAtMillis).coerceAtLeast(0))
    }

    fun samples(): List<AssignmentTimingSample> = mutableSamples.toList()
}

/**
 * One-shot cold-launch hook. The Android shell creates it only for an NFC launch and the shared Processing surface
 * completes it once it enters composition. It has no global registry, persistence, or reporting destination.
 */
class ColdLaunchProcessingTiming(
    private val startedAtMillis: Long,
    private val clock: MonotonicClock = SystemMonotonicClock
) {
    private var elapsedMillis: Long? = null

    fun markProcessingComposed() {
        if (elapsedMillis == null) elapsedMillis = (clock.nowMillis() - startedAtMillis).coerceAtLeast(0)
    }

    fun elapsedMillisOrNull(): Long? = elapsedMillis
}

/** The two product targets are declarative engineering budgets, not telemetry thresholds. */
object PerformanceBudgets {
    const val ASSIGNMENT_VERIFIED_MILLIS: Long = 2_000
    const val NFC_COLD_LAUNCH_PROCESSING_MILLIS: Long = 1_000
}
